"""Fast real-SQLite checks for the shipped migration SQL, without an Android device."""
import json
from pathlib import Path
import re
import sqlite3
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
SCHEMAS = ROOT / "app/schemas/com.github.nacabaro.vbhelper.database.AppDatabase"
DATABASE = ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/database"


def schema(version):
    return json.loads((SCHEMAS / f"{version}.json").read_text(encoding="utf-8"))["database"]


def database(version):
    connection = sqlite3.connect(":memory:")
    connection.execute("PRAGMA foreign_keys = ON")
    exported = schema(version)
    for entity in exported["entities"]:
        connection.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            connection.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    for view in exported.get("views", []):
        connection.execute(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))
    return connection


def add_pause_table(connection):
    source = (DATABASE / "ParticipationPauseSchema.kt").read_text(encoding="utf-8")
    connection.execute(re.search(r'const val CREATE = """(.*?)"""', source, re.S)[1])


def source_independent_statements():
    source = (DATABASE / "SourceIndependentSchema.kt").read_text(encoding="utf-8")
    display = (DATABASE / "SpeciesCustomizationSchema.kt").read_text(encoding="utf-8")
    query = re.search(r'const val DISPLAY_SPRITE_QUERY = """(.*?)"""', display, re.S)[1]
    for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""(?:\.trimIndent\(\))?|"(.*?)"\))', source, re.S):
        yield (triple or single).replace("${SpeciesCustomizationSchema.DISPLAY_SPRITE_QUERY}", query)


def mode_sprite_statements():
    source = (DATABASE / "ModeSpriteSchema.kt").read_text(encoding="utf-8")
    query = re.search(r'const val DISPLAY_SPRITE_QUERY = """(.*?)"""', source, re.S)[1]
    for statement in re.findall(r'db\.execSQL\("(.*?)"\)', source):
        yield statement.replace("$DISPLAY_SPRITE_QUERY", query)


def install_runtime_guards(connection):
    """Room's exported schema omits the guards installed by the actual onOpen callback."""
    for filename in ["IndividualIntegrity.kt", "WorldInteractionIntegrity.kt"]:
        source = (DATABASE / filename).read_text(encoding="utf-8")
        for sql in re.findall(r'db.execSQL\("""(.*?)"""\.trimIndent\(\)\)', source, re.S):
            if "CREATE TRIGGER" in sql:
                connection.execute(textwrap.dedent(sql).strip()
                                   .replace("$slotColumn", ", blastMode").replace("$slotValue", ", 'NONE'"))


def seed(connection, table, overrides):
    values = dict(overrides)
    for _, name, affinity, required, default, _ in connection.execute(f"PRAGMA table_info(`{table}`)"):
        if name not in values and required and default is None and name != "id":
            values[name] = {"TEXT": "", "BLOB": b"\0\0"}.get(affinity, 0)
    names = ",".join(f"`{name}`" for name in values)
    connection.execute(f"INSERT INTO `{table}` ({names}) VALUES ({','.join('?' for _ in values)})", list(values.values()))


