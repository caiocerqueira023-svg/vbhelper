"""Run the production 26->28 additive migrations in host SQLite and compare Room's fresh schema."""
from pathlib import Path
import re
import runpy
import unittest

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database = FIXTURE["create_database"]
seed = FIXTURE["seed"]
MAIN = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper"


def statements(text):
    return [multiline or single for multiline, single in re.findall(
        r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', text, re.S)]


SOURCE = (MAIN / "database/AppDatabase.kt").read_text(encoding="utf-8")
MOVEMENT = SOURCE.split("val MIGRATION_26_27 =", 1)[1].split("val MIGRATION_25_26 =", 1)[0]
DIALOGUE = (MAIN / "database/WorldDialogueSchema.kt").read_text(encoding="utf-8")


class LivingWorldMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(26)
        self.addCleanup(self.db.close)
        seed(self.db, "DigimonIndividual", individualId="a")
        seed(self.db, "DigimonIndividual", individualId="b")
        seed(self.db, "ChatMessageEntity", individualId="a", role="user", content="private history")
        seed(self.db, "WorldEcosystemSession", id="radar-local", seed=42, rulesVersion=1,
             tickIndex=37, tickRemainderMillis=999, lastCheckpointAt=123456)
        for sql in statements(MOVEMENT) + statements(DIALOGUE):
            self.db.execute(sql)

    def test_migrated_tables_and_defaults_match_fresh_schema(self):
        fresh = create_database(28)
        self.addCleanup(fresh.close)
        for table in ("WorldSpawn", "WorldInteraction", "WorldDen", "WildPairBond", "WorldEcosystemInput",
                      "WorldInteractionMessage", "WorldDialogueIntent", "WorldNpcBattle"):
            def columns(db):
                return {row[1]: row[2:] for row in db.execute(f"PRAGMA table_info(`{table}`)")}
            self.assertEqual(columns(fresh), columns(self.db), table)
            self.assertEqual(fresh.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
                             self.db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(), table)
            self.assertEqual(fresh.execute(f"PRAGMA index_list(`{table}`)").fetchall(),
                             self.db.execute(f"PRAGMA index_list(`{table}`)").fetchall(), table)

    def test_old_identity_history_seed_and_clock_are_preserved(self):
        self.assertEqual("private history", self.db.execute("SELECT content FROM ChatMessageEntity").fetchone()[0])
        self.assertEqual((42, 1, 37, 999), self.db.execute(
            "SELECT seed,rulesVersion,tickIndex,tickRemainderMillis FROM WorldEcosystemSession").fetchone())
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_normalized_transcript_deduplicates_requests_and_retention_cascades(self):
        seed(self.db, "WorldInteraction", id="conversation", type="CHAT", origin="AUTONOMOUS", state="ENDED",
             seed=42, rulesVersion=2, createdAt=0, expiresAt=100000)
        seed(self.db, "WorldInteractionMessage", id="reply-a", interactionId="conversation", sequence=1,
             speakerId="a", speakerName="A", body="Public only", source="MODEL", requestId="reply", tick=1, createdAt=1)
        import sqlite3
        with self.assertRaises(sqlite3.IntegrityError):
            seed(self.db, "WorldInteractionMessage", id="duplicate-a", interactionId="conversation", sequence=2,
                 speakerId="a", speakerName="A", body="Duplicate", source="MODEL", requestId="reply", tick=1, createdAt=1)
        self.db.execute("DELETE FROM WorldInteraction WHERE id='conversation'")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM WorldInteractionMessage").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM ChatMessageEntity").fetchone()[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
