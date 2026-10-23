package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object NativeGameArchiveSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS ExternalIndividualBinding (
            bindingId TEXT NOT NULL PRIMARY KEY, sourceId TEXT NOT NULL, documentKey TEXT NOT NULL, gameId TEXT NOT NULL,
            formatProfile TEXT NOT NULL, identityScheme TEXT NOT NULL, nativeIdentity TEXT NOT NULL,
            individualId TEXT NOT NULL, boundAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ExternalIndividualBinding_sourceId_gameId_formatProfile_identityScheme_nativeIdentity ON ExternalIndividualBinding(sourceId, gameId, formatProfile, identityScheme, nativeIdentity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ExternalIndividualBinding_documentKey_gameId_formatProfile_identityScheme_nativeIdentity ON ExternalIndividualBinding(documentKey, gameId, formatProfile, identityScheme, nativeIdentity)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ExternalIndividualBinding_individualId ON ExternalIndividualBinding(individualId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeGameSnapshot (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, individualId TEXT NOT NULL, bindingId TEXT NOT NULL,
            sourceLabel TEXT NOT NULL, contentRevision TEXT NOT NULL, codecVersion INTEGER NOT NULL,
            recordKey TEXT NOT NULL, recordJson TEXT NOT NULL, nativePersonalityId INTEGER NOT NULL,
            mappedPersonality TEXT NOT NULL, importedAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(bindingId) REFERENCES ExternalIndividualBinding(bindingId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_NativeGameSnapshot_bindingId ON NativeGameSnapshot(bindingId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_NativeGameSnapshot_individualId_id ON NativeGameSnapshot(individualId, id)")
    }
}
