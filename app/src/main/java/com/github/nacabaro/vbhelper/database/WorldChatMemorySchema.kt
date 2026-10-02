package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

object WorldChatMemorySchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldBattleContext (
                interactionId TEXT NOT NULL PRIMARY KEY, friendly INTEGER NOT NULL, chatIndividualId TEXT,
                reason TEXT NOT NULL, transcriptJson TEXT NOT NULL, createdAt INTEGER NOT NULL,
                FOREIGN KEY(interactionId) REFERENCES WorldInteraction(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldBattleContext_chatIndividualId ON WorldBattleContext(chatIndividualId)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldBattleMemory (
                interactionId TEXT NOT NULL, individualId TEXT NOT NULL, cardCharacterId INTEGER,
                individualName TEXT NOT NULL, opponentName TEXT NOT NULL, perspective TEXT NOT NULL, friendly INTEGER NOT NULL,
                reason TEXT NOT NULL, transcriptJson TEXT NOT NULL, createdAt INTEGER NOT NULL,
                needsReaction INTEGER NOT NULL, reactionMessageId INTEGER,
                PRIMARY KEY(interactionId,individualId),
                FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldBattleMemory_individualId_createdAt ON WorldBattleMemory(individualId,createdAt)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS WorldPrivateChatLink (
                sourceMessageId TEXT NOT NULL PRIMARY KEY, individualId TEXT NOT NULL, chatMessageId INTEGER NOT NULL,
                FOREIGN KEY(individualId) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldPrivateChatLink_individualId ON WorldPrivateChatLink(individualId)")
    }
}
