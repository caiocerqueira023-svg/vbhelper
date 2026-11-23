package com.tamerslab.data.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** Schema for the canonical story progress block. VB progress is the app's existing progression. */
object ProgressDomainSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `story_progress` (
                `individualId` TEXT NOT NULL,
                `level` INTEGER NOT NULL,
                `expTotal` INTEGER NOT NULL,
                `hp` INTEGER NOT NULL,
                `maxHp` INTEGER NOT NULL,
                `mp` INTEGER NOT NULL,
                `maxMp` INTEGER NOT NULL,
                `attack` INTEGER NOT NULL,
                `defense` INTEGER NOT NULL,
                `spirit` INTEGER NOT NULL,
                `speed` INTEGER NOT NULL,
                `aptitude` INTEGER,
                `seededAt` INTEGER NOT NULL,
                `seedSource` TEXT NOT NULL,
                `revision` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`),
                FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    /** Removes the experimental vb_progress table; VB progress stays in the existing app columns. */
    fun dropVb(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `vb_progress`")
    }
}
