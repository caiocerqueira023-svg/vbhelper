"""Verify production interaction schema/guards/DAO SQL against real host SQLite."""
from pathlib import Path
import re
import runpy
import unittest
import sqlite3

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database = FIXTURE["create_database"]
seed = FIXTURE["seed"]
MAIN = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper"


def statements(text):
    return [multiline or single for multiline, single in re.findall(
        r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', text, re.S
    )]


SCHEMA_SQL = statements((MAIN / "database/WorldInteractionSchema.kt").read_text(encoding="utf-8"))
INTEGRITY = (MAIN / "database/WorldInteractionIntegrity.kt").read_text(encoding="utf-8").split("fun recoverOnOpen", 1)
GUARDS = statements(INTEGRITY[0])
RECOVERY = statements(INTEGRITY[1])
QUERIES = {}
for filename in ("WorldSpawnDao.kt", "UserCharacterDao.kt", "WorldInteractionDao.kt"):
    QUERIES.update({name: multiline or single for multiline, single, name in re.findall(
        r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
        (MAIN / "daos" / filename).read_text(encoding="utf-8"), re.S
    )})


class WorldInteractionSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(25)
        self.addCleanup(self.db.close)
        seed(self.db, "WorldEcosystemSession", id="radar-local", seed=42, rulesVersion=1,
             tickIndex=37, tickRemainderMillis=999, lastCheckpointAt=123456, pauseReason="PLAYER_BATTLE")
        seed(self.db, "Card", id=1)
        seed(self.db, "Sprite", id=1)
        seed(self.db, "CardCharacter", id=100, cardId=1, spriteId=1)
        for individual in ("owned", "wild-a", "wild-b"):
            seed(self.db, "DigimonIndividual", individualId=individual)
            seed(self.db, "ChatMessageEntity", individualId=individual, role="user", content=f"private-{individual}")
        seed(self.db, "UserCharacter", id=10, individualId="owned", charId=100)
        for spawn_id, individual in ((1, "wild-a"), (2, "wild-b")):
            seed(self.db, "WorldSpawn", id=spawn_id, individualId=individual, cardCharacterId=100,
                 expiresAt=101000, recruitmentState="WILD")
            seed(self.db, "WildRelationship", individualId=individual, cardCharacterId=100, trust=82, recruitmentState="WILD")
        self.assertEqual(14, len(SCHEMA_SQL))
        self.assertEqual(8, len(GUARDS))
        self.assertEqual(4, len(RECOVERY))
        for sql in SCHEMA_SQL + GUARDS:
            self.db.execute(sql)
        self.db.commit()

    def event(self, event_id="battle", origin="DIRECT_PLAYER", state="PLAYER_CONTROLLED", two_wilds=False):
        seed(self.db, "WorldInteraction", id=event_id, type="BATTLE", origin=origin, state=state,
             seed=42, rulesVersion=1, startTick=17, nextActionTick=17, createdAt=100000, expiresAt=2000000)
        if origin == "DIRECT_PLAYER":
            seed(self.db, "WorldInteractionParticipant", interactionId=event_id, individualId="owned",
                 role="OWNED", side="ALLIED", ownedCharacterId=10, cardCharacterId=100)
        for spawn_id, individual in ((1, "wild-a"), (2, "wild-b")) if two_wilds else ((1, "wild-a"),):
            seed(self.db, "WorldInteractionParticipant", interactionId=event_id, individualId=individual,
                 role="WILD", side="OPPOSING", spawnId=spawn_id, cardCharacterId=100)
            seed(self.db, "WorldParticipationClaim", individualId=individual, interactionId=event_id, spawnId=spawn_id)

    def scalar(self, sql):
        return self.db.execute(sql).fetchone()[0]

    def test_additive_migration_matches_fresh_v26_and_keeps_the_v25_checkpoint(self):
        fresh = create_database(26)
        self.addCleanup(fresh.close)
        for table in ("WorldInteraction", "WorldInteractionParticipant", "WorldParticipationClaim", "WorldInteractionResult"):
            for pragma in ("table_info", "foreign_key_list", "index_list"):
                self.assertEqual(fresh.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 self.db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        self.assertEqual((42, 37, 999, "PLAYER_BATTLE"), self.db.execute(
            "SELECT seed,tickIndex,tickRemainderMillis,pauseReason FROM WorldEcosystemSession").fetchone())
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_competing_claim_rolls_back_the_entire_second_proposal(self):
        with self.db:
            self.event("first")
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.event("second")
        self.assertEqual(1, self.scalar("SELECT COUNT(*) FROM WorldInteraction"))
        self.assertEqual(2, self.scalar("SELECT COUNT(*) FROM WorldInteractionParticipant"))
        self.assertEqual(1, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))

    def test_claim_cannot_invent_a_spawn_identity_or_nonparticipant(self):
        with self.db:
            self.event()
        with self.assertRaises(sqlite3.IntegrityError):
            seed(self.db, "WorldParticipationClaim", individualId="wild-b", interactionId="battle", spawnId=2)
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE WorldSpawn SET recruitmentState='PENDING_RECRUITMENT' WHERE id=1")

    def test_guarded_expiry_and_eviction_keep_pinned_encounters_in_population_counts(self):
        with self.db:
            self.event()
            self.db.execute(QUERIES["deleteExpired"], {"now": 102000})
        self.assertEqual([("wild-a",)], self.db.execute("SELECT individualId FROM WorldSpawn").fetchall())
        self.assertEqual(1, self.db.execute(QUERIES["countActive"], {"now": 102000}).fetchone()[0])
        self.assertEqual(0, self.db.execute(QUERIES["deleteById"], {"id": 1}).rowcount)
        self.assertEqual(3, self.scalar("SELECT COUNT(*) FROM ChatMessageEntity"))

    def test_raw_spawn_delete_is_also_protected(self):
        with self.db:
            self.event()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("DELETE FROM WorldSpawn WHERE id=1")
        self.assertEqual(2, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))

    def test_contact_recruitment_cannot_bypass_the_same_claim(self):
        with self.db:
            self.event()
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE WildRelationship SET recruitmentState='RECRUITED' WHERE individualId='wild-a'")
        self.assertEqual(82, self.scalar("SELECT trust FROM WildRelationship WHERE individualId='wild-a'"))

    def test_card_cascade_cancels_events_without_erasing_private_individual_history(self):
        with self.db:
            self.event(two_wilds=True)
            self.db.execute("DELETE FROM Card WHERE id=1")
        self.assertEqual("CANCELLED", self.scalar("SELECT state FROM WorldInteraction"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(3, self.scalar("SELECT COUNT(*) FROM ChatMessageEntity"))
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_individual_deletion_releases_the_surviving_neighbors_claim(self):
        with self.db:
            self.event(origin="AUTONOMOUS", state="ACTIVE", two_wilds=True)
            self.db.execute("DELETE FROM DigimonIndividual WHERE individualId='wild-a'")
        self.assertEqual("CANCELLED", self.scalar("SELECT state FROM WorldInteraction"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(1, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))

    def test_owned_deletion_cancels_the_battle_and_preserves_the_wild(self):
        with self.db:
            self.event()
            self.db.execute("DELETE FROM UserCharacter WHERE id=10")
        self.assertEqual("CANCELLED", self.scalar("SELECT state FROM WorldInteraction"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(2, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))

    def test_terminal_transition_releases_claims_before_normal_expiry(self):
        with self.db:
            self.event()
            self.db.execute("UPDATE WorldInteraction SET state='CANCELLED' WHERE id='battle'")
            self.db.execute(QUERIES["deleteExpired"], {"now": 102000})
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))

    def test_unique_result_ledger_blocks_duplicate_losses_while_the_wild_remains(self):
        with self.db:
            self.event()
            seed(self.db, "WorldInteractionResult", interactionId="battle", outcome="OPPOSING_VICTORY", recordedAt=100000, appliedAt=100000)
            self.db.execute(QUERIES["recordBattleResult"], {"characterId": 10, "won": False})
            self.db.execute("UPDATE WorldInteraction SET state='ENDED' WHERE id='battle'")
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                seed(self.db, "WorldInteractionResult", interactionId="battle", outcome="ALLIED_VICTORY", recordedAt=100001)
                self.db.execute(QUERIES["recordBattleResult"], {"characterId": 10, "won": True})
        self.assertEqual((0, 1), self.db.execute("SELECT totalBattlesWon,totalBattlesLost FROM UserCharacter").fetchone())
        self.assertEqual(2, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))

    def test_failure_during_effect_application_rolls_back_ledger_and_claim_release(self):
        with self.db:
            self.event()
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                seed(self.db, "WorldInteractionResult", interactionId="battle", outcome="ALLIED_VICTORY", recordedAt=100000)
                self.db.execute(QUERIES["recordBattleResult"], {"characterId": 10, "won": True})
                self.db.execute("UPDATE WorldInteraction SET state='ENDED' WHERE id='battle'")
                self.db.execute(QUERIES["deleteById"], {"id": 1})
                # A later participant update fails: the whole result must roll back.
                seed(self.db, "WorldParticipationClaim", individualId="missing", interactionId="battle", spawnId=2)
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldInteractionResult"))
        self.assertEqual(0, self.scalar("SELECT totalBattlesWon FROM UserCharacter"))
        self.assertEqual(1, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(2, self.scalar("SELECT COUNT(*) FROM WorldSpawn"))

    def test_cold_open_interrupts_player_ownership_without_reapplying_terminal_results(self):
        with self.db:
            self.event()
            seed(self.db, "WorldInteraction", id="completed", type="BATTLE", origin="DIRECT_PLAYER", state="ENDED",
                 seed=7, rulesVersion=1, createdAt=90000, expiresAt=2000000)
            seed(self.db, "WorldInteractionResult", interactionId="completed", outcome="OPPOSING_VICTORY",
                 recordedAt=95000, appliedAt=95000)
            self.db.execute(QUERIES["recordBattleResult"], {"characterId": 10, "won": False})
            for sql in RECOVERY:
                self.db.execute(sql)
        self.assertEqual("INTERRUPTED", self.scalar("SELECT state FROM WorldInteraction WHERE id='battle'"))
        self.assertEqual("ENDED", self.scalar("SELECT state FROM WorldInteraction WHERE id='completed'"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(1, self.scalar("SELECT totalBattlesWon+totalBattlesLost FROM UserCharacter"))

    def joined_reservation(self):
        self.event("npc", origin="AUTONOMOUS", state="ACTIVE", two_wilds=True)
        self.db.execute("DELETE FROM WorldParticipationClaim WHERE interactionId='npc'")
        seed(self.db, "WorldInteraction", id="joined", type="BATTLE", origin="JOINED_PLAYER", state="RESERVED",
             seed=99, rulesVersion=1, startTick=18, nextActionTick=18, createdAt=100000, expiresAt=2000000,
             parentInteractionId="npc", reservedFrom="ACTIVE", reservationExpiresAt=130000)
        seed(self.db, "WorldInteractionParticipant", interactionId="joined", individualId="owned",
             role="OWNED", side="ALLIED", ownedCharacterId=10, cardCharacterId=100)
        for spawn_id, individual in ((1, "wild-a"), (2, "wild-b")):
            seed(self.db, "WorldInteractionParticipant", interactionId="joined", individualId=individual,
                 role="WILD", side="OPPOSING", spawnId=spawn_id, cardCharacterId=100)
            seed(self.db, "WorldParticipationClaim", individualId=individual, interactionId="joined", spawnId=spawn_id)
        self.db.execute("UPDATE WorldInteraction SET state='RESERVED', reservedFrom='ACTIVE', reservationExpiresAt=130000 WHERE id='npc'")

    def test_cold_open_restores_uncommitted_joined_claims_once_without_rerolling(self):
        with self.db:
            self.joined_reservation()
            for sql in RECOVERY:
                self.db.execute(sql)
        original = self.db.execute("SELECT seed,nextActionTick,revision FROM WorldInteraction WHERE id='npc'").fetchone()
        self.assertEqual((42, 17, 1), original)
        self.assertEqual("ACTIVE", self.scalar("SELECT state FROM WorldInteraction WHERE id='npc'"))
        self.assertEqual("CANCELLED", self.scalar("SELECT state FROM WorldInteraction WHERE id='joined'"))
        self.assertEqual([("wild-a", "npc"), ("wild-b", "npc")], self.db.execute(
            "SELECT individualId,interactionId FROM WorldParticipationClaim ORDER BY individualId").fetchall())
        with self.db:
            for sql in RECOVERY:
                self.db.execute(sql)
        self.assertEqual(original, self.db.execute("SELECT seed,nextActionTick,revision FROM WorldInteraction WHERE id='npc'").fetchone())
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldInteractionResult"))
        self.assertEqual(3, self.scalar("SELECT COUNT(*) FROM ChatMessageEntity"))

    def test_cold_open_never_resurrects_the_parent_of_a_committed_joined_battle(self):
        with self.db:
            self.joined_reservation()
            self.db.execute("UPDATE WorldInteraction SET state='PLAYER_CONTROLLED', reservedFrom=NULL WHERE id='joined'")
            self.db.execute("UPDATE WorldInteraction SET state='CANCELLED', reservedFrom=NULL WHERE id='npc'")
            for sql in RECOVERY:
                self.db.execute(sql)
        self.assertEqual("INTERRUPTED", self.scalar("SELECT state FROM WorldInteraction WHERE id='joined'"))
        self.assertEqual("CANCELLED", self.scalar("SELECT state FROM WorldInteraction WHERE id='npc'"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldParticipationClaim"))
        self.assertEqual(0, self.scalar("SELECT totalBattlesWon+totalBattlesLost FROM UserCharacter"))

    def test_retention_keeps_latest_100_and_active_source_context_with_cascaded_ledger_cleanup(self):
        with self.db:
            for index in range(110):
                seed(self.db, "WorldInteraction", id=f"ended-{index:03}", type="CHAT", origin="AUTONOMOUS",
                     state="ENDED", seed=42, rulesVersion=1, createdAt=0, expiresAt=300000, endedAt=1000 + index)
            seed(self.db, "WorldInteractionResult", interactionId="ended-000", outcome="DRAW", recordedAt=1000, appliedAt=1000)
            seed(self.db, "WorldInteraction", id="protected-parent", type="CHAT", origin="AUTONOMOUS",
                 state="ENDED", seed=42, rulesVersion=1, createdAt=0, expiresAt=300000, endedAt=0)
            seed(self.db, "WorldInteraction", id="child", type="BATTLE", origin="AUTONOMOUS",
                 state="PROPOSED", seed=42, rulesVersion=1, createdAt=0, expiresAt=300000, parentInteractionId="protected-parent")
            self.db.execute(QUERIES["pruneEndedInteractions"], {"cutoff": 500})
        self.assertEqual(100, self.scalar("SELECT COUNT(*) FROM WorldInteraction WHERE id LIKE 'ended-%'"))
        self.assertEqual(1, self.scalar("SELECT COUNT(*) FROM WorldInteraction WHERE id='protected-parent'"))
        self.assertEqual(0, self.scalar("SELECT COUNT(*) FROM WorldInteractionResult"))
        self.assertEqual(3, self.scalar("SELECT COUNT(*) FROM ChatMessageEntity"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
