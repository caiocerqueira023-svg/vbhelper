package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object ArenaSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS ArenaRun (id TEXT NOT NULL PRIMARY KEY,
            stateJson TEXT NOT NULL, revision INTEGER NOT NULL, updatedAt INTEGER NOT NULL)""".trimIndent())
        db.execSQL("""CREATE TABLE IF NOT EXISTS ArenaMatch (id TEXT NOT NULL PRIMARY KEY,
            tournamentId TEXT, tamerId TEXT NOT NULL, format INTEGER NOT NULL, stage INTEGER NOT NULL,
            difficulty TEXT NOT NULL, specJson TEXT NOT NULL, outcome TEXT, rewardBits INTEGER NOT NULL,
            alliedItemsUsed INTEGER NOT NULL, opposingItemsUsed INTEGER NOT NULL,
            createdAt INTEGER NOT NULL, completedAt INTEGER)""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ArenaMatch_tamerId ON ArenaMatch(tamerId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_ArenaMatch_tournamentId ON ArenaMatch(tournamentId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS ArenaRewardReceipt (id TEXT NOT NULL PRIMARY KEY,
            bits INTEGER NOT NULL, createdAt INTEGER NOT NULL)""".trimIndent())
    }
}
