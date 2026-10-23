package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive schema 45 -> 46. No collection/identity/artwork tables are changed. */
object LinkedGameSaveSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS LinkedGameSave (
            sourceId TEXT NOT NULL PRIMARY KEY, documentKey TEXT NOT NULL,
            documentUri TEXT NOT NULL, grantUri TEXT NOT NULL, displayName TEXT NOT NULL,
            label TEXT NOT NULL, linkedAt INTEGER NOT NULL, lastAttemptAt INTEGER,
            lastSuccessAt INTEGER, lastError TEXT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_LinkedGameSave_documentKey ON LinkedGameSave(documentKey)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS LinkedSaveObservation (
            sourceId TEXT NOT NULL PRIMARY KEY, contentRevision TEXT NOT NULL,
            observedAt INTEGER NOT NULL, gameId TEXT NOT NULL, formatProfile TEXT NOT NULL,
            codecVersion INTEGER NOT NULL, snapshotJson TEXT NOT NULL,
            FOREIGN KEY(sourceId) REFERENCES LinkedGameSave(sourceId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
    }
}
