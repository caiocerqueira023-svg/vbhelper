"""Execute production schema-33 social migration/DAO SQL against real host SQLite."""
from pathlib import Path
import re
import runpy
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper"
FIXTURE = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database = FIXTURE["create_database"]
seed = FIXTURE["seed"]
MIGRATION = [multi or single for multi, single in re.findall(
    r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)',
    (MAIN / "database/WorldSocialSchema.kt").read_text(encoding="utf-8"), re.S)]
QUERIES = {}
for name in ("WorldSocialDao.kt", "WorldInteractionDao.kt"):
    QUERIES.update({method: multi or single for multi, single, method in re.findall(
        r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
        (MAIN / "daos" / name).read_text(encoding="utf-8"), re.S)})


class WorldSocialDatabaseTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(32)
        self.addCleanup(self.db.close)
        seed(self.db, "Card", id=1)
        seed(self.db, "Sprite", id=1)
        seed(self.db, "CardCharacter", id=100, cardId=1, spriteId=1)
        for identity, kind, version in (("owned", "DEVOTED", 2), ("a", "RECKLESS", 3), ("b", "ASTUTE", 99)):
            seed(self.db, "DigimonIndividual", individualId=identity, createdAt=123)
            seed(self.db, "DigimonPersonalityTraits", individualId=identity, personalityType=kind,
                 generatedAt=456, systemVersion=version)
            seed(self.db, "ChatMessageEntity", individualId=identity, role="user", content="PRIVATE_SECRET_" + identity)
        seed(self.db, "UserCharacter", id=10, individualId="owned", charId=100)
        seed(self.db, "Farm", id="farm", name="Farm", createdAt=100, lastSimulatedAt=200, randomSeed=42)
        seed(self.db, "FarmResident", individualId="owned", farmId="farm", energy=73, satiety=64)
        seed(self.db, "WorldSpawn", id=1, individualId="a", cardCharacterId=100, latitude=1.0,
             longitude=2.0, homeLatitude=1.0, homeLongitude=2.0, ecosystemEmotion=-7, mood=82,
             recruitmentState="WILD", expiresAt=999999)
        seed(self.db, "WildRelationship", individualId="a", cardCharacterId=100, trust=82)
        seed(self.db, "WorldEcosystemSession", id="radar-local", seed=42, rulesVersion=2,
             tickIndex=37, tickRemainderMillis=999, lastCheckpointAt=123456)
        self.before = {table: self.db.execute(f"SELECT * FROM `{table}`").fetchall() for table in
                       ("DigimonIndividual", "ChatMessageEntity", "WildRelationship", "UserCharacter", "WorldEcosystemSession")}
        self.assertEqual(7, len(MIGRATION), "Extract every production migration statement")
        with self.db:
            for statement in MIGRATION:
                self.db.execute(statement)

    def memory(self, observer="a", event="event", partner="b", at=1):
        seed(self.db, "WorldSocialMemory", observerId=observer, eventId=event, partnerId=partner,
             partnerName=partner, motive="COMPANY", outcome="EXCHANGED", summary="Public encounter fact",
             openingKey=f"opening:{event}", tick=at, createdAt=at)

    def test_additive_migration_matches_exported_room_schema(self):
        fresh = create_database(33)
        self.addCleanup(fresh.close)
        for table in ("WorldInteraction", "FarmResident", "WorldSocialMemory", "IndividualSocialState"):
            def columns(db):
                return {row[1]: row[2:] for row in db.execute(f"PRAGMA table_info(`{table}`)")}
            self.assertEqual(columns(fresh), columns(self.db), table)
            for pragma in ("foreign_key_list", "index_list"):
                self.assertEqual(fresh.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 self.db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_identity_history_trust_clock_and_assigned_personalities_are_preserved(self):
        for table, rows in self.before.items():
            self.assertEqual(rows, self.db.execute(f"SELECT * FROM `{table}`").fetchall(), table)
        self.assertEqual([("a", "RECKLESS", 456, 3), ("b", "ASTUTE", 456, 99), ("owned", "DEVOTED", 456, 3)],
                         self.db.execute("SELECT individualId,personalityType,generatedAt,systemVersion FROM DigimonPersonalityTraits ORDER BY individualId").fetchall())
        self.assertEqual((73, 64, None), self.db.execute("SELECT energy,satiety,socialTargetId FROM FarmResident").fetchone())

    def test_partner_scoped_memory_queries_never_read_private_history(self):
        self.memory(partner="b")
        self.memory(event="trainer-event", partner="trainer", at=2)
        rows = self.db.execute(QUERIES["withPartner"], {"id": "a", "partner": "b", "limit": 5}).fetchall()
        self.assertEqual(1, len(rows))
        self.assertNotIn("PRIVATE_SECRET", repr(rows))
        self.assertEqual(1, self.db.execute(QUERIES["familiarity"], {"id": "a", "partner": "b"}).fetchone()[0])

    def test_memory_is_unique_per_event_and_retention_keeps_recent_facts(self):
        self.memory()
        with self.assertRaises(sqlite3.IntegrityError):
            self.memory()
        for index in range(45):
            self.memory(event=f"event-{index}", at=index + 2)
        self.db.execute(QUERIES["prune"], {"id": "a"})
        self.assertEqual(40, self.db.execute("SELECT COUNT(*) FROM WorldSocialMemory WHERE observerId='a'").fetchone()[0])
        recent = self.db.execute(QUERIES["recent"], {"id": "a", "limit": 1}).fetchone()
        self.assertIn("event-44", recent)

    def test_observer_deletion_cascades_but_partner_disappearance_preserves_history(self):
        self.memory(observer="a", partner="b")
        self.memory(observer="b", partner="a")
        seed(self.db, "IndividualSocialState", individualId="a", sessionSeed=42,
             lastPlayerInitiationTick=20, lastPeerInitiationTick=30)
        self.db.execute("DELETE FROM DigimonIndividual WHERE individualId='a'")
        self.assertEqual([("b", "a")], self.db.execute("SELECT observerId,partnerId FROM WorldSocialMemory").fetchall())
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM IndividualSocialState").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_final_public_speech_excludes_private_and_unsupported_records(self):
        for event, origin, version in (("public", "AUTONOMOUS", 3), ("private", "DIRECT_PLAYER", 3), ("old", "AUTONOMOUS", 2)):
            seed(self.db, "WorldInteraction", id=event, type="CHAT", origin=origin, state="ENDED",
                 seed=1, rulesVersion=version, startTick=10, nextActionTick=10, createdAt=1, expiresAt=10000)
            seed(self.db, "WorldInteractionMessage", id=f"speech-{event}", interactionId=event,
                 sequence=1, speakerId="a", speakerName="A", body=event, source="AUTHORED", requestId=event, tick=10, createdAt=1)
        rows = self.db.execute(QUERIES["trailingPublicSpeech"], {"oldestTick": 10, "rulesVersion": 3}).fetchall()
        self.assertEqual(1, len(rows))
        self.assertIn("public", rows[0])
        self.assertEqual([], self.db.execute(QUERIES["trailingPublicSpeech"], {"oldestTick": 19, "rulesVersion": 3}).fetchall())

    def test_social_state_survives_connection_serialization(self):
        seed(self.db, "IndividualSocialState", individualId="a", sessionSeed=42,
             lastPlayerInitiationTick=20, lastPeerInitiationTick=30, lastPartnerId="b")
        restored = sqlite3.connect(":memory:")
        self.addCleanup(restored.close)
        self.db.commit()
        self.db.backup(restored)
        self.assertEqual(("a", 42, 20, 30, "b"), restored.execute(QUERIES["getState"], {"id": "a"}).fetchone())


if __name__ == "__main__":
    unittest.main(verbosity=2)
