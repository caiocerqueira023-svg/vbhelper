package com.github.nacabaro.vbhelper.database

import androidx.sqlite.db.SupportSQLiteDatabase

/** The additive 25->26 schema, shared by migration and host SQLite verification. */
object WorldInteractionSchema {
    fun create(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `WorldInteraction` (
                `id` TEXT NOT NULL, `type` TEXT NOT NULL, `origin` TEXT NOT NULL,
                `state` TEXT NOT NULL, `seed` INTEGER NOT NULL, `rulesVersion` INTEGER NOT NULL,
                `startTick` INTEGER NOT NULL, `nextActionTick` INTEGER NOT NULL, `endTick` INTEGER,
                `revision` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL,
                `reservationExpiresAt` INTEGER, `reservedFrom` TEXT, `parentInteractionId` TEXT,
                `endedAt` INTEGER, `terminalReason` TEXT, PRIMARY KEY(`id`)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteraction_state_expiresAt` ON `WorldInteraction` (`state`, `expiresAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteraction_parentInteractionId` ON `WorldInteraction` (`parentInteractionId`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `WorldInteractionParticipant` (
                `interactionId` TEXT NOT NULL, `individualId` TEXT NOT NULL,
                `role` TEXT NOT NULL, `side` TEXT NOT NULL, `spawnId` INTEGER,
                `ownedCharacterId` INTEGER, `cardCharacterId` INTEGER,
                PRIMARY KEY(`interactionId`, `individualId`),
                FOREIGN KEY(`interactionId`) REFERENCES `WorldInteraction`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`spawnId`) REFERENCES `WorldSpawn`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`ownedCharacterId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                FOREIGN KEY(`cardCharacterId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_interactionId` ON `WorldInteractionParticipant` (`interactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_individualId` ON `WorldInteractionParticipant` (`individualId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_spawnId` ON `WorldInteractionParticipant` (`spawnId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_ownedCharacterId` ON `WorldInteractionParticipant` (`ownedCharacterId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_cardCharacterId` ON `WorldInteractionParticipant` (`cardCharacterId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WorldInteractionParticipant_interactionId_ownedCharacterId` ON `WorldInteractionParticipant` (`interactionId`, `ownedCharacterId`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `WorldParticipationClaim` (
                `individualId` TEXT NOT NULL, `interactionId` TEXT NOT NULL, `spawnId` INTEGER NOT NULL,
                PRIMARY KEY(`individualId`),
                FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`interactionId`) REFERENCES `WorldInteraction`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`spawnId`) REFERENCES `WorldSpawn`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldParticipationClaim_interactionId` ON `WorldParticipationClaim` (`interactionId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WorldParticipationClaim_spawnId` ON `WorldParticipationClaim` (`spawnId`)")
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `WorldInteractionResult` (
                `interactionId` TEXT NOT NULL, `outcome` TEXT NOT NULL,
                `recordedAt` INTEGER NOT NULL, `appliedAt` INTEGER, PRIMARY KEY(`interactionId`),
                FOREIGN KEY(`interactionId`) REFERENCES `WorldInteraction`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
    }
}
