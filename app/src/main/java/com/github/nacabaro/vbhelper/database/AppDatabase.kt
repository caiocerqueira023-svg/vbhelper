package com.github.nacabaro.vbhelper.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.nacabaro.vbhelper.daos.AdventureDao
import com.github.nacabaro.vbhelper.daos.CardAdventureDao
import com.github.nacabaro.vbhelper.daos.CharacterDao
import com.github.nacabaro.vbhelper.daos.ChatDao
import com.github.nacabaro.vbhelper.daos.DexDao
import com.github.nacabaro.vbhelper.daos.DigimonIndividualDao
import com.github.nacabaro.vbhelper.daos.DigimonStateSnapshotDao
import com.github.nacabaro.vbhelper.daos.CardDao
import com.github.nacabaro.vbhelper.daos.CardFusionsDao
import com.github.nacabaro.vbhelper.daos.CardProgressDao
import com.github.nacabaro.vbhelper.daos.ItemDao
import com.github.nacabaro.vbhelper.daos.SpecialMissionDao
import com.github.nacabaro.vbhelper.daos.SpeciesProfileDao
import com.github.nacabaro.vbhelper.daos.SpriteDao
import com.github.nacabaro.vbhelper.daos.UserCharacterDao
import com.github.nacabaro.vbhelper.daos.VitalWearSettingsDao
import com.github.nacabaro.vbhelper.daos.CharacterTransferPolicyDao
import com.github.nacabaro.vbhelper.daos.LorebookEntryDao
import com.github.nacabaro.vbhelper.daos.WorldSpawnDao
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardDao
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardEntity
import com.github.nacabaro.vbhelper.domain.card.Background
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.card.Card
import com.github.nacabaro.vbhelper.domain.card.CardAdventure
import com.github.nacabaro.vbhelper.domain.card.CardFusions
import com.github.nacabaro.vbhelper.domain.card.CardProgress
import com.github.nacabaro.vbhelper.domain.card.PossibleTransformations
import com.github.nacabaro.vbhelper.domain.characters.Sprite
import com.github.nacabaro.vbhelper.domain.characters.Adventure
import com.github.nacabaro.vbhelper.domain.characters.Dex
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.device_data.BECharacterData
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.VitalsHistory
import com.github.nacabaro.vbhelper.domain.device_data.VitalWearCharacterSettings
import com.github.nacabaro.vbhelper.domain.device_data.CharacterTransferPolicy
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.reactions.DigimonStateSnapshot
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityTraits
import com.github.nacabaro.vbhelper.domain.items.Items
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn

