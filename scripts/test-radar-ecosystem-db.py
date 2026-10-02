"""Exercise the production 24->25 SQL with exported schemas and in-memory SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / "app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase"
DATABASE = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/database/AppDatabase.kt"
MIGRATION = DATABASE.read_text(encoding="utf-8").split("val MIGRATION_24_25 =", 1)[1].split("val MIGRATION_20_21 =", 1)[0]
SQL = [multiline or single for multiline, single in re.findall(
    r'db\.execSQL\(\s*(?:"""(.*?)"""\.trimIndent\(\)|"([^"\n]*)")\s*\)', MIGRATION, re.S
)]


def create_database(version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys = ON")
    for entity in schema["entities"]:
        table = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table))
    return db


def seed(db, table, **overrides):
    values = dict(overrides)
    for _, column, kind, not_null, default, _ in db.execute(f"PRAGMA table_info(`{table}`)"):
        if column not in values and not_null and default is None and column != "id":
            values[column] = "" if kind == "TEXT" else b"\0" if kind == "BLOB" else 0
    db.execute(
        f"INSERT INTO `{table}` ({','.join('`' + c + '`' for c in values)}) VALUES ({','.join('?' for _ in values)})",
        tuple(values.values()),
    )


class RadarEcosystemMigrationTest(unittest.TestCase):
    def setUp(self):
        self.db = create_database(24)
        self.addCleanup(self.db.close)
        seed(self.db, "Card", id=1, name="Fixture")
        seed(self.db, "Sprite", id=1)
        seed(self.db, "CardCharacter", id=100, cardId=1, spriteId=1)
        for individual, state in (("wild", "WILD"), ("pending", "PENDING_RECRUITMENT"), ("recruited", "RECRUITED")):
            seed(self.db, "DigimonIndividual", individualId=individual, nickname=f"{individual}-name")
            seed(self.db, "WildRelationship", individualId=individual, cardCharacterId=100,
                 trust=82, contactUnlockedAt=1000, recruitmentState=state)
            seed(self.db, "ChatMessageEntity", individualId=individual, role="user", content=f"private-{individual}")
            if state == "RECRUITED":
                seed(self.db, "UserCharacter", individualId=individual, charId=100)
            else:
                seed(self.db, "WorldSpawn", individualId=individual, cardCharacterId=100,
                     latitude=-23.5, longitude=-46.6, mood=82, recruitmentState=state, expiresAt=999999)
        self.before = {table: self.db.execute(f"SELECT * FROM `{table}`").fetchall() for table in (
            "DigimonIndividual", "WildRelationship", "ChatMessageEntity", "UserCharacter"
        )}
        self.assertEqual(11, len(SQL), "Every production migration statement must be extracted")
        with self.db:
            for statement in SQL:
                self.db.execute(statement)

    def test_existing_positions_are_homes_and_new_social_state_is_neutral(self):
        rows = self.db.execute("""
            SELECT individualId, latitude, longitude, homeLatitude, homeLongitude,
                   wanderTargetLatitude, wanderTargetLongitude, movementState,
                   anchorRadiusMeters, denId, ecosystemEmotion, mood, recruitmentState
            FROM WorldSpawn ORDER BY individualId
        """).fetchall()
        self.assertEqual([
            ("pending", -23.5, -46.6, -23.5, -46.6, None, None, "HOME", 25.0, None, 0, 82, "PENDING_RECRUITMENT"),
            ("wild", -23.5, -46.6, -23.5, -46.6, None, None, "HOME", 25.0, None, 0, 82, "WILD"),
        ], rows)

    def test_identity_trust_private_messages_and_storage_are_unchanged(self):
        for table, rows in self.before.items():
            self.assertEqual(rows, self.db.execute(f"SELECT * FROM `{table}`").fetchall(), table)
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_migrated_tables_match_fresh_room_schema_including_defaults_and_indexes(self):
        fresh = create_database(25)
        try:
            for table in ("WorldSpawn", "WorldEcosystemSession"):
                # Column order differs between ALTER TABLE and Room fresh creation.
                def columns(db):
                    return {row[1]: row[2:] for row in db.execute(f"PRAGMA table_info(`{table}`)")}
                self.assertEqual(columns(fresh), columns(self.db), table)
                self.assertEqual(fresh.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
                                 self.db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall())
                self.assertEqual(fresh.execute(f"PRAGMA index_list(`{table}`)").fetchall(),
                                 self.db.execute(f"PRAGMA index_list(`{table}`)").fetchall())
        finally:
            fresh.close()

    def test_session_can_store_a_freeze_and_fractional_tick_without_losing_seed(self):
        self.db.execute("""
            INSERT INTO WorldEcosystemSession VALUES('radar-local', 42, 1, 37, 999, 123456, 8, -23.5, -46.6, 'PLAYER_BATTLE')
        """)
        self.assertEqual((42, 37, 999, "PLAYER_BATTLE"), self.db.execute(
            "SELECT seed, tickIndex, tickRemainderMillis, pauseReason FROM WorldEcosystemSession"
        ).fetchone())

    def test_spawn_expiry_retains_individual_player_contacts_and_chat(self):
        self.db.execute("DELETE FROM WorldSpawn WHERE individualId = 'wild'")
        for table, rows in self.before.items():
            self.assertEqual(rows, self.db.execute(f"SELECT * FROM `{table}`").fetchall(), table)


if __name__ == "__main__":
    unittest.main(verbosity=2)
