package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

object TransferContinuitySchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN generation INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN totalTrophies INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN rank INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN remainingTrainingTimeInMinutes INTEGER NOT NULL DEFAULT 6000")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN itemType INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN itemMultiplier INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN itemRemainingTime INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN mentalEffectValue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN mentalEffectMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN activityEffectValue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN activityEffectMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN vitalEffectValue INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN vitalEffectMinutes INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN abilityType INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN abilityRarity INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN abilityBranch INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN abilityReset INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE AppPartnerProgress ADD COLUMN originBasisPoints INTEGER NOT NULL DEFAULT 10000")
        db.execSQL("INSERT OR IGNORE INTO AppPartnerProgress(individualId,trainingHp,trainingAp,trainingBp) SELECT individualId,0,0,0 FROM UserCharacter WHERE characterType='APP'")
        db.execSQL("UPDATE AppPartnerProgress SET totalTrophies=MAX(totalTrophies,COALESCE((SELECT MAX(trophies) FROM UserCharacter u WHERE u.individualId=AppPartnerProgress.individualId),0))")
        db.execSQL("""INSERT INTO SpecialMissions(characterId,goal,watchId,progress,status,timeElapsedInMinutes,timeLimitInMinutes,missionType)
            SELECT u.id,0,s.slot,0,'UNAVAILABLE',0,0,'NONE' FROM UserCharacter u
            JOIN (SELECT 0 AS slot UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) s
            WHERE u.characterType='APP' AND NOT EXISTS(SELECT 1 FROM SpecialMissions m WHERE m.characterId=u.id AND m.watchId=s.slot)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS CompanionConnection (connectionId TEXT NOT NULL PRIMARY KEY,
            serverId TEXT NOT NULL, label TEXT NOT NULL, host TEXT NOT NULL, port INTEGER NOT NULL,
            certificatePin TEXT NOT NULL, encryptedToken TEXT NOT NULL, pairedAt INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_CompanionConnection_serverId ON CompanionConnection(serverId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS LinkedSaveLocation (locationId TEXT NOT NULL PRIMARY KEY,
            kind TEXT NOT NULL, locator TEXT NOT NULL, label TEXT NOT NULL, linkedAt INTEGER NOT NULL,
            lastScanAt INTEGER, lastError TEXT, truncated INTEGER NOT NULL)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_LinkedSaveLocation_kind_locator ON LinkedSaveLocation(kind, locator)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS SaveLocationExclusion (locationId TEXT NOT NULL, documentKey TEXT NOT NULL,
            PRIMARY KEY(locationId, documentKey), FOREIGN KEY(locationId) REFERENCES LinkedSaveLocation(locationId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS IndividualLifeEvent (eventId TEXT NOT NULL PRIMARY KEY, individualId TEXT NOT NULL,
            kind TEXT NOT NULL, occurredAt INTEGER NOT NULL, nativeSnapshotId INTEGER, representationId INTEGER,
            definitionId INTEGER, speciesId TEXT,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_IndividualLifeEvent_individualId_occurredAt ON IndividualLifeEvent(individualId, occurredAt)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS AppProgressDelta (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            individualId TEXT NOT NULL, kind TEXT NOT NULL, hp INTEGER NOT NULL, ap INTEGER NOT NULL, bp INTEGER NOT NULL,
            vitals INTEGER NOT NULL, wins INTEGER NOT NULL, losses INTEGER NOT NULL, recordedAt INTEGER NOT NULL, version INTEGER NOT NULL,
            trophies INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_AppProgressDelta_individualId_id ON AppProgressDelta(individualId, id)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeAppProjection (individualId TEXT NOT NULL PRIMARY KEY,
            nativeSnapshotId INTEGER NOT NULL, projectionVersion INTEGER NOT NULL, baselineKind TEXT NOT NULL,
            stage INTEGER NOT NULL, baseHp INTEGER NOT NULL, baseBp INTEGER NOT NULL, baseAp INTEGER NOT NULL,
            trainingHp INTEGER NOT NULL, trainingAp INTEGER NOT NULL, trainingBp INTEGER NOT NULL,
            vitalPoints INTEGER NOT NULL, wins INTEGER NOT NULL, losses INTEGER NOT NULL, baselineDeltaId INTEGER NOT NULL, recordedAt INTEGER NOT NULL,
            originBasisPoints INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        backfill(db)
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeSaveContainer (revision TEXT NOT NULL PRIMARY KEY,
            byteCount INTEGER NOT NULL, archivedAt INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeSaveContainerChunk (revision TEXT NOT NULL, chunkIndex INTEGER NOT NULL, bytes BLOB NOT NULL,
            PRIMARY KEY(revision,chunkIndex), FOREIGN KEY(revision) REFERENCES NativeSaveContainer(revision) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeSnapshotContainer (snapshotId INTEGER NOT NULL PRIMARY KEY, revision TEXT NOT NULL,
            FOREIGN KEY(snapshotId) REFERENCES NativeGameSnapshot(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(revision) REFERENCES NativeSaveContainer(revision) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_NativeSnapshotContainer_revision ON NativeSnapshotContainer(revision)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS UniversalJourneyAccount (individualId TEXT NOT NULL PRIMARY KEY,
            originSnapshotId INTEGER, revision INTEGER NOT NULL, stateJson TEXT NOT NULL, lastAppDeltaId INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS UniversalProgressReceipt (eventId TEXT NOT NULL PRIMARY KEY, individualId TEXT NOT NULL,
            viewKey TEXT NOT NULL, policyId TEXT NOT NULL, deltaJson TEXT NOT NULL, occurredAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_UniversalProgressReceipt_individualId ON UniversalProgressReceipt(individualId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS PendingNativeProgress (snapshotId INTEGER NOT NULL PRIMARY KEY, individualId TEXT NOT NULL,
            previousSnapshotId INTEGER NOT NULL, reason TEXT NOT NULL, recordedAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE,
            FOREIGN KEY(snapshotId) REFERENCES NativeGameSnapshot(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_PendingNativeProgress_individualId ON PendingNativeProgress(individualId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS ProgressProjectionGuard (individualId TEXT NOT NULL PRIMARY KEY, operationId TEXT NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        install(db)
    }

    private fun backfill(db: SupportSQLiteDatabase) {
        db.execSQL("""INSERT OR IGNORE INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,nativeSnapshotId)
            SELECT 'native:' || id, individualId, CASE WHEN reviewAuditJson IS NULL THEN 'NATIVE_IMPORTED' ELSE 'NATIVE_REFRESHED' END,
            importedAt, id FROM NativeGameSnapshot""")
        db.execSQL("""INSERT OR IGNORE INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,representationId,definitionId,speciesId)
            SELECT 'materialized:' || individualId, individualId, 'APP_MATERIALIZED', createdAt, characterId, definitionId,
            canonicalSpeciesId FROM NativeMaterializationReceipt""")
        db.execSQL("""INSERT OR IGNORE INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,representationId,definitionId,speciesId)
            SELECT 'history:' || h.id, u.individualId, CASE WHEN u.characterType='APP' THEN 'APP_FORM_RECORDED' ELSE 'DEVICE_FORM_OBSERVED' END,
            h.transformationDate,u.id,h.stageId,c.speciesId FROM TransformationHistory h JOIN UserCharacter u ON u.id=h.monId
            JOIN CardCharacter c ON c.id=h.stageId JOIN DigimonIndividual i ON i.individualId=u.individualId""")
        db.execSQL("""INSERT OR IGNORE INTO NativeAppProjection(individualId,nativeSnapshotId,projectionVersion,baselineKind,stage,baseHp,baseBp,baseAp,
            trainingHp,trainingAp,trainingBp,vitalPoints,wins,losses,baselineDeltaId,recordedAt,originBasisPoints)
            SELECT r.individualId,r.snapshotId,r.projectionVersion,'ADOPTED_EXISTING_STATE',c.stage,c.baseHp,c.baseBp,c.baseAp,
            COALESCE(p.trainingHp,0),COALESCE(p.trainingAp,0),COALESCE(p.trainingBp,0),u.vitalPoints,u.totalBattlesWon,u.totalBattlesLost,
            0,CAST(strftime('%s','now') AS INTEGER)*1000,COALESCE(p.originBasisPoints,10000) FROM NativeMaterializationReceipt r
            JOIN UserCharacter u ON u.individualId=r.individualId JOIN CardCharacter c ON c.id=u.charId
            LEFT JOIN AppPartnerProgress p ON p.individualId=r.individualId WHERE u.characterType='APP'""")
    }

    fun install(db: SupportSQLiteDatabase) {
        // Training survives representation deletion, so re-materializing an individual reuses it.
        // Avoid INSERT OR IGNORE here: the outer statement's OR ABORT overrides it in triggers.
        db.execSQL("DROP TRIGGER IF EXISTS app_state_initial")
        db.execSQL("""CREATE TRIGGER app_state_initial AFTER INSERT ON UserCharacter WHEN NEW.characterType='APP' BEGIN
            INSERT INTO AppPartnerProgress(individualId,trainingHp,trainingAp,trainingBp)
            SELECT NEW.individualId,0,0,0 WHERE NOT EXISTS(SELECT 1 FROM AppPartnerProgress WHERE individualId=NEW.individualId);
            INSERT INTO SpecialMissions(characterId,goal,watchId,progress,status,timeElapsedInMinutes,timeLimitInMinutes,missionType)
            SELECT NEW.id,0,s.slot,0,'UNAVAILABLE',0,0,'NONE' FROM (SELECT 0 AS slot UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) s
            WHERE NOT EXISTS(SELECT 1 FROM SpecialMissions m WHERE m.characterId=NEW.id AND m.watchId=s.slot); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS app_trophy_total AFTER UPDATE OF trophies ON UserCharacter
            WHEN NEW.characterType='APP' AND NEW.trophies>OLD.trophies AND NOT EXISTS(SELECT 1 FROM ProgressProjectionGuard WHERE individualId=NEW.individualId) BEGIN
            UPDATE AppPartnerProgress SET totalTrophies=totalTrophies+NEW.trophies-OLD.trophies WHERE individualId=NEW.individualId; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS continuity_app_trophies AFTER UPDATE OF totalTrophies ON AppPartnerProgress
            WHEN NEW.totalTrophies>OLD.totalTrophies AND NOT EXISTS(SELECT 1 FROM ProgressProjectionGuard WHERE individualId=NEW.individualId) BEGIN
            INSERT INTO AppProgressDelta(individualId,kind,hp,ap,bp,vitals,wins,losses,recordedAt,version,trophies)
            VALUES(NEW.individualId,'APP_TROPHY_GAIN',0,0,0,0,0,0,CAST(strftime('%s','now') AS INTEGER)*1000,1,NEW.totalTrophies-OLD.totalTrophies); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS app_mission_progress AFTER UPDATE ON UserCharacter
            WHEN NEW.characterType='APP' AND NOT EXISTS(SELECT 1 FROM ProgressProjectionGuard WHERE individualId=NEW.individualId) BEGIN
            UPDATE SpecialMissions SET
            status=CASE WHEN progress+CASE missionType WHEN 'BATTLES' THEN MAX(0,NEW.totalBattlesWon+NEW.totalBattlesLost-OLD.totalBattlesWon-OLD.totalBattlesLost)
                WHEN 'WINS' THEN MAX(0,NEW.totalBattlesWon-OLD.totalBattlesWon) WHEN 'VITALS' THEN MAX(0,NEW.vitalPoints-OLD.vitalPoints) ELSE 0 END>=goal THEN 'COMPLETED'
                ELSE 'IN_PROGRESS' END,
            progress=MIN(goal,progress+CASE missionType WHEN 'BATTLES' THEN MAX(0,NEW.totalBattlesWon+NEW.totalBattlesLost-OLD.totalBattlesWon-OLD.totalBattlesLost)
                WHEN 'WINS' THEN MAX(0,NEW.totalBattlesWon-OLD.totalBattlesWon) WHEN 'VITALS' THEN MAX(0,NEW.vitalPoints-OLD.vitalPoints) ELSE 0 END)
            WHERE characterId=NEW.id AND goal>0 AND status IN ('AVAILABLE','IN_PROGRESS') AND missionType IN ('BATTLES','WINS','VITALS')
            AND CASE missionType WHEN 'BATTLES' THEN MAX(0,NEW.totalBattlesWon+NEW.totalBattlesLost-OLD.totalBattlesWon-OLD.totalBattlesLost)
                WHEN 'WINS' THEN MAX(0,NEW.totalBattlesWon-OLD.totalBattlesWon) WHEN 'VITALS' THEN MAX(0,NEW.vitalPoints-OLD.vitalPoints) ELSE 0 END>0; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS continuity_native_snapshot AFTER INSERT ON NativeGameSnapshot BEGIN
            INSERT OR IGNORE INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,nativeSnapshotId)
            VALUES('native:' || NEW.id,NEW.individualId,CASE WHEN NEW.reviewAuditJson IS NULL THEN 'NATIVE_IMPORTED' ELSE 'NATIVE_REFRESHED' END,NEW.importedAt,NEW.id); END""")
        // The inner conflict clause is overridden by the outer INSERT's clause (Room uses
        // INSERT OR ABORT), so avoid a conflict entirely instead of relying on OR IGNORE.
        db.execSQL("DROP TRIGGER IF EXISTS continuity_materialized")
        db.execSQL("""CREATE TRIGGER continuity_materialized AFTER INSERT ON NativeMaterializationReceipt BEGIN
            INSERT INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,representationId,definitionId,speciesId)
            SELECT 'materialized:' || NEW.individualId,NEW.individualId,'APP_MATERIALIZED',NEW.createdAt,NEW.characterId,NEW.definitionId,NEW.canonicalSpeciesId
            WHERE NOT EXISTS(SELECT 1 FROM IndividualLifeEvent WHERE eventId='materialized:' || NEW.individualId); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS continuity_form AFTER INSERT ON TransformationHistory BEGIN
            INSERT OR IGNORE INTO IndividualLifeEvent(eventId,individualId,kind,occurredAt,representationId,definitionId,speciesId)
            SELECT 'history:' || NEW.id,u.individualId,CASE WHEN u.characterType='APP' THEN 'APP_FORM_RECORDED' ELSE 'DEVICE_FORM_OBSERVED' END,
            NEW.transformationDate,u.id,NEW.stageId,c.speciesId FROM UserCharacter u JOIN CardCharacter c ON c.id=NEW.stageId WHERE u.id=NEW.monId; END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS continuity_app_training AFTER UPDATE ON AppPartnerProgress
            WHEN (NEW.trainingHp!=OLD.trainingHp OR NEW.trainingAp!=OLD.trainingAp OR NEW.trainingBp!=OLD.trainingBp)
            AND NOT EXISTS(SELECT 1 FROM ProgressProjectionGuard WHERE individualId=NEW.individualId) BEGIN
            INSERT INTO AppProgressDelta(individualId,kind,hp,ap,bp,vitals,wins,losses,recordedAt,version)
            VALUES(NEW.individualId,'APP_TRAINING_CHANGE',NEW.trainingHp-OLD.trainingHp,NEW.trainingAp-OLD.trainingAp,NEW.trainingBp-OLD.trainingBp,
            0,0,0,CAST(strftime('%s','now') AS INTEGER)*1000,1); END""")
        db.execSQL("""CREATE TRIGGER IF NOT EXISTS continuity_app_state AFTER UPDATE ON UserCharacter
            WHEN NEW.characterType='APP' AND (NEW.vitalPoints!=OLD.vitalPoints OR NEW.totalBattlesWon!=OLD.totalBattlesWon OR NEW.totalBattlesLost!=OLD.totalBattlesLost)
            AND NOT EXISTS(SELECT 1 FROM ProgressProjectionGuard WHERE individualId=NEW.individualId) BEGIN
            INSERT INTO AppProgressDelta(individualId,kind,hp,ap,bp,vitals,wins,losses,recordedAt,version)
            VALUES(NEW.individualId,'APP_STATE_CHANGE',0,0,0,NEW.vitalPoints-OLD.vitalPoints,NEW.totalBattlesWon-OLD.totalBattlesWon,
            NEW.totalBattlesLost-OLD.totalBattlesLost,CAST(strftime('%s','now') AS INTEGER)*1000,1); END""")
    }
}
