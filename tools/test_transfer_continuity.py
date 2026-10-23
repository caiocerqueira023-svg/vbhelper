"""Real SQLite preservation/trigger checks for linked locations and lifetime continuity."""
import re
import sqlite3
import unittest
from tools import test_participation_schema as fixture


def statements():
    source = (fixture.DATABASE / "TransferContinuitySchema.kt").read_text(encoding="utf-8")
    return [triple or single for triple, single in re.findall(r'db\.execSQL\((?:"""(.*?)"""|"(.*?)")\)', source, re.S)]


class TransferContinuityTest(unittest.TestCase):
    def test_51_to_52_preserves_bindings_and_never_promotes_legacy_identity_confidence(self):
        db = fixture.database(51)
        self.addCleanup(db.close)
        fixture.seed(db, "DigimonIndividual", {"individualId": "kept", "createdAt": 1, "blastMode": "NONE"})
        fixture.seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "kept"})
        fixture.seed(db, "SaveImportDraft", {"draftId": "review"})
        protected = [entity["tableName"] for entity in fixture.schema(51)["entities"]]
        columns = {name: [row[1] for row in db.execute(f"PRAGMA table_info(`{name}`)")] for name in protected}
        before = {name: db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall() for name in protected}
        source = (fixture.DATABASE / "ProvisionalSnapshotSchema.kt").read_text(encoding="utf-8")
        for statement in re.findall(r'db.execSQL\("(ALTER TABLE[^"\n]+)"\)', source):
            db.execute(statement)
        expected = fixture.database(52)
        self.addCleanup(expected.close)
        for table in ["ExternalIndividualBinding", "SaveImportDraft"]:
            self.assertEqual(expected.execute(f"PRAGMA table_info(`{table}`)").fetchall(), db.execute(f"PRAGMA table_info(`{table}`)").fetchall())
        for name, rows in before.items():
            self.assertEqual(rows, db.execute(f"SELECT {','.join(columns[name])} FROM `{name}`").fetchall(), name)
        self.assertEqual([('PROVISIONAL',)], db.execute("SELECT identityConfidence FROM ExternalIndividualBinding").fetchall())
        self.assertEqual([(0,)], db.execute("SELECT identityReviewRequired FROM SaveImportDraft").fetchall())
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def prepare(self):
        db = fixture.database(50)
        self.addCleanup(db.close)
        fixture.seed(db, "Card", {"id": 1, "cardId": 1, "name": "Kept"})
        fixture.seed(db, "Sprite", {"id": 1, "width": 1, "height": 1})
        fixture.seed(db, "CardCharacter", {"id": 100, "cardId": 1, "spriteId": 1, "attribute": "Vaccine", "stage": 2})
        fixture.seed(db, "DigimonIndividual", {"individualId": "kept", "createdAt": 55, "blastMode": "NONE"})
        fixture.seed(db, "UserCharacter", {"id": 1, "individualId": "kept", "charId": 100, "characterType": "APP", "injuryStatus": "None", "vitalPoints": 1234})
        fixture.seed(db, "AppPartnerProgress", {"individualId": "kept", "trainingHp": 20, "trainingAp": 30, "trainingBp": 40})
        fixture.seed(db, "TransformationHistory", {"monId": 1, "stageId": 100, "transformationDate": 66})
        fixture.seed(db, "SpecialMissions", {"id": 11, "characterId": 1, "watchId": 0, "goal": 50,
            "progress": 7, "status": "IN_PROGRESS", "missionType": "VITALS", "timeElapsedInMinutes": 5, "timeLimitInMinutes": 120})
        fixture.seed(db, "ExternalIndividualBinding", {"bindingId": "binding", "individualId": "kept"})
        fixture.seed(db, "NativeGameSnapshot", {"id": 1, "bindingId": "binding", "individualId": "kept", "recordJson": "retained native data", "importedAt": 60})
        fixture.seed(db, "NativeMaterializationReceipt", {"individualId": "kept", "snapshotId": 1, "characterId": 1, "definitionId": 100, "createdAt": 61, "projectionVersion": 1})
        return db

    def migrate(self, db):
        for sql in statements():
            db.execute(sql)

    def test_50_to_51_matches_export_and_preserves_old_rows(self):
        db = self.prepare()
        protected = [entity["tableName"] for entity in fixture.schema(50)["entities"]]
        columns = {table: [row[1] for row in db.execute(f"PRAGMA table_info(`{table}`)")] for table in protected}
        before = {table: db.execute(f"SELECT {','.join(columns[table])} FROM `{table}`").fetchall() for table in protected}
        self.migrate(db)
        expected = fixture.database(51)
        self.addCleanup(expected.close)
        for entity in fixture.schema(51)["entities"]:
            table = entity["tableName"]
            if table in protected:
                after = db.execute(f"SELECT {','.join(columns[table])} FROM `{table}`").fetchall()
                if table == "SpecialMissions":
                    self.assertTrue(all(row in after for row in before[table]), table)
                    self.assertEqual(4, len(after))
                else:
                    self.assertEqual(before[table], after, table)
                if table == "AppPartnerProgress":
                    self.assertEqual(expected.execute("PRAGMA table_info(AppPartnerProgress)").fetchall(), db.execute("PRAGMA table_info(AppPartnerProgress)").fetchall())
            else:
                for pragma in ["table_info", "foreign_key_list", "index_list"]:
                    self.assertEqual(expected.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), db.execute(f"PRAGMA {pragma}(`{table}`)").fetchall(), (table, pragma))
        self.assertEqual([], db.execute("PRAGMA foreign_key_check").fetchall())

    def test_app_deltas_are_recorded_once_and_keep_the_frozen_baseline(self):
        db = self.prepare()
        self.migrate(db)
        db.execute("UPDATE AppPartnerProgress SET trainingHp=30 WHERE individualId='kept'")
        db.execute("UPDATE AppPartnerProgress SET trainingHp=30 WHERE individualId='kept'")
        db.execute("UPDATE UserCharacter SET vitalPoints=vitalPoints+5,totalBattlesWon=totalBattlesWon+1 WHERE id=1")
        self.assertEqual([("APP_TRAINING_CHANGE", 10, 0, 0), ("APP_STATE_CHANGE", 0, 5, 1)],
                         db.execute("SELECT kind,hp,vitals,wins FROM AppProgressDelta ORDER BY id").fetchall())
        self.assertEqual((20, 30, 40, 1234, "ADOPTED_EXISTING_STATE"), db.execute(
            "SELECT trainingHp,trainingAp,trainingBp,vitalPoints,baselineKind FROM NativeAppProjection").fetchone())
        db.execute("UPDATE UserCharacter SET characterType='VBDevice',vitalPoints=9999 WHERE id=1")
        self.assertEqual(2, db.execute("SELECT COUNT(*) FROM AppProgressDelta").fetchone()[0])

    def test_trophy_gains_survive_phase_counter_resets_without_being_awarded_twice(self):
        db = self.prepare()
        self.migrate(db)
        db.execute("UPDATE UserCharacter SET trophies=5 WHERE id=1")
        db.execute("UPDATE UserCharacter SET trophies=0 WHERE id=1")
        self.assertEqual([(5,)], db.execute("SELECT trophies FROM AppProgressDelta WHERE kind='APP_TROPHY_GAIN'").fetchall())
        self.assertEqual(5, db.execute("SELECT totalTrophies FROM AppPartnerProgress").fetchone()[0])

    def test_lifetime_history_and_native_data_survive_representation_deletion(self):
        db = self.prepare()
        self.migrate(db)
        before = db.execute("SELECT * FROM IndividualLifeEvent ORDER BY eventId").fetchall()
        self.assertEqual(3, len(before))
        db.execute("DELETE FROM UserCharacter WHERE id=1")
        self.assertEqual(before, db.execute("SELECT * FROM IndividualLifeEvent ORDER BY eventId").fetchall())
        self.assertEqual([("retained native data",)], db.execute("SELECT recordJson FROM NativeGameSnapshot").fetchall())

    def test_new_forms_native_refresh_and_location_exclusions_have_correct_lifetimes(self):
        db = self.prepare()
        self.migrate(db)
        fixture.seed(db, "NativeGameSnapshot", {"id": 2, "bindingId": "binding", "individualId": "kept", "reviewAuditJson": "review", "importedAt": 70})
        self.assertEqual([("NATIVE_REFRESHED",)], db.execute("SELECT kind FROM IndividualLifeEvent WHERE nativeSnapshotId=2").fetchall())
        fixture.seed(db, "LinkedSaveLocation", {"locationId": "folder", "kind": "TREE", "locator": "content://provider/tree"})
        fixture.seed(db, "SaveLocationExclusion", {"locationId": "folder", "documentKey": "provider:slot"})
        db.execute("DELETE FROM LinkedSaveLocation WHERE locationId='folder'")
        self.assertEqual([], db.execute("SELECT * FROM SaveLocationExclusion").fetchall())
        self.assertEqual(1, db.execute("SELECT COUNT(*) FROM DigimonIndividual").fetchone()[0])

    def test_projection_and_dim_assignment_do_not_award_progress(self):
        db = self.prepare()
        self.migrate(db)
        db.execute("INSERT INTO ProgressProjectionGuard VALUES ('kept','conversion')")
        db.execute("UPDATE UserCharacter SET vitalPoints=9999,totalBattlesWon=99,trophies=20 WHERE id=1")
        db.execute("UPDATE AppPartnerProgress SET trainingHp=999,originBasisPoints=11000 WHERE individualId='kept'")
        db.execute("DELETE FROM ProgressProjectionGuard WHERE individualId='kept'")
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM AppProgressDelta").fetchone()[0])
        self.assertEqual(0, db.execute("SELECT totalTrophies FROM AppPartnerProgress").fetchone()[0])
        db.execute("UPDATE UserCharacter SET exportCardCharacterId=100 WHERE id=1")
        self.assertEqual(0, db.execute("SELECT COUNT(*) FROM AppProgressDelta").fetchone()[0])

    def test_app_partners_have_mission_slots_and_real_app_battle_progress(self):
        db = self.prepare()
        self.migrate(db)
        self.assertEqual(4, db.execute("SELECT COUNT(*) FROM SpecialMissions WHERE characterId=1").fetchone()[0])
        db.execute("UPDATE SpecialMissions SET missionType='WINS',goal=2,status='AVAILABLE' WHERE characterId=1 AND watchId=3")
        db.execute("UPDATE UserCharacter SET exportCardCharacterId=100 WHERE id=1")
        self.assertEqual(('AVAILABLE', 0), db.execute("SELECT status,progress FROM SpecialMissions WHERE characterId=1 AND watchId=3").fetchone())
        db.execute("UPDATE UserCharacter SET totalBattlesWon=totalBattlesWon+2 WHERE id=1")
        self.assertEqual(('COMPLETED', 2), db.execute("SELECT status,progress FROM SpecialMissions WHERE characterId=1 AND watchId=3").fetchone())

    def test_dim_stat_override_uses_exact_selected_card_without_rewriting_origin(self):
        db = self.prepare()
        self.migrate(db)
        db.execute("UPDATE CardCharacter SET cardId=NULL,speciesId='digimon.agumon',catalogSpeciesId='digimon.agumon',baseHp=3,baseBp=10,baseAp=2 WHERE id=100")
        fixture.seed(db, "Card", {"id": 2, "cardId": 2, "name": "Assigned DIM", "isBEm": 0})
        fixture.seed(db, "CardCharacter", {"id": 200, "cardId": 2, "spriteId": 1, "attribute": "Virus", "speciesId": "digimon.agumon", "baseHp": 9, "baseBp": 88, "baseAp": 7})
        db.execute("UPDATE AppPartnerProgress SET originBasisPoints=11000 WHERE individualId='kept'")
        source = (fixture.ROOT / "app/src/main/java/com/github/nacabaro/vbhelper/daos/UserCharacterDao.kt").read_text(encoding="utf-8")
        queries = {name: sql for sql, name in re.findall(r'@Query\(\s*"""(.*?)"""\s*\)\s*(?:suspend\s+)?fun\s+(\w+)', source, re.S)}
        db.row_factory = sqlite3.Row
        query = queries['getAllBattleParticipantProfiles']
        original = dict(db.execute(query).fetchone())
        self.assertEqual((3, 10, 2, 11000, 0), tuple(original[key] for key in ['baseHp','baseBp','baseAp','originBasisPoints','usesAssignedDim']))
        self.assertTrue(original['externalCharacterId'].startswith('app_mon'))
        db.execute("UPDATE UserCharacter SET exportCardCharacterId=200 WHERE id=1")
        assigned = dict(db.execute(query).fetchone())
        self.assertEqual((9, 88, 7, 10000, 1), tuple(assigned[key] for key in ['baseHp','baseBp','baseAp','originBasisPoints','usesAssignedDim']))
        self.assertEqual(11000, db.execute("SELECT originBasisPoints FROM AppPartnerProgress").fetchone()[0])
        self.assertEqual('retained native data', db.execute("SELECT recordJson FROM NativeGameSnapshot").fetchone()[0])


if __name__ == "__main__":
    unittest.main()
