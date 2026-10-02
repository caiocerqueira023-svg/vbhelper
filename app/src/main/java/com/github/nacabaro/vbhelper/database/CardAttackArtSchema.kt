package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

internal object CardAttackArtSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS CardAttackArt (
                cardCharacterId INTEGER NOT NULL PRIMARY KEY,
                smallAttackId INTEGER NOT NULL,
                largeAttackId INTEGER NOT NULL,
                smallPixels BLOB, smallWidth INTEGER, smallHeight INTEGER,
                largePixels BLOB, largeWidth INTEGER, largeHeight INTEGER,
                FOREIGN KEY(cardCharacterId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
