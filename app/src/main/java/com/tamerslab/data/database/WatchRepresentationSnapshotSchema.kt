package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

object WatchRepresentationSnapshotSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS WatchRepresentationSnapshot (transferToken TEXT NOT NULL PRIMARY KEY,
            individualId TEXT NOT NULL, sourceCharacterId INTEGER NOT NULL, deviceType INTEGER NOT NULL,
            dimId INTEGER NOT NULL, capturedAt INTEGER NOT NULL, version INTEGER NOT NULL DEFAULT 1, stateJson TEXT NOT NULL,
            nativeJson TEXT DEFAULT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WatchRepresentationSnapshot_individualId_capturedAt ON WatchRepresentationSnapshot(individualId, capturedAt)")
    }
}
