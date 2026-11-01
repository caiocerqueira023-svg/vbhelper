package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive 46 -> 47. Preview persistence is independent of the owned collection and linked-source lifetime. */
object SaveImportDraftSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS SaveImportDraft (
            draftId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, sourceLabel TEXT NOT NULL,
            documentKey TEXT NOT NULL, gameId TEXT NOT NULL, formatProfile TEXT NOT NULL,
            codecVersion INTEGER NOT NULL, contentRevision TEXT NOT NULL, plannerVersion INTEGER NOT NULL,
            fingerprint TEXT NOT NULL, mappingPending INTEGER NOT NULL, createdAt INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_SaveImportDraft_sourceId_createdAt ON SaveImportDraft(sourceId, createdAt)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS SaveImportDraftItem (
            draftId TEXT NOT NULL, recordKey TEXT NOT NULL, itemJson TEXT NOT NULL,
            PRIMARY KEY(draftId, recordKey),
            FOREIGN KEY(draftId) REFERENCES SaveImportDraft(draftId) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS GameImportItemReceipt (
            receiptId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, contentRevision TEXT NOT NULL,
            formatProfile TEXT NOT NULL, codecVersion INTEGER NOT NULL, recordKey TEXT NOT NULL,
            individualId TEXT NOT NULL, importedAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_GameImportItemReceipt_sourceId_contentRevision_formatProfile_codecVersion_recordKey ON GameImportItemReceipt(sourceId, contentRevision, formatProfile, codecVersion, recordKey)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_GameImportItemReceipt_individualId ON GameImportItemReceipt(individualId)")
    }
}
