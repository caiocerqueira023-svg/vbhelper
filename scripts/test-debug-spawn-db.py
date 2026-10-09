"""Execute the debug picker and automatic-spawn production DAO queries on SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
DAO = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/CharacterDao.kt"


def query_for(method):
    source = DAO.read_text(encoding="utf-8")
    for sql, name in re.findall(r'@Query\(\s*"""(.*?)"""\s*\)\s*suspend fun (\w+)\(', source, re.S):
        if name == method:
            return sql
    raise AssertionError(f"Production query not found: {method}")


def seed(db, table, **values):
    for _, column, kind, required, default, _ in db.execute(f"PRAGMA table_info(`{table}`)"):
        if column not in values and required and default is None and column != "id":
            values[column] = "" if kind == "TEXT" else b"\0" if kind == "BLOB" else 0
    db.execute(f"INSERT INTO `{table}` ({','.join('`' + c + '`' for c in values)}) "
               f"VALUES ({','.join('?' for _ in values)})", tuple(values.values()))


class DebugSpawnCatalogTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA foreign_keys = ON")
        schema = json.loads((ROOT / "app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase/32.json")
                            .read_text(encoding="utf-8"))["database"]
        for entity in schema["entities"]:
            self.db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        seed(self.db, "Card", id=1, name="Enabled card", worldSpawnsEnabled=1)
        seed(self.db, "Card", id=2, name="Disabled card", worldSpawnsEnabled=0)
        seed(self.db, "Sprite", id=1, spriteIdle1=b"enabled pixels")
        seed(self.db, "Sprite", id=2, spriteIdle1=b"disabled pixels")
        seed(self.db, "CardCharacter", id=100, cardId=1, spriteId=1, charaIndex=3)
        seed(self.db, "CardCharacter", id=200, cardId=2, spriteId=2, charaIndex=3)

    def test_all_loaded_variants_are_selectable_without_discovery_or_automatic_spawn_permission(self):
        choices = self.db.execute(query_for("getDebugSpawnCharacters")).fetchall()
        self.assertEqual({100, 200}, {row["id"] for row in choices})
        automatic = self.db.execute(query_for("getCharactersForWorldSpawns")).fetchall()
        self.assertEqual([100], [row["id"] for row in automatic])
        by_id = {row["id"]: row for row in choices}
        self.assertEqual("Disabled card", by_id[200]["cardName"])
        self.assertEqual(b"disabled pixels", by_id[200]["spriteIdle"])
        self.assertIsNone(by_id[200]["speciesName"])

    def test_name_uses_manual_species_then_official_fallback_without_duplicate_rows(self):
        seed(self.db, "SpeciesProfile", cardCharacterId=100, speciesName="Edited name", matchedName="Official name")
        seed(self.db, "SpeciesProfile", cardCharacterId=200, speciesName="", matchedName="Official fallback")
        rows = self.db.execute(query_for("getDebugSpawnCharacters")).fetchall()
        self.assertEqual({100: "Edited name", 200: "Official fallback"},
                         {row["id"]: row["speciesName"] for row in rows})
        self.assertEqual(2, len(rows))

    def test_deleted_cards_disappear_without_removing_other_variants(self):
        self.db.execute("DELETE FROM Card WHERE id = 2")
        rows = self.db.execute(query_for("getDebugSpawnCharacters")).fetchall()
        self.assertEqual([100], [row["id"] for row in rows])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())


if __name__ == "__main__":
    unittest.main()
