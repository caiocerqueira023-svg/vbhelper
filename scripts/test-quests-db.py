"""Execute every production quest migration and key DAO queries against real SQLite."""
from pathlib import Path
import re
import runpy
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper"
fixture = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database, seed = fixture["create_database"], fixture["seed"]
source = (MAIN / "database/QuestSchema.kt").read_text(encoding="utf-8")

def migration(name):
    body = source.split("fun " + name + "(", 1)[1].split("\n    fun ", 1)[0]
    return [multi or single for multi, single in re.findall(
        r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', body, re.S)]

QUERIES = {}
for filename in ("QuestDao.kt", "ItemDao.kt", "WorldSpawnDao.kt"):
    QUERIES.update({method: multi or single for multi, single, method in re.findall(
        r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
        (MAIN / "daos" / filename).read_text(encoding="utf-8"), re.S)})

class QuestDatabaseTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(33)
        self.addCleanup(self.db.close)
        seed(self.db, "Card", id=7, name="Original")
        seed(self.db, "Sprite", id=1)
        seed(self.db, "CardCharacter", id=700, cardId=7, spriteId=1, stage=3)
        seed(self.db, "DigimonIndividual", individualId="giver")
        seed(self.db, "WildRelationship", individualId="giver", cardCharacterId=700, trust=95)
        seed(self.db, "ChatMessageEntity", individualId="giver", role="user", content="preserved")
        self.before = self.db.execute("SELECT * FROM WildRelationship").fetchall()
        for method in ("create", "addStorySteps", "addBattleConditions", "addFollowUps", "addWatchCapabilities"):
            statements = migration(method)
            self.assertTrue(statements, method)
            for sql in statements:
                self.db.execute(sql)
        self.db.commit()

    def quest(self, id="q", **kwargs):
        seed(self.db, "QuestInstance", id=id, giverId="giver", giverCardCharacterId=700,
             giverName="Giver", category="NORMAL", templateId="courier", templateVersion=5,
             state="ACTIVE", **kwargs)

    def objective(self, id="o", **kwargs):
        seed(self.db, "QuestObjective", id=id, questId="q", type="DELIVER_TOKEN", required=1, **kwargs)

    def test_all_migrations_match_current_generated_room_schema_and_preserve_data(self):
        fresh = create_database(38)
        self.addCleanup(fresh.close)
        tables = [r[0] for r in self.db.execute("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'Quest%'")]
        for table in tables:
            def columns(db):
                return {r[1]: r[2:] for r in db.execute(f"PRAGMA table_info(`{table}`)")}
            self.assertEqual(columns(fresh), columns(self.db), table)
            def indexes(db):
                return sorted((r[1], r[2], r[3]) for r in db.execute(f"PRAGMA index_list(`{table}`)"))
            self.assertEqual(indexes(fresh), indexes(self.db), table)
            self.assertEqual(fresh.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
                             self.db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(), table)
        self.assertEqual(self.before, self.db.execute("SELECT * FROM WildRelationship").fetchall())
        self.assertEqual("preserved", self.db.execute("SELECT content FROM ChatMessageEntity").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_one_child_per_parent_but_multiple_roots_are_allowed(self):
        self.quest("root")
        self.quest("other-root")
        self.quest("child", parentQuestId="root")
        with self.assertRaises(sqlite3.IntegrityError):
            self.quest("duplicate-child", parentQuestId="root")

    def test_quest_object_cannot_be_consumed_by_another_quest(self):
        self.quest()
        seed(self.db, "QuestToken", id="letter", questId="q", kind="LETTER", state="HELD")
        self.assertEqual(0, self.db.execute(QUERIES["deliverToken"], {"id":"letter", "questId":"other", "now":1}).rowcount)
        self.assertEqual(1, self.db.execute(QUERIES["deliverToken"], {"id":"letter", "questId":"q", "now":1}).rowcount)
        self.assertEqual(0, self.db.execute(QUERIES["deliverToken"], {"id":"letter", "questId":"q", "now":2}).rowcount)

    def test_only_current_step_targets_are_exposed(self):
        self.quest(currentPhase=1)
        self.objective("now", phase=1, targetIndividualId="now-target")
        self.objective("future", phase=2, targetIndividualId="future-target")
        rows = self.db.execute(QUERIES["pendingTargets"]).fetchall()
        self.assertEqual(1, len(rows))
        self.assertEqual("now", rows[0][0])
        self.db.execute("UPDATE QuestInstance SET availabilityIssue='TARGET_CARD_MISSING'")
        self.assertEqual([], self.db.execute(QUERIES["pendingTargets"]).fetchall())

    def test_wallet_and_inventory_rollback_together_on_failed_delivery(self):
        seed(self.db, "QuestWallet", id=1, balance=100)
        seed(self.db, "Items", id=1, quantity=2, itemType="UNIVERSAL")
        self.db.commit()
        with self.assertRaises(RuntimeError):
            with self.db:
                self.db.execute(QUERIES["addBits"], {"amount":50})
                self.assertEqual(1, self.db.execute(QUERIES["handOver"], {"id":1,"amount":2}).rowcount)
                if self.db.execute(QUERIES["handOver"], {"id":1,"amount":1}).rowcount != 1:
                    raise RuntimeError("insufficient stock")
        self.assertEqual(100, self.db.execute("SELECT balance FROM QuestWallet").fetchone()[0])
        self.assertEqual(2, self.db.execute("SELECT quantity FROM Items").fetchone()[0])

    def test_reward_and_source_receipts_cannot_be_replayed(self):
        seed(self.db, "QuestRewardReceipt", questId="q", claimedAt=1)
        with self.assertRaises(sqlite3.IntegrityError):
            seed(self.db, "QuestRewardReceipt", questId="q", claimedAt=2)
        seed(self.db, "QuestPhaseReceipt", questId="q", sourceId="battle", phase=0, recordedAt=1)
        self.db.execute("INSERT OR IGNORE INTO QuestPhaseReceipt VALUES('q','battle',1,2)")
        self.assertEqual(0, self.db.execute(QUERIES["phaseReceipt"], {"questId":"q","sourceId":"battle"}).fetchone()[2])

    def test_battle_hits_are_scoped_to_actual_actor_target_and_technique(self):
        seed(self.db, "QuestBattleTechniqueHit", interactionId="b", actorId="partner", targetId="target", techniqueId="skill", hits=2)
        args = {"id":"b","actorId":"partner","techniqueId":"skill","targetId":"target"}
        self.assertEqual(2, self.db.execute(QUERIES["techniqueHits"], args).fetchone()[0])
        self.assertIsNone(self.db.execute(QUERIES["techniqueHits"], dict(args, actorId="teammate")).fetchone()[0])
        self.assertIsNone(self.db.execute(QUERIES["techniqueHits"], dict(args, targetId="other")).fetchone()[0])

    def test_deferred_followups_require_completed_parent_and_stay_closed_when_skipped(self):
        self.quest("q", followUpTemplateId="rescue")
        self.assertEqual([], self.db.execute(QUERIES["pendingFollowUps"], {"giverId":"giver"}).fetchall())
        self.db.execute("UPDATE QuestInstance SET state='COMPLETED'")
        self.assertEqual(1, len(self.db.execute(QUERIES["pendingFollowUps"], {"giverId":"giver"}).fetchall()))
        self.db.execute("UPDATE QuestInstance SET followUpClosed=1")
        self.assertEqual([], self.db.execute(QUERIES["pendingFollowUps"], {"giverId":"giver"}).fetchall())

    def test_serialized_database_retains_bound_partner_objects_and_evidence(self):
        self.quest(partnerId="partner", partnerDeviceType="BEDevice")
        seed(self.db, "QuestToken", id="letter", questId="q", kind="LETTER", state="HELD")
        seed(self.db, "QuestEvidence", questId="q", objectiveId="o", sourceId="watch:receipt", amount=3, recordedAt=1)
        restored = sqlite3.connect(":memory:")
        self.addCleanup(restored.close)
        self.db.commit()
        self.db.backup(restored)
        self.assertEqual(("partner", "BEDevice"), restored.execute("SELECT partnerId,partnerDeviceType FROM QuestInstance").fetchone())
        self.assertEqual("HELD", restored.execute("SELECT state FROM QuestToken").fetchone()[0])
        self.assertEqual(3, restored.execute("SELECT amount FROM QuestEvidence").fetchone()[0])

if __name__ == "__main__":
    unittest.main(verbosity=2)
