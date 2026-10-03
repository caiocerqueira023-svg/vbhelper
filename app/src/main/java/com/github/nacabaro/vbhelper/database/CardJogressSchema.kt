package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object CardJogressSchema {
    fun create(db: SupportSQLiteDatabase) {
        var hasNameFlag = false
        db.query("PRAGMA table_info(Card)").use { cursor ->
            while (cursor.moveToNext()) if (cursor.getString(1) == "nameIsUserEdited") hasNameFlag = true
        }
        if (!hasNameFlag) db.execSQL("ALTER TABLE Card ADD COLUMN nameIsUserEdited INTEGER NOT NULL DEFAULT 0")
        // DiM imports used zero exclusively for "not locked"; preserve BEM zero, which
        // can mean adventure stage 1. Re-import resolves any legacy BEM ambiguity.
        db.execSQL("""
            UPDATE PossibleTransformations SET requiredAdventureLevelCompleted = -1
            WHERE requiredAdventureLevelCompleted = 0 AND charaId IN (
                SELECT cc.id FROM CardCharacter cc JOIN Card c ON c.id = cc.cardId WHERE c.isBEm = 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS CardSpecificJogress (
                fromCharaId INTEGER NOT NULL, toCharaId INTEGER NOT NULL,
                partnerCardNumber INTEGER NOT NULL, partnerCharaIndex INTEGER NOT NULL,
                PRIMARY KEY(fromCharaId,toCharaId,partnerCardNumber,partnerCharaIndex),
                FOREIGN KEY(fromCharaId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(toCharaId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_CardSpecificJogress_fromCharaId ON CardSpecificJogress(fromCharaId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_CardSpecificJogress_toCharaId ON CardSpecificJogress(toCharaId)")
    }
}
