package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema 56: DigiLine v2 tables plus the two nullable card-rendering columns on
 * [com.tamerslab.domain.chat.ChatMessageEntity].
 *
 * The raw SQL below must stay byte-for-byte compatible with the Room entities in
 * `com.tamerslab.digiline.data`: after this migration Room compares the migrated schema against
 * the entity-generated one and fails to open the database on any mismatch (column name/type,
 * NOT NULL, primary keys, index names/uniqueness).
 *
 * Idempotency: `CREATE TABLE/INDEX IF NOT EXISTS` plus [hasColumn] guards keep the migration
 * re-runnable, matching the convention used by the other schema helpers in this package.
 */
object DigilineSchema {
    fun migrate(db: SupportSQLiteDatabase) {
        createTables(db)
        addChatMessageCardColumns(db)
    }

    /** Creates every DigiLine table/index; safe to call on an already-migrated database. */
    fun createTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineProfile` (
                `individualId` TEXT NOT NULL,
                `speciesVariant` INTEGER NOT NULL,
                `lastAskedAt` INTEGER NOT NULL,
                `lastQuizAt` INTEGER NOT NULL,
                `lastShiftAt` INTEGER NOT NULL,
                `sessionCounter` INTEGER NOT NULL,
                `contentVersion` INTEGER NOT NULL,
                `nativeNoticeShown` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineQuestionRecord` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `individualId` TEXT NOT NULL,
                `questionKey` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `askedAt` INTEGER NOT NULL,
                `answeredAt` INTEGER,
                `optionId` TEXT,
                `shiftJson` TEXT,
                `moodDelta` INTEGER NOT NULL,
                `status` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DigilineQuestionRecord_individualId` " +
                "ON `DigilineQuestionRecord` (`individualId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineQuizRecord` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `individualId` TEXT NOT NULL,
                `quizKey` TEXT NOT NULL,
                `chosenIndex` INTEGER NOT NULL,
                `correct` INTEGER NOT NULL,
                `moodDelta` INTEGER NOT NULL,
                `answeredAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DigilineQuizRecord_individualId` " +
                "ON `DigilineQuizRecord` (`individualId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `PersonalityTendency` (
                `individualId` TEXT NOT NULL,
                `typeNumber` INTEGER NOT NULL,
                `points` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`, `typeNumber`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `NativePersonalitySync` (
                `individualId` TEXT NOT NULL,
                `targetType` INTEGER NOT NULL,
                `targetNativeId` INTEGER,
                `state` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineNegotiation` (
                `wildIndividualId` TEXT NOT NULL,
                `tier` INTEGER NOT NULL,
                `nodeId` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `seed` INTEGER NOT NULL,
                `demandsJson` TEXT,
                `startedAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `departUsed` INTEGER NOT NULL,
                PRIMARY KEY(`wildIndividualId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineNegotiationLog` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `wildIndividualId` TEXT NOT NULL,
                `nodeId` TEXT NOT NULL,
                `optionId` TEXT,
                `effectsJson` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DigilineNegotiationLog_wildIndividualId` " +
                "ON `DigilineNegotiationLog` (`wildIndividualId`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `WildPersuasion` (
                `wildIndividualId` TEXT NOT NULL,
                `stageIndex` INTEGER NOT NULL,
                `topicsDoneJson` TEXT NOT NULL,
                `questOfferState` TEXT NOT NULL,
                `joinedAt` INTEGER NOT NULL,
                `lastTalkAt` INTEGER NOT NULL,
                `departureState` TEXT NOT NULL,
                PRIMARY KEY(`wildIndividualId`)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `DigilineEventRecord` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `individualId` TEXT NOT NULL,
                `eventKey` TEXT NOT NULL,
                `deliveredAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_DigilineEventRecord_individualId` " +
                "ON `DigilineEventRecord` (`individualId`)"
        )
    }

    /** Adds the nullable card payload columns the DigiLine transcript renderer reads (schema 56). */
    private fun addChatMessageCardColumns(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "ChatMessageEntity", "kind")) {
            db.execSQL("ALTER TABLE `ChatMessageEntity` ADD COLUMN `kind` TEXT")
        }
        if (!hasColumn(db, "ChatMessageEntity", "payloadJson")) {
            db.execSQL("ALTER TABLE `ChatMessageEntity` ADD COLUMN `payloadJson` TEXT")
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == column) return true
            }
        }
        return false
    }
}
