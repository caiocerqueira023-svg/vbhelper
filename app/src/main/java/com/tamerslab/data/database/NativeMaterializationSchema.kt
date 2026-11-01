package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

object NativeMaterializationSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeCanonicalMapping (
            snapshotId INTEGER NOT NULL PRIMARY KEY, nativeSpeciesId INTEGER NOT NULL,
            canonicalSpeciesId TEXT NOT NULL, authority TEXT NOT NULL, reviewedAt INTEGER NOT NULL,
            FOREIGN KEY(snapshotId) REFERENCES NativeGameSnapshot(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS NativeMaterializationReceipt (
            individualId TEXT NOT NULL PRIMARY KEY, snapshotId INTEGER NOT NULL, canonicalSpeciesId TEXT NOT NULL,
            definitionId INTEGER NOT NULL, characterId INTEGER NOT NULL, projectionVersion INTEGER NOT NULL,
            createdAt INTEGER NOT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(snapshotId) REFERENCES NativeGameSnapshot(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_NativeMaterializationReceipt_snapshotId ON NativeMaterializationReceipt(snapshotId)")
    }
}
