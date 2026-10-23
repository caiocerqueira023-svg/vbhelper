package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Rebuild only native snapshot nullability, retaining child references, indices and lifetime triggers. */
object WorldDsAcquisitionSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        // PreservingMigrationFactory disables foreign keys before Room's upgrade transaction.
        check(db.query("PRAGMA foreign_keys").use { it.moveToFirst() && it.getInt(0) == 0 })
        val triggers = db.query("SELECT sql FROM sqlite_master WHERE type='trigger' AND tbl_name='NativeGameSnapshot' AND sql IS NOT NULL")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        db.execSQL("ALTER TABLE LinkedGameSave ADD COLUMN reviewedCopyRevision TEXT")
        db.execSQL("ALTER TABLE LinkedGameSave ADD COLUMN reviewedSaveCopy INTEGER")
        db.execSQL("""CREATE TABLE NativeGameSnapshot_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, individualId TEXT NOT NULL, bindingId TEXT NOT NULL,
            sourceLabel TEXT NOT NULL, contentRevision TEXT NOT NULL, codecVersion INTEGER NOT NULL,
            recordKey TEXT NOT NULL, recordJson TEXT NOT NULL, nativePersonalityId INTEGER,
            mappedPersonality TEXT NOT NULL, importedAt INTEGER NOT NULL, reviewAuditJson TEXT DEFAULT NULL,
            FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(bindingId) REFERENCES ExternalIndividualBinding(bindingId) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("INSERT INTO NativeGameSnapshot_new SELECT * FROM NativeGameSnapshot")
        db.execSQL("INSERT INTO sqlite_sequence(name,seq) SELECT 'NativeGameSnapshot_new',0 WHERE NOT EXISTS (SELECT 1 FROM sqlite_sequence WHERE name='NativeGameSnapshot_new')")
        db.execSQL("UPDATE sqlite_sequence SET seq=MAX(seq,COALESCE((SELECT MAX(seq) FROM sqlite_sequence WHERE name='NativeGameSnapshot'),0)) WHERE name='NativeGameSnapshot_new'")
        db.execSQL("DROP TABLE NativeGameSnapshot")
        db.execSQL("ALTER TABLE NativeGameSnapshot_new RENAME TO NativeGameSnapshot")
        db.execSQL("CREATE INDEX index_NativeGameSnapshot_bindingId ON NativeGameSnapshot(bindingId)")
        db.execSQL("CREATE INDEX index_NativeGameSnapshot_individualId_id ON NativeGameSnapshot(individualId,id)")
        triggers.forEach { db.execSQL(it) }
        check(db.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }
}
