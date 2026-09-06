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
import com.github.nacabaro.vbhelper.domain.items.Items
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

@Database(
    version = 7,
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
        SpeciesProfile::class
    ]
)
@TypeConverters(SpeciesProfileConverters::class)
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

    companion object {
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
    }
}
