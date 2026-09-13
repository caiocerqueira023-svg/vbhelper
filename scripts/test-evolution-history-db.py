"""Exercise the production DAO SQL against SQLite; no phone data is accessed."""
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
DAO = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/UserCharacterDao.kt"
QUERIES = {
    name: multiline or single
    for multiline, single, name in re.findall(
        r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
        DAO.read_text(encoding="utf-8"), re.S
    )
}
REPAIR_QUERIES = {
    name: multiline or single
    for multiline, single, name in re.findall(
        r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
        DAO.with_name("EvolutionHistoryDao.kt").read_text(encoding="utf-8"), re.S
    )
}


class EvolutionHistorySqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.executescript("""
            CREATE TABLE TransformationHistory(id INTEGER PRIMARY KEY, monId INTEGER, stageId INTEGER, transformationDate INTEGER);
            CREATE TABLE UserCharacter(id INTEGER PRIMARY KEY, charId INTEGER, vitalPoints INTEGER, trophies INTEGER, currentPhaseBattlesWon INTEGER, transformationCountdown INTEGER, individualId TEXT);
            CREATE TABLE CardCharacter(id INTEGER PRIMARY KEY, charaIndex INTEGER, stage INTEGER, spriteId INTEGER, cardId INTEGER);
            CREATE TABLE Sprite(id INTEGER PRIMARY KEY, spriteIdle1 BLOB, width INTEGER, height INTEGER);
            CREATE TABLE Card(id INTEGER PRIMARY KEY, name TEXT);
            INSERT INTO Card VALUES(1, 'Custom');
            INSERT INTO Sprite VALUES(1, X'00', 1, 1);
            INSERT INTO CardCharacter VALUES(2, 2, 2, 1, 1), (3, 3, 3, 1, 1);
            INSERT INTO UserCharacter VALUES(1, 3, 9999, 30, 25, 1, '1stmaru');
            -- Recruitment's phone date is newer than the subsequent watch evolution.
            INSERT INTO TransformationHistory VALUES(10, 1, 2, 9000), (11, 1, 3, 1000);
            -- Another individual of the same species must remain untouched.
            INSERT INTO TransformationHistory VALUES(12, 2, 2, 500);
        """)

    def tearDown(self):
        self.db.close()

    def test_home_and_export_follow_lineage_even_with_reversed_dates(self):
        rows = self.db.execute(QUERIES["getTransformationHistory"], {"monId": 1}).fetchall()
        self.assertEqual([10, 11], [row[0] for row in rows])
        exported = self.db.execute(QUERIES["getTransformationHistoryForExport"], {"monId": 1}).fetchall()
        self.assertEqual([2, 3], [row[0] for row in exported])

    def test_degeneration_removes_later_evolution_without_touching_other_individual(self):
        self.db.execute(QUERIES["deleteTransformationsAfter"], {"characterId": 1, "historyId": 10})
        self.assertEqual([(10, 1, 2), (12, 2, 2)], self.db.execute(
            "SELECT id, monId, stageId FROM TransformationHistory ORDER BY id").fetchall())

    def test_selected_history_must_belong_to_this_individual(self):
        self.assertIsNone(self.db.execute(QUERIES["getTransformationHistoryEntry"],
                                        {"characterId": 1, "historyId": 12}).fetchone())

    def test_degeneration_only_resets_vitals_and_keeps_identity_and_other_stats(self):
        self.db.execute(QUERIES["degenerateCharacter"], {"characterId": 1, "stageId": 2})
        self.assertEqual((2, 0, 30, 25, 1, "1stmaru"), self.db.execute(
            "SELECT charId, vitalPoints, trophies, currentPhaseBattlesWon, transformationCountdown, individualId FROM UserCharacter WHERE id = 1"
        ).fetchone())

    def test_repair_uses_local_card_primary_key_and_reads_all_storage(self):
        self.db.executescript("""
            INSERT INTO Card VALUES(2, 'Other custom with same slots');
            INSERT INTO CardCharacter VALUES(20, 2, 2, 1, 2), (30, 3, 3, 1, 2);
            INSERT INTO UserCharacter VALUES(2, 30, 1000, 20, 12, 10, 'other');
            CREATE TABLE PossibleTransformations(charaId INTEGER, toCharaId INTEGER);
            CREATE TABLE CardFusions(fromCharaId INTEGER, toCharaId INTEGER);
            INSERT INTO PossibleTransformations VALUES(2, 3), (20, 30), (2, NULL);
            INSERT INTO CardFusions VALUES(2, 3);
        """)
        self.assertEqual([(1,), (2,)], self.db.execute(REPAIR_QUERIES["getStoredCharacterIds"]).fetchall())
        self.assertEqual((3, 1, 3, 3), self.db.execute(REPAIR_QUERIES["getCurrentSpecies"], {"characterId": 1}).fetchone())
        self.assertEqual([(2, 1, 2, 2), (3, 1, 3, 3)], self.db.execute(REPAIR_QUERIES["getSpecies"], {"cardId": 1}).fetchall())
        self.assertEqual([(2, 3)], self.db.execute(REPAIR_QUERIES["getRoutes"], {"cardId": 1}).fetchall())
        self.assertEqual([(20, 30)], self.db.execute(REPAIR_QUERIES["getRoutes"], {"cardId": 2}).fetchall())

    def test_replacing_history_keeps_individual_stats_and_other_histories(self):
        before = self.db.execute("SELECT * FROM UserCharacter").fetchall()
        with self.db:
            self.db.execute(REPAIR_QUERIES["deleteHistory"], {"characterId": 1})
            self.db.executemany(
                "INSERT INTO TransformationHistory(monId, stageId, transformationDate) VALUES(?, ?, ?)",
                [(1, 2, 9000), (1, 3, 1000)])
        self.assertEqual(before, self.db.execute("SELECT * FROM UserCharacter").fetchall())
        self.assertEqual([(2, 9000), (3, 1000)], self.db.execute(REPAIR_QUERIES["getHistory"], {"characterId": 1}).fetchall())
        self.assertEqual([(2, 500)], self.db.execute(REPAIR_QUERIES["getHistory"], {"characterId": 2}).fetchall())

    def test_transaction_restores_old_history_if_replacement_fails(self):
        before = self.db.execute("SELECT * FROM TransformationHistory").fetchall()
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.db.execute(REPAIR_QUERIES["deleteHistory"], {"characterId": 1})
                # Simulate an insertion failure after deletion (duplicate primary key).
                self.db.execute("INSERT INTO TransformationHistory VALUES(12, 1, 3, 1000)")
        self.assertEqual(before, self.db.execute("SELECT * FROM TransformationHistory").fetchall())


if __name__ == "__main__":
    unittest.main()
