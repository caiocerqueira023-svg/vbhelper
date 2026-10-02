"""Check the production 29->30 memory migration and durable chat/result invariants."""
from pathlib import Path
import re
import runpy
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database, seed = FIXTURE["create_database"], FIXTURE["seed"]
SOURCE = (ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/database/WorldChatMemorySchema.kt").read_text(encoding="utf-8")
SQL = [multiline or single for multiline, single in re.findall(
    r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', SOURCE, re.S)]


class WorldChatMemorySqlTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(29)
        self.addCleanup(self.db.close)
        seed(self.db, "DigimonIndividual", individualId="hackmon")
        seed(self.db, "ChatMessageEntity", individualId="hackmon", role="user", content="If you win I tell you my name.")
        seed(self.db, "WorldInteraction", id="battle", type="BATTLE", origin="DIRECT_PLAYER", state="PLAYER_CONTROLLED",
             seed=42, rulesVersion=2, createdAt=1000, expiresAt=100000)
        self.db.commit()
        for sql in SQL:
            self.db.execute(sql)
        self.db.commit()

    def memory(self):
        seed(self.db, "WorldBattleMemory", interactionId="battle", individualId="hackmon", individualName="Hackmon",
             opponentName="Agumon", perspective="WON", friendly=1, reason="Winner learns the trainer's name.",
             transcriptJson='[{"role":"user","text":"If you win I tell you my name."}]', createdAt=2000, needsReaction=1)

    def test_migration_matches_fresh_room_schema_without_erasing_history(self):
        fresh = create_database(30)
        self.addCleanup(fresh.close)
        for table in ("WorldBattleContext", "WorldBattleMemory", "WorldPrivateChatLink"):
            for pragma in ("table_info", "foreign_key_list", "index_list"):
                self.assertEqual(fresh.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 self.db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        self.assertEqual("If you win I tell you my name.", self.db.execute("SELECT content FROM ChatMessageEntity").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_memory_and_wager_survive_pruning_the_event(self):
        with self.db:
            self.memory()
            seed(self.db, "WorldBattleContext", interactionId="battle", friendly=1, chatIndividualId="hackmon",
                 reason="name wager", transcriptJson="[]", createdAt=1000)
            self.db.execute("DELETE FROM WorldInteraction WHERE id='battle'")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM WorldBattleContext").fetchone()[0])
        self.assertIn("tell you my name", self.db.execute("SELECT transcriptJson FROM WorldBattleMemory").fetchone()[0])

    def test_event_individual_memory_and_public_import_are_unique(self):
        with self.db:
            self.memory()
            seed(self.db, "WorldPrivateChatLink", sourceMessageId="greeting", individualId="hackmon", chatMessageId=1)
        with self.assertRaises(sqlite3.IntegrityError):
            self.memory()
        self.db.rollback()
        with self.assertRaises(sqlite3.IntegrityError):
            seed(self.db, "WorldPrivateChatLink", sourceMessageId="greeting", individualId="hackmon", chatMessageId=2)

    def test_failed_reaction_commit_rolls_back_both_message_and_completion(self):
        with self.db:
            self.memory()
        before = self.db.execute("SELECT COUNT(*) FROM ChatMessageEntity").fetchone()[0]
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                seed(self.db, "ChatMessageEntity", individualId="hackmon", role="assistant", content="Now, what is your name?")
                self.db.execute("UPDATE WorldBattleMemory SET reactionMessageId=2 WHERE interactionId='battle'")
                seed(self.db, "WorldPrivateChatLink", sourceMessageId="bad", individualId="missing", chatMessageId=2)
        self.assertEqual(before, self.db.execute("SELECT COUNT(*) FROM ChatMessageEntity").fetchone()[0])
        self.assertIsNone(self.db.execute("SELECT reactionMessageId FROM WorldBattleMemory").fetchone()[0])

    def test_deleting_history_does_not_reimport_a_greeting_or_erase_battle_memory(self):
        with self.db:
            self.memory()
            seed(self.db, "WorldPrivateChatLink", sourceMessageId="greeting", individualId="hackmon", chatMessageId=1)
            self.db.execute("DELETE FROM ChatMessageEntity WHERE individualId='hackmon'")
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM WorldPrivateChatLink").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM WorldBattleMemory").fetchone()[0])
        with self.db:
            self.db.execute("DELETE FROM DigimonIndividual WHERE individualId='hackmon'")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM WorldBattleMemory").fetchone()[0])
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM WorldPrivateChatLink").fetchone()[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
