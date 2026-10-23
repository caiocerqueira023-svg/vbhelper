"""SQLite upgrade acceptance: nullable native facts must not cascade native history or reset IDs."""
import re
import unittest
from tools import test_participation_schema as fixture
from tools.test_transfer_continuity import statements as continuity_statements


class WorldDsAcquisitionSchemaTest(unittest.TestCase):
    def test_native_progress_review_query_is_bounded_owner_scoped_and_read_only(self):
        db = fixture.database(53)
        self.addCleanup(db.close)
        for owner in ["kept", "other"]:
            fixture.seed(db, "DigimonIndividual", {"individualId": owner, "createdAt": 1, "blastMode": "NONE"})
            fixture.seed(db, "ExternalIndividualBinding", {"bindingId": owner, "individualId": owner, "sourceId": owner})
        for number in range(1, 42):
            owner = "kept" if number <= 40 else "other"
            fixture.seed(db, "NativeGameSnapshot", {"id": number, "bindingId": owner, "individualId": owner})
            fixture.seed(db, "PendingNativeProgress", {"snapshotId": number, "individualId": owner,
                "previousSnapshotId": 1, "reason": "GAME_PROGRESS_POLICY_REQUIRED", "recordedAt": number})
        before = db.execute("SELECT * FROM PendingNativeProgress ORDER BY snapshotId").fetchall()
        source = (fixture.ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/TransferContinuityDao.kt").read_text(encoding="utf-8")
        query = re.search(r'@Query\("([^"]+)"\)\s*suspend fun recentPendingNative', source)[1]
        rows = db.execute(query, {"id": "kept"}).fetchall()
        self.assertEqual(32, len(rows))
        self.assertEqual(list(range(40, 8, -1)), [row[0] for row in rows])
        self.assertEqual(before, db.execute("SELECT * FROM PendingNativeProgress ORDER BY snapshotId").fetchall())
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM UniversalProgressReceipt").fetchone()[0])

    def test_52_to_53_preserves_native_children_triggers_and_sequence(self):
        db = fixture.database(52)
        self.addCleanup(db.close)
        fixture.seed(db, "DigimonIndividual", {"individualId": "kept", "createdAt": 1, "blastMode": "NONE"})
        fixture.seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "kept"})
        fixture.seed(db, "LinkedGameSave", {"sourceId": "source", "documentKey": "doc"})
        # Install the production trigger rather than a fixture that mirrors the new migration.
        for sql in continuity_statements():
            if "CREATE TRIGGER IF NOT EXISTS continuity_native_snapshot " in sql:
                db.execute(sql)
        fixture.seed(db, "NativeGameSnapshot", {"id": 1, "bindingId": "binding", "individualId": "kept",
            "recordJson": "exact original native bytes", "nativePersonalityId": 16, "mappedPersonality": "COMPASSIONATE", "importedAt": 2})
        fixture.seed(db, "NativeCanonicalMapping", {"snapshotId": 1, "canonicalSpeciesId": "digimon.test"})
        fixture.seed(db, "NativeSaveContainer", {"revision": "revision", "byteCount": 18, "archivedAt": 2})
        fixture.seed(db, "NativeSaveContainerChunk", {"revision": "revision", "chunkIndex": 0, "bytes": b"original container"})
        fixture.seed(db, "NativeSnapshotContainer", {"snapshotId": 1, "revision": "revision"})
        db.execute("UPDATE sqlite_sequence SET seq=200 WHERE name='NativeGameSnapshot'")
        before = {e["tableName"]: db.execute(f'SELECT * FROM `{e["tableName"]}`').fetchall()
                  for e in fixture.schema(52)["entities"]}
        columns = {table: [field[1] for field in db.execute(f'PRAGMA table_info(`{table}`)')] for table in before}
        trigger_sql = [row[0] for row in db.execute("SELECT sql FROM sqlite_master WHERE type='trigger' AND tbl_name='NativeGameSnapshot'")]
        db.commit()
        db.execute("PRAGMA foreign_keys=OFF")
        db.execute("BEGIN")
        source = (fixture.DATABASE / "WorldDsAcquisitionSchema.kt").read_text(encoding="utf-8")
        for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S):
            db.execute(triple or single)
        for sql in trigger_sql:
            db.execute(sql)
        db.commit()
        db.execute("PRAGMA foreign_keys=ON")
        for table, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT {','.join(columns[table])} FROM `{table}`").fetchall(), table)
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())
        expected = fixture.database(53)
        self.addCleanup(expected.close)
        for table in ["LinkedGameSave", "NativeGameSnapshot"]:
            self.assertEqual(expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
                             db.execute(f"PRAGMA table_info(`{table}`)").fetchall())
        db.execute("INSERT INTO NativeGameSnapshot(individualId,bindingId,sourceLabel,contentRevision,codecVersion,recordKey,recordJson,nativePersonalityId,mappedPersonality,importedAt) VALUES ('kept','binding','DS','new',1,'copy:1:record:0','native null personality',NULL,'BRAVE',3)")
        self.assertEqual((201, None), db.execute("SELECT id,nativePersonalityId FROM NativeGameSnapshot ORDER BY id DESC LIMIT 1").fetchone())
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM IndividualLifeEvent WHERE nativeSnapshotId=201").fetchone()[0])
        db.execute("UPDATE LinkedGameSave SET reviewedCopyRevision='new',reviewedSaveCopy=2 WHERE sourceId='source'")
        self.assertEqual(('new', 2), db.execute("SELECT reviewedCopyRevision,reviewedSaveCopy FROM LinkedGameSave").fetchone())


if __name__ == "__main__":
    unittest.main()
