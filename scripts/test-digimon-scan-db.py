"""Exercise the additive scan migration and production conversion-consumption SQL."""
from pathlib import Path
import re
import runpy
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
FIXTURE = runpy.run_path(str(ROOT / "scripts/test-radar-ecosystem-db.py"))
create_database, seed = FIXTURE["create_database"], FIXTURE["seed"]
SOURCE_ROOT = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper"
SOURCE = (SOURCE_ROOT / "database/DigimonScanSchema.kt").read_text(encoding="utf-8")
SQL = [multiline or single for multiline, single in re.findall(
    r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', SOURCE, re.S)]
DAO = (SOURCE_ROOT / "daos/DigimonScanDao.kt").read_text(encoding="utf-8")
CONSUME = re.search(r'@Query\("([^"]+)"\)\s*suspend fun consumeCompleteScan', DAO).group(1)
COLLECTION = {name: query for query, name in re.findall(
    r'@Query\("""(.*?)"""\)\s*fun (\w+)', DAO, re.S)}.get("observeCollection")


class DigimonScanSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(31)
        self.addCleanup(self.db.close)
        seed(self.db, "Card", id=1, name="DIM fixture")
        seed(self.db, "Card", id=2, name="Custom variant", cardId=0)
        seed(self.db, "Sprite", id=1)
        seed(self.db, "CardCharacter", id=100, cardId=1, spriteId=1)
        seed(self.db, "CardCharacter", id=200, cardId=2, spriteId=1)
        seed(self.db, "DigimonIndividual", individualId="partner", nickname="Existing partner")
        seed(self.db, "UserCharacter", id=10, individualId="partner", charId=100)
        seed(self.db, "ChatMessageEntity", individualId="partner", role="user", content="Existing conversation")
        seed(self.db, "WorldInteraction", id="old-battle", type="BATTLE", origin="DIRECT_PLAYER", state="ENDED")
        seed(self.db, "WorldInteractionResult", interactionId="old-battle", outcome="ALLIED_VICTORY", recordedAt=1)
        self.before = {table: self.db.execute(f"SELECT * FROM {table}").fetchall()
                       for table in ("UserCharacter", "DigimonIndividual", "ChatMessageEntity", "WorldInteractionResult")}
        self.db.commit()
        self.assertEqual(3, len(SQL))
        with self.db:
            for statement in SQL:
                self.db.execute(statement)

    def test_additive_migration_matches_fresh_schema_and_preserves_existing_data(self):
        fresh = create_database(32)
        self.addCleanup(fresh.close)
        for table in ("DigimonScanProgress", "DigimonScanReward"):
            for pragma in ("table_info", "foreign_key_list", "index_list"):
                self.assertEqual(fresh.execute(f"PRAGMA {pragma}({table})").fetchall(),
                                 self.db.execute(f"PRAGMA {pragma}({table})").fetchall(), (table, pragma))
        for table, rows in self.before.items():
            self.assertEqual(rows, self.db.execute(f"SELECT * FROM {table}").fetchall(), table)
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM DigimonScanProgress").fetchone()[0])

    def test_progress_is_per_local_card_character_and_consumption_is_guarded(self):
        with self.db:
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=100, updatedAt=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=200, percentage=80, updatedAt=1)
            self.assertEqual(1, self.db.execute(CONSUME, {"cardCharacterId": 100, "now": 2}).rowcount)
            self.assertEqual(0, self.db.execute(CONSUME, {"cardCharacterId": 100, "now": 3}).rowcount)
            self.assertEqual(0, self.db.execute(CONSUME, {"cardCharacterId": 200, "now": 3}).rowcount)
        self.assertEqual([(100, 0, 2), (200, 80, 1)], self.db.execute("SELECT * FROM DigimonScanProgress ORDER BY cardCharacterId").fetchall())

    def test_failed_conversion_restores_consumed_data_and_new_identity(self):
        with self.db:
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=100, updatedAt=1)
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.db.execute(CONSUME, {"cardCharacterId": 100, "now": 2})
                seed(self.db, "DigimonIndividual", individualId="new-partner")
                seed(self.db, "UserCharacter", individualId="new-partner", charId=999)
        self.assertEqual(100, self.db.execute("SELECT percentage FROM DigimonScanProgress").fetchone()[0])
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM DigimonIndividual WHERE individualId='new-partner'").fetchone()[0])

    def test_receipts_are_unique_and_event_cleanup_preserves_collection_progress(self):
        with self.db:
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=20, updatedAt=1)
            seed(self.db, "DigimonScanReward", interactionId="old-battle", cardCharacterId=100, percentageBefore=0, percentageAfter=20)
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                seed(self.db, "DigimonScanReward", interactionId="old-battle", cardCharacterId=100, percentageBefore=20, percentageAfter=40)
        with self.db:
            self.db.execute("DELETE FROM WorldInteraction WHERE id='old-battle'")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM DigimonScanReward").fetchone()[0])
        self.assertEqual(20, self.db.execute("SELECT percentage FROM DigimonScanProgress").fetchone()[0])

    def test_card_deletion_cascades_only_its_progress_and_receipts(self):
        with self.db:
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=100, updatedAt=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=200, percentage=60, updatedAt=1)
            seed(self.db, "DigimonScanReward", interactionId="old-battle", cardCharacterId=100, percentageBefore=80, percentageAfter=100)
            self.db.execute("DELETE FROM Card WHERE id=1")
        self.assertEqual([(200, 60, 1)], self.db.execute("SELECT * FROM DigimonScanProgress").fetchall())
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM DigimonScanReward").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def collection(self):
        self.assertIsNotNone(COLLECTION, "The production scan collection query must exist")
        cursor = self.db.execute(COLLECTION)
        return [dict(zip([column[0] for column in cursor.description], row)) for row in cursor.fetchall()]

    def test_collection_shows_partial_and_complete_scans_ready_first_but_not_zero_or_unscanned(self):
        with self.db:
            seed(self.db, "CardCharacter", id=300, cardId=1, spriteId=1)
            seed(self.db, "CardCharacter", id=400, cardId=1, spriteId=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=20, updatedAt=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=200, percentage=100, updatedAt=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=300, percentage=0, updatedAt=1)
        rows = self.collection()
        self.assertEqual([200, 100], [row["id"] for row in rows])
        self.assertEqual([100, 20], [row["percentage"] for row in rows])
        self.assertEqual(["Custom variant", "DIM fixture"], [row["cardName"] for row in rows])

    def test_collection_resolves_imported_art_and_species_by_local_keys(self):
        with self.db:
            seed(self.db, "Sprite", id=2, spriteIdle1=b"\xab\xcd", width=33, height=34)
            seed(self.db, "CardCharacter", id=311, cardId=1, spriteId=2, stage=4, charaIndex=7)
            seed(self.db, "SpeciesProfile", cardCharacterId=311, speciesName="", matchedName="Greymon")
            seed(self.db, "DigimonScanProgress", cardCharacterId=311, percentage=60, updatedAt=1)
        row = self.collection()[0]
        self.assertEqual((311, 7, 4, "Greymon", b"\xab\xcd", 33, 34),
                         tuple(row[key] for key in ("id", "charaIndex", "stage", "speciesName", "spriteIdle", "spriteWidth", "spriteHeight")))

    def test_consumed_scan_leaves_collection_without_hiding_other_variants(self):
        with self.db:
            seed(self.db, "DigimonScanProgress", cardCharacterId=100, percentage=100, updatedAt=1)
            seed(self.db, "DigimonScanProgress", cardCharacterId=200, percentage=40, updatedAt=1)
            self.db.execute(CONSUME, {"cardCharacterId": 100, "now": 2})
        self.assertEqual([200], [row["id"] for row in self.collection()])


if __name__ == "__main__":
    unittest.main(verbosity=2)