class ParticipationMigrationTest(unittest.TestCase):
    def fixture(self, version):
        db = database(version)
        self.addCleanup(db.close)
        seed(db, "Card", {"id": 1, "cardId": 1, "name": "Kept card", "officialStatus": "OFFICIAL"})
        seed(db, "Sprite", {"id": 1, "width": 1, "height": 1})
        seed(db, "CardCharacter", {"id": 100, "cardId": 1, "spriteId": 1, "attribute": "Vaccine"})
        seed(db, "SpeciesProfile", {"cardCharacterId": 100, "speciesName": "Dukemon", "matchedName": "Gallantmon",
                                    "appearanceKey": "dukemon", "source": "OFFICIAL_MATCHED", "specialMoves": "[]"})
        seed(db, "DigimonIndividual", {"individualId": "kept", "createdAt": 55, "nickname": "Keep me", "blastMode": "NONE"})
        seed(db, "UserCharacter", {"id": 1, "individualId": "kept", "charId": 100, "vitalPoints": 1234,
                                   "injuryStatus": "None", "characterType": "VBDevice"})
        seed(db, "ChatMessageEntity", {"individualId": "kept", "role": "assistant", "content": "Keep this conversation", "timestamp": 99})
        seed(db, "TransformationHistory", {"monId": 1, "stageId": 100, "transformationDate": 66})
        return db

    def test_42_to_43_preserves_existing_rows_and_matches_exported_room_table(self):
        db = self.fixture(42)
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in
                  ["Card", "Sprite", "CardCharacter", "DigimonIndividual", "UserCharacter", "ChatMessageEntity"]}
        add_pause_table(db)
        add_pause_table(db)  # Opening/retry does not remove data or duplicate metadata.
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall())
        expected = database(43)
        self.addCleanup(expected.close)
        self.assertEqual(expected.execute("PRAGMA table_info(ParticipationPause)").fetchall(),
                         db.execute("PRAGMA table_info(ParticipationPause)").fetchall())
        self.assertEqual(expected.execute("PRAGMA foreign_key_list(ParticipationPause)").fetchall(),
                         db.execute("PRAGMA foreign_key_list(ParticipationPause)").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_pause_requires_a_real_individual_and_deletion_cascades_only_its_pause(self):
        db = self.fixture(42)
        add_pause_table(db)
        db.execute("INSERT INTO ParticipationPause VALUES ('kept', 100000)")
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("INSERT INTO ParticipationPause VALUES ('missing', 100000)")
        db.execute("DELETE FROM DigimonIndividual WHERE individualId = 'kept'")
        self.assertEqual([], db.execute("SELECT * FROM ParticipationPause").fetchall())
        self.assertEqual([(1234,)], db.execute("SELECT vitalPoints FROM UserCharacter").fetchall())

    def test_adventure_resume_preserves_remaining_time_and_history(self):
        db = self.fixture(42)
        seed(db, "Adventure", {"characterId": 1, "finishesAdventure": 160, "originalDuration": 1})
        source = (ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/species/PartnerParticipationGate.kt").read_text(encoding="utf-8")
        statement = re.search(r'sql.execSQL\("(UPDATE Adventure SET finishesAdventure[^"\n]+)"', source)[1]
        db.execute(statement, (3600, "kept"))
        self.assertEqual(60, db.execute("SELECT finishesAdventure FROM Adventure").fetchone()[0] - 3700)
        self.assertEqual("Keep this conversation", db.execute("SELECT content FROM ChatMessageEntity").fetchone()[0])

    def test_43_to_44_rebuilds_shared_definitions_without_losing_rows_or_foreign_keys(self):
        db = self.fixture(43)
        seed(db, "ParticipationPause", {"individualId": "kept", "pausedAt": 1000})
        seed(db, "SpeciesSpriteChoice", {"speciesKey": "dukemon", "spriteId": 1})
        install_runtime_guards(db)
        protected = ["Card", "Sprite", "CardCharacter", "UserCharacter", "DigimonIndividual", "ChatMessageEntity",
                     "TransformationHistory", "SpeciesProfile", "SpeciesSpriteChoice", "ParticipationPause"]
        columns = {name: [row[1] for row in db.execute(f"PRAGMA table_info(`{name}`)")] for name in protected}
        before = {name: db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall() for name in protected}
        # The Android open-helper disables foreign keys before Room begins its upgrade transaction.
        db.commit()
        db.execute("PRAGMA foreign_keys = OFF")
        db.execute("BEGIN")
        for statement in source_independent_statements():
            db.execute(statement)
        db.commit()
        db.execute("PRAGMA foreign_keys = ON")
        install_runtime_guards(db)
        for name, values in before.items():
            self.assertEqual(values, db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall(), name)
        self.assertEqual([(100, 1, 1)], db.execute("SELECT id, cardId, spriteId FROM CardCharacter").fetchall())
        self.assertEqual([("CARD",)], db.execute(
            "SELECT originKind FROM DigimonIndividual WHERE individualId = 'kept'").fetchall())
        expected = database(44)
        self.addCleanup(expected.close)
        for table in ["CardCharacter", "UserCharacter", "AppSourceState", "SpeciesDefaultSprite",
                      "AppPartnerProgress", "FirstPartnerEncounter"]:
            self.assertEqual(expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
                              db.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
                              table)
            self.assertEqual(expected.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
                             db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(), table)
        # ViewInfo compares the SQL text, not just columns. Skipping this hid the launch crash.
        self.assertEqual(expected.execute("SELECT sql FROM sqlite_master WHERE type='view' AND name='DisplaySprite'").fetchone(),
                         db.execute("SELECT sql FROM sqlite_master WHERE type='view' AND name='DisplaySprite'").fetchone())
        self.assertEqual(1, len(db.execute("SELECT * FROM DisplaySprite WHERE cardCharacterId = 100").fetchall()))
        self.assertEqual((1,), db.execute("PRAGMA foreign_keys").fetchone())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        db.execute("INSERT INTO CardCharacter(id, spriteId, charaIndex, stage, attribute, baseHp, baseBp, baseAp, nameSprite, nameWidth, nameHeight, speciesId, catalogSpeciesId) VALUES (-7, 1, 0, 2, 'Vaccine', 3, 10, 2, X'00', 1, 1, 'digimon.agumon', 'digimon.agumon')")
        seed(db, "UserCharacter", {"id": 9, "individualId": "expanded", "charId": -7, "characterType": "APP", "isActive": 0})
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        self.assertEqual([(-7,)], db.execute("SELECT charId FROM UserCharacter WHERE id = 9").fetchall())
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("UPDATE CardCharacter SET cardId = 999 WHERE id = -7")
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "UserCharacter", {"id": 10, "individualId": "kept", "charId": 100, "characterType": "VBDevice"})

    def test_41_binding_defaults_match_the_43_entity_without_erasing_source_data(self):
        db = self.fixture(41)
        source = (DATABASE / "SpeciesBindingSchema.kt").read_text(encoding="utf-8")
        for statement in re.findall(r'db.execSQL\("(ALTER TABLE[^"\n]+)"\)', source):
            db.execute(statement)
        for statement in re.findall(r'db.execSQL\("""(.*?)"""\.trimIndent\(\)\)', source, re.S):
            db.execute(statement)
        self.assertEqual(("Dukemon", "CARD", 100, "Keep me"), db.execute(
            "SELECT speciesName, originKind, originCardCharacterId, nickname FROM DigimonIndividual").fetchone())
        expected = database(43)
        self.addCleanup(expected.close)
        self.assertEqual(expected.execute("PRAGMA table_info(DigimonIndividual)").fetchall(),
                          db.execute("PRAGMA table_info(DigimonIndividual)").fetchall())

    def test_44_to_45_expanded_ignores_normalization_and_vb_restores_the_saved_choice(self):
        db = self.fixture(44)
        seed(db, "Sprite", {"id": 2, "width": 1, "height": 1, "spriteIdle1": b"\x01\x00"})
        seed(db, "Sprite", {"id": -3, "width": 1, "height": 1, "spriteIdle1": b"\x02\x00"})
        seed(db, "SpeciesSpriteChoice", {"speciesKey": "dukemon", "spriteId": 2})
        seed(db, "SpeciesDefaultSprite", {"cardCharacterId": 100, "spriteId": -3})
        seed(db, "AppSourceState", {"id": 1, "mode": "VITAL_BRACELET"})
        db.execute("UPDATE CardCharacter SET speciesId = 'digimon.dukemon' WHERE id = 100")
        seed(db, "CardCharacter", {"id": -7, "spriteId": -3, "cardId": None,
                                  "speciesId": "digimon.dukemon", "catalogSpeciesId": "digimon.dukemon"})
        protected = ["Card", "Sprite", "CardCharacter", "UserCharacter", "DigimonIndividual", "ChatMessageEntity",
                     "TransformationHistory", "SpeciesProfile", "SpeciesSpriteChoice", "SpeciesDefaultSprite"]
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in protected}
        for statement in mode_sprite_statements():
            db.execute(statement)
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)

        def displayed():
            return db.execute("SELECT id,spriteIdle1 FROM DisplaySprite WHERE cardCharacterId = 100").fetchone()

        self.assertEqual((2, b"\x01\x00"), displayed())
        db.execute("UPDATE AppSourceState SET mode = 'EXPANDED'")
        self.assertEqual((-3, b"\x02\x00"), displayed())
        # Even a freshly imported slot without its own default uses the canonical Dot definition.
        db.execute("DELETE FROM SpeciesDefaultSprite WHERE cardCharacterId = 100")
        self.assertEqual((-3, b"\x02\x00"), displayed())
        db.execute("UPDATE AppSourceState SET mode = 'VITAL_BRACELET'")
        self.assertEqual((2, b"\x01\x00"), displayed())
        self.assertEqual([(2,)], db.execute("SELECT spriteId FROM SpeciesSpriteChoice WHERE speciesKey = 'dukemon'").fetchall())
        db.execute("DELETE FROM SpeciesSpriteChoice")
        self.assertEqual((1, b"\x00\x00"), displayed())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        expected = database(45)
        self.addCleanup(expected.close)
        self.assertEqual(expected.execute("SELECT sql FROM sqlite_master WHERE type='view' AND name='DisplaySprite'").fetchone(),
                          db.execute("SELECT sql FROM sqlite_master WHERE type='view' AND name='DisplaySprite'").fetchone())

    def test_49_to_50_preserves_native_data_and_prevents_materialization_receipt_replay(self):
        db = self.fixture(49)
        seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "kept"})
        seed(db, "NativeGameSnapshot", {"id": 1, "bindingId": "binding", "individualId": "kept", "recordJson": "preserved native values"})
        protected = [entity["tableName"] for entity in schema(49)["entities"]]
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in protected}
        source = (DATABASE / "NativeMaterializationSchema.kt").read_text(encoding="utf-8")
        for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S):
            db.execute(triple or single)
        expected = database(50)
        self.addCleanup(expected.close)
        for table in ["NativeCanonicalMapping", "NativeMaterializationReceipt"]:
            for pragma in ["table_info", "foreign_key_list", "index_list"]:
                self.assertEqual(expected.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)
        seed(db, "NativeCanonicalMapping", {"snapshotId": 1, "nativeSpeciesId": 10, "canonicalSpeciesId": "digimon.agumon", "authority": "USER_CONFIRMED"})
        seed(db, "NativeMaterializationReceipt", {"individualId": "kept", "snapshotId": 1, "characterId": 1})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "NativeMaterializationReceipt", {"individualId": "kept", "snapshotId": 1, "characterId": 999})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "NativeCanonicalMapping", {"snapshotId": 999})
        db.execute("DELETE FROM UserCharacter WHERE id=1")
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM NativeMaterializationReceipt").fetchone()[0])
        self.assertEqual([("preserved native values",)], db.execute("SELECT recordJson FROM NativeGameSnapshot").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_48_to_49_adds_separate_review_audit_without_rewriting_native_or_app_records(self):
        db = self.fixture(48)
        seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "kept"})
        seed(db, "NativeGameSnapshot", {"id": 1, "bindingId": "binding", "individualId": "kept", "recordJson": "original native bytes"})
        protected = [entity["tableName"] for entity in schema(48)["entities"]]
        columns = {name: [row[1] for row in db.execute(f"PRAGMA table_info(`{name}`)")] for name in protected}
        before = {name: db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall() for name in protected}
        source = (DATABASE / "NativeRefreshSchema.kt").read_text(encoding="utf-8")
        for statement in re.findall(r'db.execSQL\("(ALTER TABLE[^"\n]+)"\)', source):
            db.execute(statement)
        expected = database(49)
        self.addCleanup(expected.close)
        self.assertEqual(expected.execute("PRAGMA table_info(NativeGameSnapshot)").fetchall(), db.execute("PRAGMA table_info(NativeGameSnapshot)").fetchall())
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall(), name)
        self.assertEqual([(None,)], db.execute("SELECT reviewAuditJson FROM NativeGameSnapshot").fetchall())
        seed(db, "NativeGameSnapshot", {"id": 2, "bindingId": "binding", "individualId": "kept", "recordJson": "new native bytes", "reviewAuditJson": "explicit app/source choices"})
        self.assertEqual([("original native bytes", None), ("new native bytes", "explicit app/source choices")], db.execute("SELECT recordJson, reviewAuditJson FROM NativeGameSnapshot ORDER BY id").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_47_to_48_preserves_data_and_scopes_native_bindings_to_verified_source_scheme(self):
        db = self.fixture(47)
        install_runtime_guards(db)
        protected = [entity["tableName"] for entity in schema(47)["entities"]]
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in protected}
        source = (DATABASE / "NativeGameArchiveSchema.kt").read_text(encoding="utf-8")
        for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S):
            db.execute(triple or single)
        expected = database(48)
        self.addCleanup(expected.close)
        for table in ["ExternalIndividualBinding", "NativeGameSnapshot"]:
            for pragma in ["table_info", "foreign_key_list", "index_list"]:
                self.assertEqual(expected.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)
        seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "sourceId": "source", "individualId": "kept", "nativeIdentity": "123"})
        seed(db, "NativeGameSnapshot", {"bindingId": "binding", "individualId": "kept", "recordJson": "kept native bytes"})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "ExternalIndividualBinding", {"bindingId": "duplicate", "sourceId": "source", "individualId": "kept", "nativeIdentity": "123"})
        seed(db, "ExternalIndividualBinding", {"bindingId": "other-source", "sourceId": "other", "individualId": "kept", "nativeIdentity": "123"})
        seed(db, "ExternalIndividualBinding", {"bindingId": "other-scheme", "sourceId": "source", "identityScheme": "v2", "individualId": "kept", "nativeIdentity": "123"})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "NativeGameSnapshot", {"bindingId": "missing", "individualId": "kept"})
        with self.assertRaises(sqlite3.IntegrityError):
            db.execute("DELETE FROM DigimonIndividual WHERE individualId='kept'")
        self.assertEqual([("kept native bytes",)], db.execute("SELECT recordJson FROM NativeGameSnapshot").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_46_to_47_keeps_previews_independent_and_receipts_item_scoped(self):
        db = self.fixture(46)
        install_runtime_guards(db)
        protected = [entity["tableName"] for entity in schema(46)["entities"]]
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in protected}
        source = (DATABASE / "SaveImportDraftSchema.kt").read_text(encoding="utf-8")
        for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S):
            db.execute(triple or single)
        expected = database(47)
        self.addCleanup(expected.close)
        for table in ["SaveImportDraft", "SaveImportDraftItem", "GameImportItemReceipt"]:
            for pragma in ["table_info", "foreign_key_list", "index_list"]:
                self.assertEqual(expected.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)
        seed(db, "LinkedGameSave", {"sourceId": "source", "documentKey": "provider:slot"})
        seed(db, "SaveImportDraft", {"draftId": "draft", "sourceId": "source"})
        seed(db, "SaveImportDraftItem", {"draftId": "draft", "recordKey": "one", "itemJson": "kept native data"})
        seed(db, "GameImportItemReceipt", {"receiptId": "receipt", "sourceId": "source", "recordKey": "one", "individualId": "kept"})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "GameImportItemReceipt", {"receiptId": "duplicate", "sourceId": "source", "recordKey": "one", "individualId": "kept"})
        seed(db, "GameImportItemReceipt", {"receiptId": "additional", "sourceId": "source", "recordKey": "two", "individualId": "kept"})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "GameImportItemReceipt", {"receiptId": "orphan", "sourceId": "source", "recordKey": "three", "individualId": "missing"})
        db.execute("DELETE FROM LinkedGameSave WHERE sourceId='source'")
        self.assertEqual([("kept native data",)], db.execute("SELECT itemJson FROM SaveImportDraftItem").fetchall())
        self.assertEqual(2, db.execute("SELECT COUNT(*) FROM GameImportItemReceipt").fetchone()[0])
        db.execute("DELETE FROM SaveImportDraft WHERE draftId='draft'")
        self.assertEqual([], db.execute("SELECT * FROM SaveImportDraftItem").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_45_to_46_preserves_collection_and_adds_isolated_linked_sources(self):
        db = self.fixture(45)
        install_runtime_guards(db)
        protected = [entity["tableName"] for entity in schema(45)["entities"]]
        before = {name: db.execute(f"SELECT * FROM `{name}`").fetchall() for name in protected}
        view = db.execute("SELECT sql FROM sqlite_master WHERE name='DisplaySprite'").fetchone()
        source = (DATABASE / "LinkedGameSaveSchema.kt").read_text(encoding="utf-8")
        statements = [triple or single for triple, single in re.findall(
            r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S)]
        for _ in range(2):
            for statement in statements:
                db.execute(statement)
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)
        self.assertEqual(view, db.execute("SELECT sql FROM sqlite_master WHERE name='DisplaySprite'").fetchone())
        expected = database(46)
        self.addCleanup(expected.close)
        for table in ["LinkedGameSave", "LinkedSaveObservation"]:
            for pragma in ["table_info", "foreign_key_list", "index_list"]:
                self.assertEqual(expected.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(),
                                 db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        seed(db, "LinkedGameSave", {"sourceId": "source", "documentKey": "provider:slot"})
        seed(db, "LinkedSaveObservation", {"sourceId": "source"})
        with self.assertRaises(sqlite3.IntegrityError):
            seed(db, "LinkedGameSave", {"sourceId": "duplicate", "documentKey": "provider:slot"})
        db.execute("DELETE FROM LinkedGameSave WHERE sourceId='source'")
        self.assertEqual([], db.execute("SELECT * FROM LinkedSaveObservation").fetchall())
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT * FROM `{name}`").fetchall(), name)
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())


if __name__ == "__main__":
    unittest.main()
