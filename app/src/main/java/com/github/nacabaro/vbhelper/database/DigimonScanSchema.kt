package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object DigimonScanSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS DigimonScanProgress (
                cardCharacterId INTEGER NOT NULL PRIMARY KEY,
                percentage INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                FOREIGN KEY(cardCharacterId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS DigimonScanReward (
                interactionId TEXT NOT NULL,
                cardCharacterId INTEGER NOT NULL,
                percentageBefore INTEGER NOT NULL,
                percentageAfter INTEGER NOT NULL,
                PRIMARY KEY(interactionId, cardCharacterId),
                FOREIGN KEY(interactionId) REFERENCES WorldInteraction(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(cardCharacterId) REFERENCES CardCharacter(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_DigimonScanReward_cardCharacterId ON DigimonScanReward(cardCharacterId)")
    }
}
