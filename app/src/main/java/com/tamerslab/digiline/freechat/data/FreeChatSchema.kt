package com.tamerslab.digiline.freechat.data

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 57: Free Talk tables (`FreeChatProfile`, `FreeChatTurn`).
 *
 * The raw SQL below must stay byte-for-byte compatible with the Room entities in this package:
 * after this migration Room compares the migrated schema against the entity-generated one and
 * fails to open the database on any mismatch (column name/type, NOT NULL, primary keys, index
 * names/uniqueness).
 *
 * Idempotency: `CREATE TABLE/INDEX IF NOT EXISTS` keeps the migration re-runnable, matching the
 * convention used by the other schema helpers in `com.tamerslab.data.database`.
 */
object FreeChatSchema {

    /** Creates every Free Talk table/index; safe to call on an already-migrated database. */
    fun migrate(db: SupportSQLiteDatabase) {
        createTables(db)
    }

    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `FreeChatProfile` (
                `individualId` TEXT NOT NULL,
                `turnCounter` INTEGER NOT NULL,
                `lastTalkAt` INTEGER NOT NULL,
                `lastMoodCreditDay` INTEGER NOT NULL,
                `moodCreditToday` INTEGER NOT NULL,
                `sessionCounter` INTEGER NOT NULL,
                `contentVersion` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `FreeChatTurn` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `individualId` TEXT NOT NULL,
                `turnCounter` INTEGER NOT NULL,
                `normalizedHash` INTEGER NOT NULL,
                `intentId` TEXT NOT NULL,
                `replyId` TEXT NOT NULL,
                `variantSeed` INTEGER NOT NULL,
                `scoreQ16` INTEGER NOT NULL,
                `fallbackLevel` INTEGER NOT NULL,
                `safetyClass` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_FreeChatTurn_individualId` " +
                "ON `FreeChatTurn` (`individualId`)"
        )
    }
}
