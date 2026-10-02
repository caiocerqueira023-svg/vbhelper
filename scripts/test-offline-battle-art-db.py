"""Check production battle-art queries against SQLite and the bundled attack catalog."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"
DAOS = APP / "java/com/github/nacabaro/vbhelper/daos"
SCHEMAS = ROOT / "app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase"
ASSETS = APP / "assets/battle_sprites"


def queries(file_name):
    return {
        name: multiline or single
        for multiline, single, name in re.findall(
            r'@Query\(\s*(?:"""(.*?)"""|"([^"\n]*)")\s*\)\s*(?:suspend\s+)?fun\s+(\w+)',
            (DAOS / file_name).read_text(encoding="utf-8"), re.S
        )
    }


def seed(db, table, **overrides):
    values = dict(overrides)
    for _, column, kind, not_null, default, _ in db.execute(f"PRAGMA table_info(`{table}`)"):
        if column not in values and not_null and default is None and column != "id":
            values[column] = "" if kind == "TEXT" else b"\0" if kind == "BLOB" else 0
    db.execute(
        f"INSERT INTO `{table}` ({','.join('`' + c + '`' for c in values)}) VALUES ({','.join('?' for _ in values)})",
        tuple(values.values()),
    )


class OfflineBattleArtSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA foreign_keys = ON")
        schema = json.loads((SCHEMAS / "28.json").read_text(encoding="utf-8"))["database"]
        for entity in schema["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        migration = (APP / "java/com/github/nacabaro/vbhelper/database/CardAttackArtSchema.kt").read_text(encoding="utf-8")
        self.migration_sql = re.findall(r'db\.execSQL\("""(.*?)"""\.trimIndent\(\)\)', migration, re.S)
        for sql in self.migration_sql:
            self.db.execute(sql)
        seed(self.db, "Sprite", id=1)
        # Local row IDs intentionally differ from real DiM/BEM numbers, including DiM 000.
        for row_id, card_number, slot, is_bem in ((12, 0, 3, False), (7, 137, 2, True)):
            seed(self.db, "Card", id=row_id, cardId=card_number, isBEm=is_bem)
            seed(self.db, "CardCharacter", id=row_id, cardId=row_id, spriteId=1,
                 charaIndex=slot, stage=3, attribute="Vaccine")
            seed(self.db, "DigimonIndividual", individualId=f"owned-{row_id}")
            seed(self.db, "UserCharacter", id=row_id, charId=row_id, individualId=f"owned-{row_id}")
            seed(self.db, "DigimonIndividual", individualId=f"wild-{row_id}")
            seed(self.db, "WorldSpawn", id=row_id, cardCharacterId=row_id, individualId=f"wild-{row_id}",
                 expiresAt=100_000, recruitmentState="WILD")

    def owned(self):
        return {row["sourceCharacterId"]: row for row in self.db.execute(
            queries("UserCharacterDao.kt")["getAllBattleParticipantProfiles"]
        )}

    def test_stored_partners_use_real_card_numbers_for_both_dim_and_bem(self):
        rows = self.owned()
        self.assertEqual("dim000_mon04", rows[12]["externalCharacterId"])
        self.assertEqual("dim137_mon03", rows[7]["externalCharacterId"])

    def test_every_radar_projection_carries_the_asset_identity_and_local_card_key(self):
        sql = queries("WorldSpawnDao.kt")
        for query_name in ("getActiveSpawnsWithDetails", "getPendingRecruitsWithDetails", "getSpawnById"):
            with self.subTest(query=query_name):
                state = "PENDING_RECRUITMENT" if query_name == "getPendingRecruitsWithDetails" else "WILD"
                self.db.execute("UPDATE WorldSpawn SET recruitmentState=?", (state,))
                for row_id, expected in ((12, "dim000_mon04"), (7, "dim137_mon03")):
                    rows = self.db.execute(sql[query_name], {"now": 0, "spawnId": row_id}).fetchall()
                    row = next(row for row in rows if row["id"] == row_id)
                    self.assertIn("externalCharacterId", row.keys())
                    self.assertEqual(expected, row["externalCharacterId"])
                    self.assertEqual(row_id, row["cardId"])

    def test_selected_identity_resolves_the_digimons_small_and_large_attack_files(self):
        records = json.loads((ASSETS / "extracted_digimon_stats/character_data/MonoBehaviour_CharacterData.json")
                             .read_text(encoding="utf-8"))["DataList"]
        catalog = {re.search(r"charaId='([^']+)'", record)[1]: record for record in records}
        record = catalog[self.owned()[12]["externalCharacterId"]]
        for field, expected in (("smalefilename", "atk_s_15"), ("laugeFileName", "atk_l_14")):
            with self.subTest(variant=field):
                file_name = re.search(rf"{field}='([^']+)'", record)[1]
                self.assertEqual(expected, file_name)
                self.assertTrue((ASSETS / f"extracted_atksprites/{file_name}.png").is_file())

    def test_custom_attack_assignments_are_scoped_to_local_species_even_when_card_numbers_collide(self):
        self.db.execute("UPDATE Card SET cardId=0")
        seed(self.db, "CardAttackArt", cardCharacterId=12, smallAttackId=7, largeAttackId=8)
        seed(self.db, "CardAttackArt", cardCharacterId=7, smallAttackId=15, largeAttackId=14)
        sql = queries("CardAttackArtDao.kt")["getForCharacter"]
        for species, expected in ((12, (7, 8)), (7, (15, 14))):
            row = self.db.execute(sql, {"characterId": species}).fetchone()
            self.assertEqual(expected, (row["smallAttackId"], row["largeAttackId"]))
            card_rows = self.db.execute(queries("CardAttackArtDao.kt")["getForCard"], {"cardId": species}).fetchall()
            self.assertEqual([species], [row["cardCharacterId"] for row in card_rows])

    def test_attack_art_migration_preserves_existing_individuals_and_matches_fresh_room_schema(self):
        before = {table: [tuple(row) for row in self.db.execute(f"SELECT * FROM {table}")] for table in (
            "Card", "CardCharacter", "UserCharacter", "DigimonIndividual", "WorldSpawn"
        )}
        # Repeating the additive migration also supports artificial version-restamp tests.
        for sql in self.migration_sql:
            self.db.execute(sql)
        schema = json.loads((SCHEMAS / "29.json").read_text(encoding="utf-8"))["database"]
        entity = next(entity for entity in schema["entities"] if entity["tableName"] == "CardAttackArt")
        fresh = sqlite3.connect(":memory:")
        self.addCleanup(fresh.close)
        fresh.execute(entity["createSql"].replace("${TABLE_NAME}", "CardAttackArt"))
        for pragma in ("table_info", "foreign_key_list"):
            self.assertEqual(fresh.execute(f"PRAGMA {pragma}(CardAttackArt)").fetchall(),
                             [tuple(row) for row in self.db.execute(f"PRAGMA {pragma}(CardAttackArt)")])
        for table, rows in before.items():
            self.assertEqual(rows, [tuple(row) for row in self.db.execute(f"SELECT * FROM {table}")])

    def test_embedded_bem_pixels_survive_storage_and_card_deletion_cascades_only_their_art(self):
        seed(self.db, "CardAttackArt", cardCharacterId=7, smallAttackId=39, largeAttackId=22,
             smallPixels=b"\x00\xf8", smallWidth=1, smallHeight=1,
             largePixels=b"\xe0\x07", largeWidth=1, largeHeight=1)
        row = self.db.execute(queries("CardAttackArtDao.kt")["getForCharacter"], {"characterId": 7}).fetchone()
        self.assertEqual((b"\x00\xf8", 1, 1), (row["smallPixels"], row["smallWidth"], row["smallHeight"]))
        self.assertEqual((b"\xe0\x07", 1, 1), (row["largePixels"], row["largeWidth"], row["largeHeight"]))
        self.db.execute("DELETE FROM Card WHERE id=7")
        self.assertEqual(0, self.db.execute("SELECT COUNT(*) FROM CardAttackArt").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT COUNT(*) FROM UserCharacter WHERE id=12").fetchone()[0])


if __name__ == "__main__":
    unittest.main(verbosity=2)