@Database(
    version = 13,
    exportSchema = false,
    entities = [
        Card::class,
        CardProgress::class,
        CardCharacter::class,
        CardAdventure::class,
        CardFusions::class,
        Sprite::class,
        UserCharacter::class,
        DigimonIndividual::class,
        BECharacterData::class,
        VBCharacterData::class,
        SpecialMissions::class,
        TransformationHistory::class,
        VitalsHistory::class,
        Dex::class,
        Items::class,
        Adventure::class,
        Background::class,
        PossibleTransformations::class,
        ValidatedCardEntity::class,
        VitalWearCharacterSettings::class,
        CharacterTransferPolicy::class,
        ChatMessageEntity::class,
        SpeciesProfile::class,
        DigimonPersonalityTraits::class,
        DigimonStateSnapshot::class,
        LorebookEntry::class,
        WorldSpawn::class
    ]
)
@TypeConverters(SpeciesProfileConverters::class, PersonalityConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cardDao(): CardDao
    abstract fun cardProgressDao(): CardProgressDao
    abstract fun characterDao(): CharacterDao
    abstract fun userCharacterDao(): UserCharacterDao
    abstract fun digimonIndividualDao(): DigimonIndividualDao
    abstract fun dexDao(): DexDao
    abstract fun itemDao(): ItemDao
    abstract fun adventureDao(): AdventureDao
    abstract fun spriteDao(): SpriteDao
    abstract fun specialMissionDao(): SpecialMissionDao
    abstract fun cardAdventureDao(): CardAdventureDao
    abstract fun cardFusionsDao(): CardFusionsDao
    abstract fun validatedCardDao(): ValidatedCardDao
    abstract fun vitalWearSettingsDao(): VitalWearSettingsDao
    abstract fun characterTransferPolicyDao(): CharacterTransferPolicyDao
    abstract fun chatDao(): ChatDao
    abstract fun speciesProfileDao(): SpeciesProfileDao
    abstract fun digimonStateSnapshotDao(): DigimonStateSnapshotDao
    abstract fun lorebookEntryDao(): LorebookEntryDao
    abstract fun worldSpawnDao(): WorldSpawnDao

    companion object {
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `mood` INTEGER NOT NULL DEFAULT 50")
                db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `recruitmentState` TEXT NOT NULL DEFAULT 'WILD'")
            }
        }

        /**
         * Repairs characters created by World recruitment before the auxiliary-row
         * creation was made atomic. Such characters can have UserCharacter/VBCharacterData
         * but no TransformationHistory and/or no SpecialMissions rows.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT INTO TransformationHistory(monId, stageId, transformationDate)
                    SELECT uc.id, uc.charId, CAST(strftime('%s', 'now') AS INTEGER) * 1000
                    FROM UserCharacter uc
                    LEFT JOIN TransformationHistory th ON th.monId = uc.id
                    WHERE th.id IS NULL
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO SpecialMissions(
                        characterId, goal, watchId, progress, status,
                        timeElapsedInMinutes, timeLimitInMinutes, missionType
                    )
                    SELECT
                        uc.id,
                        0,
                        ((uc.id * 4 + slots.slot) % 65535) + 1,
                        0,
                        'UNAVAILABLE',
                        0,
                        0,
                        'NONE'
                    FROM UserCharacter uc
                    JOIN (
                        SELECT 0 AS slot
                        UNION ALL SELECT 1
                        UNION ALL SELECT 2
                        UNION ALL SELECT 3
                    ) slots
                    WHERE uc.characterType = 'VBDevice'
                      AND slots.slot < (
                          4 - (
                              SELECT COUNT(*)
                              FROM SpecialMissions sm
                              WHERE sm.characterId = uc.id
                          )
                      )
                    """.trimIndent()
                )
            }
        }


        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WorldSpawn` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `cardCharacterId` INTEGER NOT NULL,
                        `individualId` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `spawnedAt` INTEGER NOT NULL,
                        `expiresAt` INTEGER NOT NULL,
                        `interacted` INTEGER NOT NULL,
                        FOREIGN KEY(`cardCharacterId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WorldSpawn_cardCharacterId` ON `WorldSpawn` (`cardCharacterId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WorldSpawn_individualId` ON `WorldSpawn` (`individualId`)")
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new tables
                db.execSQL("CREATE TABLE IF NOT EXISTS `Items` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `description` TEXT NOT NULL, `itemIcon` INTEGER NOT NULL, `itemLength` INTEGER NOT NULL, `price` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `itemType` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SpecialMissions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `characterId` INTEGER NOT NULL, `goal` INTEGER NOT NULL, `watchId` INTEGER NOT NULL, `progress` INTEGER NOT NULL, `status` TEXT NOT NULL, `timeElapsedInMinutes` INTEGER NOT NULL, `timeLimitInMinutes` INTEGER NOT NULL, `missionType` TEXT NOT NULL, FOREIGN KEY(`characterId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `VBCharacterData` (`id` INTEGER NOT NULL, `generation` INTEGER NOT NULL, `totalTrophies` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `TransformationHistory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `monId` INTEGER NOT NULL, `stageId` INTEGER NOT NULL, `transformationDate` INTEGER NOT NULL, FOREIGN KEY(`monId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`stageId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `VitalsHistory` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `charId` INTEGER NOT NULL, `year` INTEGER NOT NULL, `month` INTEGER NOT NULL, `day` INTEGER NOT NULL, `vitalPoints` INTEGER NOT NULL, FOREIGN KEY(`charId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Dex` (`id` INTEGER NOT NULL, `discoveredOn` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`id`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Adventure` (`characterId` INTEGER NOT NULL, `originalDuration` INTEGER NOT NULL, `finishesAdventure` INTEGER NOT NULL, PRIMARY KEY(`characterId`), FOREIGN KEY(`characterId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `Background` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardId` INTEGER NOT NULL, `background` BLOB NOT NULL, `backgroundWidth` INTEGER NOT NULL, `backgroundHeight` INTEGER NOT NULL, FOREIGN KEY(`cardId`) REFERENCES `Card`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `PossibleTransformations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `charaId` INTEGER NOT NULL, `requiredVitals` INTEGER NOT NULL, `requiredTrophies` INTEGER NOT NULL, `requiredBattles` INTEGER NOT NULL, `requiredWinRate` INTEGER NOT NULL, `changeTimerHours` INTEGER NOT NULL, `requiredAdventureLevelCompleted` INTEGER NOT NULL, `toCharaId` INTEGER, FOREIGN KEY(`charaId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`toCharaId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE TABLE IF NOT EXISTS `validated` (`cardId` INTEGER NOT NULL, PRIMARY KEY(`cardId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `VitalWearCharacterSettings` (`characterId` INTEGER NOT NULL, `trainingInBackground` INTEGER NOT NULL, `allowedBattles` INTEGER NOT NULL, `accumulatedDailyInjuries` INTEGER NOT NULL, PRIMARY KEY(`characterId`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `CharacterTransferPolicy` (`characterId` INTEGER NOT NULL, `nativeDeviceType` TEXT NOT NULL, `preferredHceExportFormat` TEXT NOT NULL, `preferredNfcaExportFormat` TEXT NOT NULL, `lastObservedImportFormat` TEXT, `lastTransferTransport` TEXT, `lastTransferTarget` TEXT, `preserveVbRoundTrip` INTEGER NOT NULL, `preserveBeRoundTrip` INTEGER NOT NULL, PRIMARY KEY(`characterId`), FOREIGN KEY(`characterId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")

                // Add columns to existing tables
                try {
                    db.execSQL("ALTER TABLE `UserCharacter` ADD COLUMN `characterType` TEXT NOT NULL DEFAULT 'BEDevice'")
                } catch (e: Exception) {}
                try {
                    db.execSQL("ALTER TABLE `Card` ADD COLUMN `isBEm` INTEGER NOT NULL DEFAULT 0")
                } catch (e: Exception) {}
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ChatMessageEntity` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `characterId` INTEGER NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`characterId`) REFERENCES `UserCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ChatMessageEntity_characterId` ON `ChatMessageEntity` (`characterId`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `Card` ADD COLUMN `officialStatus` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `SpeciesProfile` (
                        `cardCharacterId` INTEGER NOT NULL,
                        `speciesName` TEXT,
                        `matchedName` TEXT,
                        `level` TEXT,
                        `type` TEXT,
                        `profileDescription` TEXT,
                        `specialMoves` TEXT NOT NULL,
                        `source` TEXT NOT NULL,
                        PRIMARY KEY(`cardCharacterId`),
                        FOREIGN KEY(`cardCharacterId`) REFERENCES `CardCharacter`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SpeciesProfile_cardCharacterId` ON `SpeciesProfile` (`cardCharacterId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `DigimonIndividual` (`individualId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`individualId`))"
                )
                db.execSQL("ALTER TABLE `UserCharacter` ADD COLUMN `individualId` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "UPDATE `UserCharacter` SET `individualId` = lower(hex(randomblob(11))) WHERE `individualId` = ''"
                )
                db.execSQL(
                    "INSERT OR IGNORE INTO `DigimonIndividual` (`individualId`, `createdAt`) SELECT `individualId`, 0 FROM `UserCharacter`"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ChatMessageEntity_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `individualId` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `ChatMessageEntity_new` (`id`, `individualId`, `role`, `content`, `timestamp`)
                    SELECT cm.`id`, uc.`individualId`, cm.`role`, cm.`content`, cm.`timestamp`
                    FROM `ChatMessageEntity` cm
                    JOIN `UserCharacter` uc ON uc.`id` = cm.`characterId`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `ChatMessageEntity`")
                db.execSQL("ALTER TABLE `ChatMessageEntity_new` RENAME TO `ChatMessageEntity`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ChatMessageEntity_individualId` ON `ChatMessageEntity` (`individualId`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `DigimonIndividual` ADD COLUMN `nickname` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `DigimonPersonalityTraits` (
                        `individualId` TEXT NOT NULL,
                        `temperament` TEXT NOT NULL,
                        `socialStyle` TEXT NOT NULL,
                        `speechQuirk` TEXT NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`individualId`),
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_DigimonPersonalityTraits_individualId` ON `DigimonPersonalityTraits` (`individualId`)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `DigimonStateSnapshot` (
                        `individualId` TEXT NOT NULL,
                        `stage` INTEGER NOT NULL,
                        `mood` INTEGER NOT NULL,
                        `vitalPoints` INTEGER NOT NULL,
                        `trophies` INTEGER NOT NULL,
                        `totalBattlesWon` INTEGER NOT NULL,
                        `totalBattlesLost` INTEGER NOT NULL,
                        `injuryStatus` TEXT NOT NULL,
                        `specialMissionsJson` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`individualId`),
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("ALTER TABLE `DigimonIndividual` ADD COLUMN `lastDiaryEntryAt` INTEGER")
                db.execSQL("ALTER TABLE `DigimonIndividual` ADD COLUMN `lastCelebratedWinsMilestone` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `DigimonIndividual` ADD COLUMN `lastCelebratedTrophyMilestone` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `LorebookEntry` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `triggerKeys` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `caseSensitive` INTEGER NOT NULL,
                        `priority` INTEGER NOT NULL,
                        `source` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
