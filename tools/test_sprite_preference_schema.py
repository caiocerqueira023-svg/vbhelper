"""Execute the shipped cosmetic migration and stat projection against real SQLite."""
import re
import unittest

from tools.test_participation_schema import ROOT, DATABASE, database, schema, seed


def migrate(db):
    source = (DATABASE / "SpritePreferenceSchema.kt").read_text(encoding="utf-8")
    query = re.search(r'const val DISPLAY_SPRITE_QUERY = """(.*?)"""', source, re.S)[1]
    for statement in re.findall(r'db\.execSQL\("(.*?)"\)', source):
        db.execute(statement.replace("$DISPLAY_SPRITE_QUERY", query))
    archive = (DATABASE / "WatchRepresentationSnapshotSchema.kt").read_text(encoding="utf-8")
    for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', archive, re.S):
        db.execute(triple or single)


class SpritePreferenceSchemaTest(unittest.TestCase):
    def fixture(self):
        db = database(53)
        self.addCleanup(db.close)
        seed(db, "Card", {"id": 1, "cardId": 1, "name": "Fixture BEM", "isBEm": 1})
        for sprite in [1, 2, -3, -4, -1]:
            seed(db, "Sprite", {"id": sprite, "width": 1, "height": 1})
        seed(db, "CardCharacter", {"id": 100, "cardId": 1, "spriteId": 1, "attribute": "Vaccine",
                                   "speciesId": "digimon.dukemon", "baseHp": 3000, "baseBp": 4500, "baseAp": 1000})
        seed(db, "CardCharacter", {"id": -7, "cardId": None, "spriteId": -3, "attribute": "Vaccine",
                                   "speciesId": "digimon.dukemon", "catalogSpeciesId": "digimon.dukemon"})
        seed(db, "SpeciesProfile", {"cardCharacterId": 100, "speciesName": "Dukemon", "appearanceKey": "dukemon"})
        seed(db, "SpeciesSpriteChoice", {"speciesKey": "dukemon", "spriteId": 2})
        seed(db, "SpeciesDefaultSprite", {"cardCharacterId": 100, "spriteId": -3})
        seed(db, "DigimonIndividual", {"individualId": "fixture", "createdAt": 55, "nickname": "Retained"})
        seed(db, "UserCharacter", {"id": 1, "individualId": "fixture", "charId": -7, "characterType": "APP",
                                   "injuryStatus": "None", "isActive": 1})
        seed(db, "AppPartnerProgress", {"individualId": "fixture", "trainingHp": 222, "trainingBp": 333,
                                        "trainingAp": 111, "originBasisPoints": 11000})
        seed(db, "UniversalJourneyAccount", {"individualId": "fixture", "stateJson": '{"retained":"growth"}'})
        seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "fixture"})
        seed(db, "NativeGameSnapshot", {"id": 1, "bindingId": "binding", "individualId": "fixture", "recordJson": "retained native bytes"})
        seed(db, "AppSourceState", {"id": 1, "mode": "EXPANDED"})
        return db

    def test_upgrade_preserves_all_native_progress_and_identity_tables_and_matches_room(self):
        db = self.fixture()
        protected = [item["tableName"] for item in schema(53)["entities"] if item["tableName"] != "AppSourceState"]
        before = {table: db.execute(f"SELECT * FROM `{table}`").fetchall() for table in protected}
        migrate(db)
        self.assertEqual("DOT_FIRST", db.execute("SELECT mode FROM AppSourceState").fetchone()[0])
        for preference in ["VB_FIRST", "DOT_FIRST", "VB_FIRST"]:
            db.execute("UPDATE AppSourceState SET mode=?", (preference,))
            for table, rows in before.items():
                self.assertEqual(rows, db.execute(f"SELECT * FROM `{table}`").fetchall(), table)
        expected = database(54)
        self.addCleanup(expected.close)
        for pragma in ["table_info", "foreign_key_list", "index_list"]:
            self.assertEqual(expected.execute(f"PRAGMA {pragma}(WatchRepresentationSnapshot)").fetchall(),
                             db.execute(f"PRAGMA {pragma}(WatchRepresentationSnapshot)").fetchall())
        self.assertEqual(expected.execute("SELECT sql FROM sqlite_master WHERE name='DisplaySprite'").fetchone(),
                         db.execute("SELECT sql FROM sqlite_master WHERE name='DisplaySprite'").fetchone())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_both_priorities_have_mutual_fallback_and_preserve_the_saved_choice(self):
        db = self.fixture()
        migrate(db)
        displayed = lambda: db.execute("SELECT id FROM DisplaySprite WHERE cardCharacterId=100").fetchone()[0]
        self.assertEqual(-3, displayed())
        db.execute("UPDATE AppSourceState SET mode='VB_FIRST'")
        self.assertEqual(2, displayed())
        db.execute("UPDATE SpeciesSpriteChoice SET spriteId=-4")
        self.assertEqual(1, displayed())  # A Dot choice does not defeat VB-first native art.
        db.execute("UPDATE AppSourceState SET mode='DOT_FIRST'")
        self.assertEqual(-4, displayed())
        db.execute("UPDATE SpeciesSpriteChoice SET spriteId=2")
        db.execute("DELETE FROM SpeciesDefaultSprite")
        db.execute("UPDATE CardCharacter SET spriteId=-1 WHERE id=-7")
        self.assertEqual(2, displayed())  # No Dot set: the loaded VB choice is the fallback.

    def test_no_artwork_still_produces_a_displayable_neutral_partner(self):
        db = self.fixture()
        migrate(db)
        db.execute("UPDATE CardCharacter SET speciesId='digimon.noart',spriteId=-1 WHERE id=-7")
        for preference in ["VB_FIRST", "DOT_FIRST"]:
            db.execute("UPDATE AppSourceState SET mode=?", (preference,))
            self.assertEqual((-1,), db.execute("SELECT id FROM DisplaySprite WHERE cardCharacterId=-7").fetchone())
            self.assertEqual((1,), db.execute("SELECT isActive FROM UserCharacter WHERE id=1").fetchone())

    def test_dim_override_changes_only_effective_stats_not_retained_growth(self):
        db = self.fixture()
        migrate(db)
        source = (ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/UserCharacterDao.kt").read_text(encoding="utf-8")
        query = next(sql for sql in re.findall(r'"""(.*?)"""', source, re.S) if "AS sourceCharacterId" in sql)
        def profile():
            cursor = db.execute(query)
            return dict(zip([column[0] for column in cursor.description], cursor.fetchone()))
        before = profile()
        retained = db.execute("SELECT * FROM AppPartnerProgress").fetchall()
        db.execute("UPDATE UserCharacter SET exportCardCharacterId=100 WHERE id=1")
        assigned = profile()
        self.assertEqual((3000, 0, 0, 0, 10000), tuple(assigned[key] for key in
                         ["baseHp", "trainingHp", "trainingAp", "trainingBp", "originBasisPoints"]))
        self.assertEqual(retained, db.execute("SELECT * FROM AppPartnerProgress").fetchall())
        db.execute("UPDATE UserCharacter SET exportCardCharacterId=NULL WHERE id=1")
        self.assertEqual(before, profile())
        self.assertEqual("retained native bytes", db.execute("SELECT recordJson FROM NativeGameSnapshot").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
