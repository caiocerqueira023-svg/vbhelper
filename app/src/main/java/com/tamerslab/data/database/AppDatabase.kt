package com.tamerslab.data.database

import androidx.room.Database
import com.tamerslab.storage.data.EvolutionHistoryDao
import com.tamerslab.transfer.data.WatchTransferDao
import com.tamerslab.domain.identity.WatchTransfer
import com.tamerslab.domain.identity.WatchImportReceipt
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tamerslab.adventure.data.AdventureDao
import com.tamerslab.adventure.data.CardAdventureDao
import com.tamerslab.storage.data.CharacterDao
import com.tamerslab.chat.data.ChatDao
import com.tamerslab.species.data.DexDao
import com.tamerslab.species.data.DigimonScanDao
import com.tamerslab.domain.scan.DigimonScanProgress
import com.tamerslab.domain.scan.DigimonScanReward
import com.tamerslab.storage.data.DigimonIndividualDao
import com.tamerslab.storage.data.DigimonTechniqueLoadoutDao
import com.tamerslab.storage.data.DigimonStateSnapshotDao
import com.tamerslab.species.data.CardDao
import com.tamerslab.species.data.CardFusionsDao
import com.tamerslab.species.data.CardProgressDao
import com.tamerslab.items.data.ItemDao
import com.tamerslab.storage.data.SpecialMissionDao
import com.tamerslab.species.data.SpeciesProfileDao
import com.tamerslab.species.data.SpriteDao
import com.tamerslab.storage.data.UserCharacterDao
import com.tamerslab.storage.data.VitalWearSettingsDao
import com.tamerslab.transfer.data.CharacterTransferPolicyDao
import com.tamerslab.chat.data.LorebookEntryDao
import com.tamerslab.world.data.WorldSpawnDao
import com.tamerslab.world.data.WorldEcosystemDao
import com.tamerslab.world.ecosystem.WorldEcosystemSession
import com.tamerslab.world.data.WorldInteractionDao
import com.tamerslab.world.ecosystem.WorldInteraction
import com.tamerslab.world.ecosystem.WorldInteractionParticipant
import com.tamerslab.world.ecosystem.WorldParticipationClaim
import com.tamerslab.world.ecosystem.WorldInteractionResult
import com.tamerslab.world.ecosystem.WorldDen
import com.tamerslab.world.ecosystem.WildPairBond
import com.tamerslab.world.ecosystem.WorldEcosystemInput
import com.tamerslab.world.ecosystem.WorldInteractionMessage
import com.tamerslab.world.ecosystem.WorldDialogueIntent
import com.tamerslab.world.ecosystem.WorldNpcBattle
import com.tamerslab.world.ecosystem.WorldBattleContext
import com.tamerslab.world.ecosystem.WorldBattleMemory
import com.tamerslab.world.ecosystem.WorldPrivateChatLink
import com.tamerslab.chat.data.WorldChatMemoryDao
import com.tamerslab.farm.data.DigifarmDao
import com.tamerslab.world.data.WildRelationshipDao
import com.tamerslab.companion.validation.ValidatedCardDao
import com.tamerslab.companion.validation.ValidatedCardEntity
import com.tamerslab.domain.card.Background
import com.tamerslab.domain.card.CardCharacter
import com.tamerslab.domain.card.CardAttackArt
import com.tamerslab.species.data.CardAttackArtDao
import com.tamerslab.domain.card.Card
import com.tamerslab.domain.card.CardAdventure
import com.tamerslab.domain.card.CardFusions
import com.tamerslab.domain.card.CardSpecificJogress
import com.tamerslab.domain.card.CardProgress
import com.tamerslab.domain.card.PossibleTransformations
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.domain.characters.Adventure
import com.tamerslab.domain.characters.Dex
import com.tamerslab.domain.chat.ChatMessageEntity
import com.tamerslab.domain.device_data.BECharacterData
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.device_data.TransformationHistory
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.domain.device_data.VBCharacterData
import com.tamerslab.domain.device_data.VitalsHistory
import com.tamerslab.domain.device_data.VitalWearCharacterSettings
import com.tamerslab.domain.device_data.CharacterTransferPolicy
import com.tamerslab.domain.device_data.DigimonIndividual
import com.tamerslab.domain.device_data.DigimonTechniqueLoadout
import com.tamerslab.domain.reactions.DigimonStateSnapshot
import com.tamerslab.domain.personality.DigimonPersonalityTraits
import com.tamerslab.domain.items.Items
import com.tamerslab.domain.species.SpeciesProfile
import com.tamerslab.domain.lorebook.LorebookEntry
import com.tamerslab.domain.world.WorldSpawn
import com.tamerslab.domain.digifarm.Farm
import com.tamerslab.domain.digifarm.FarmMessage
import com.tamerslab.domain.digifarm.FarmMessageRecipient
import com.tamerslab.domain.digifarm.FarmReadState
import com.tamerslab.domain.digifarm.FarmRelationship
import com.tamerslab.domain.digifarm.FarmMemory
import com.tamerslab.domain.digifarm.FarmResident
import com.tamerslab.domain.digifarm.WildRelationship

@Database(
    version = 60,
    exportSchema = true,
    views = [com.tamerslab.domain.species.DisplaySprite::class],
    entities = [
        Card::class,
        CardProgress::class,
        CardCharacter::class,
        CardAttackArt::class,
        CardAdventure::class,
        CardFusions::class,
        CardSpecificJogress::class,
        Sprite::class,
        UserCharacter::class,
        DigimonIndividual::class,
        com.tamerslab.domain.device_data.ParticipationPause::class,
        com.tamerslab.domain.species.AppSourceState::class,
        com.tamerslab.domain.species.SpeciesDefaultSprite::class,
        com.tamerslab.domain.species.AppPartnerProgress::class,
        com.tamerslab.domain.species.FirstPartnerEncounter::class,
        DigimonTechniqueLoadout::class,
        WatchTransfer::class,
        WatchImportReceipt::class,
        BECharacterData::class,
        VBCharacterData::class,
        SpecialMissions::class,
        TransformationHistory::class,
        VitalsHistory::class,
        Dex::class,
        DigimonScanProgress::class,
        DigimonScanReward::class,
        Items::class,
        Adventure::class,
        Background::class,
        PossibleTransformations::class,
        ValidatedCardEntity::class,
        VitalWearCharacterSettings::class,
        CharacterTransferPolicy::class,
        ChatMessageEntity::class,
        SpeciesProfile::class,
        com.tamerslab.domain.species.SpeciesCatalogEntry::class,
        com.tamerslab.domain.species.SpeciesSpriteChoice::class,
        DigimonPersonalityTraits::class,
        DigimonStateSnapshot::class,
        LorebookEntry::class,
        WorldSpawn::class,
        Farm::class,
        FarmResident::class,
        FarmMessage::class,
        FarmMessageRecipient::class,
        FarmReadState::class,
        FarmRelationship::class,
        FarmMemory::class,
        WildRelationship::class,
        WorldEcosystemSession::class,
        WorldInteraction::class,
        WorldInteractionParticipant::class,
        WorldParticipationClaim::class,
        WorldInteractionResult::class,
        WorldDen::class,
        WildPairBond::class,
        WorldEcosystemInput::class,
        WorldInteractionMessage::class,
        WorldDialogueIntent::class,
        WorldNpcBattle::class,
        WorldBattleContext::class,
        WorldBattleMemory::class,
        WorldPrivateChatLink::class,
        com.tamerslab.world.ecosystem.WorldSocialMemory::class,
        com.tamerslab.world.ecosystem.IndividualSocialState::class,
        com.tamerslab.quests.QuestInstance::class,
        com.tamerslab.quests.QuestObjective::class,
        com.tamerslab.quests.QuestEvidence::class,
        com.tamerslab.quests.QuestRewardReceipt::class,
        com.tamerslab.quests.QuestWatchBaseline::class,
        com.tamerslab.quests.QuestWallet::class,
        com.tamerslab.quests.QuestBattleStock::class,
        com.tamerslab.quests.QuestBattleReservation::class,
        com.tamerslab.quests.QuestToken::class,
        com.tamerslab.quests.QuestPhaseReceipt::class,
        com.tamerslab.quests.QuestBattleReport::class,
        com.tamerslab.quests.QuestBattleMember::class,
        com.tamerslab.quests.QuestBattleTechniqueHit::class,
        com.tamerslab.quests.QuestBattleAttempt::class,
        com.tamerslab.battle.offline.tamers.ArenaRunEntity::class,
        com.tamerslab.battle.offline.tamers.ArenaMatchEntity::class,
        com.tamerslab.battle.offline.tamers.ArenaRewardReceipt::class,
        com.tamerslab.domain.transfer.LinkedGameSave::class,
        com.tamerslab.domain.transfer.LinkedSaveObservation::class,
        com.tamerslab.domain.transfer.SaveImportDraft::class,
        com.tamerslab.domain.transfer.SaveImportDraftItem::class,
        com.tamerslab.domain.transfer.GameImportItemReceipt::class,
        com.tamerslab.domain.transfer.ExternalIndividualBinding::class,
        com.tamerslab.domain.transfer.NativeGameSnapshot::class,
        com.tamerslab.domain.transfer.NativeCanonicalMapping::class,
        com.tamerslab.domain.transfer.NativeMaterializationReceipt::class,
        com.tamerslab.domain.transfer.CompanionConnection::class,
        com.tamerslab.domain.transfer.LinkedSaveLocation::class,
        com.tamerslab.domain.transfer.SaveLocationExclusion::class,
        com.tamerslab.domain.transfer.IndividualLifeEvent::class,
        com.tamerslab.domain.transfer.AppProgressDelta::class,
        com.tamerslab.domain.transfer.NativeAppProjection::class,
        com.tamerslab.domain.transfer.StoryProgressDomain::class,
        com.tamerslab.domain.transfer.NativeSaveContainer::class,
        com.tamerslab.domain.transfer.NativeSaveContainerChunk::class,
        com.tamerslab.domain.transfer.NativeSnapshotContainer::class,
        com.tamerslab.domain.transfer.UniversalJourneyAccount::class,
        com.tamerslab.domain.transfer.UniversalProgressReceipt::class,
        com.tamerslab.domain.transfer.PendingNativeProgress::class,
        com.tamerslab.domain.transfer.ProgressProjectionGuard::class,
        com.tamerslab.domain.transfer.GameEndpointPresence::class,
        com.tamerslab.domain.identity.WatchRepresentationSnapshot::class,
        com.tamerslab.digiline.data.DigilineProfileEntity::class,
        com.tamerslab.digiline.data.DigilineQuestionRecordEntity::class,
        com.tamerslab.digiline.data.DigilineQuizRecordEntity::class,
        com.tamerslab.digiline.data.PersonalityTendencyEntity::class,
        com.tamerslab.digiline.data.NativePersonalitySyncEntity::class,
        com.tamerslab.digiline.data.DigilineNegotiationEntity::class,
        com.tamerslab.digiline.data.DigilineNegotiationLogEntity::class,
        com.tamerslab.digiline.data.WildPersuasionEntity::class,
        com.tamerslab.digiline.data.DigilineEventRecordEntity::class,
        // Free Talk (schema 57): owned-partner free-chat cadence/credit + turn audit.
        com.tamerslab.digiline.freechat.data.FreeChatProfileEntity::class,
        com.tamerslab.digiline.freechat.data.FreeChatTurnEntity::class,
        // Tamer Rank (schema 58): offline Tamer profile, XP ledger, caps, rewards and activity.
        com.tamerslab.rank.data.TamerProfileEntity::class,
        com.tamerslab.rank.data.TamerTxpEventEntity::class,
        com.tamerslab.rank.data.TamerDailyCapEntity::class,
        com.tamerslab.rank.data.TamerRankRewardEntity::class,
        com.tamerslab.rank.data.TamerActivityEntity::class
    ]
)
@TypeConverters(SpeciesProfileConverters::class, PersonalityConverters::class)
abstract class AppDatabase : RoomDatabase() {
    /** Runtime policy shared by all repository instances using this database; not a Room entity. */
    @Volatile var participation: com.tamerslab.species.PartnerParticipationGate? = null
    @Volatile var expandedCatalog: com.tamerslab.species.ExpandedCatalogRepository? = null
    abstract fun cardDao(): CardDao
    abstract fun cardProgressDao(): CardProgressDao
    abstract fun characterDao(): CharacterDao
    abstract fun cardAttackArtDao(): CardAttackArtDao
    abstract fun userCharacterDao(): UserCharacterDao
    abstract fun evolutionHistoryDao(): EvolutionHistoryDao
    abstract fun digimonIndividualDao(): DigimonIndividualDao
    abstract fun digimonTechniqueLoadoutDao(): DigimonTechniqueLoadoutDao
    abstract fun watchTransferDao(): WatchTransferDao
    abstract fun dexDao(): DexDao
    abstract fun digimonScanDao(): DigimonScanDao
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
    abstract fun speciesCustomizationDao(): com.tamerslab.species.data.SpeciesCustomizationDao
    abstract fun digimonStateSnapshotDao(): DigimonStateSnapshotDao
    abstract fun lorebookEntryDao(): LorebookEntryDao
    abstract fun worldSpawnDao(): WorldSpawnDao
    abstract fun digifarmDao(): DigifarmDao
    abstract fun wildRelationshipDao(): WildRelationshipDao
    abstract fun worldEcosystemDao(): WorldEcosystemDao
    abstract fun worldInteractionDao(): WorldInteractionDao
    abstract fun worldChatMemoryDao(): WorldChatMemoryDao
    abstract fun firstPartnerDao(): com.tamerslab.storage.data.FirstPartnerDao
    abstract fun worldSocialDao(): com.tamerslab.world.data.WorldSocialDao
    abstract fun questDao(): com.tamerslab.quests.data.QuestDao
    abstract fun arenaDao(): com.tamerslab.battle.data.ArenaDao
    abstract fun linkedGameSaveDao(): com.tamerslab.transfer.data.LinkedGameSaveDao
    abstract fun saveImportDraftDao(): com.tamerslab.transfer.data.SaveImportDraftDao
    abstract fun nativeGameArchiveDao(): com.tamerslab.transfer.data.NativeGameArchiveDao
    abstract fun nativeMaterializationDao(): com.tamerslab.transfer.data.NativeMaterializationDao
    abstract fun transferContinuityDao(): com.tamerslab.transfer.data.TransferContinuityDao
    abstract fun appPartnerStateDao(): com.tamerslab.storage.data.AppPartnerStateDao
    abstract fun digilineDao(): com.tamerslab.digiline.data.DigilineDao
    abstract fun freeChatDao(): com.tamerslab.digiline.freechat.data.FreeChatDao
    abstract fun rankDao(): com.tamerslab.rank.data.RankDao
    abstract fun progressDomainDao(): com.tamerslab.transfer.data.ProgressDomainDao

    companion object {
        val MIGRATION_59_60 = object : Migration(59, 60) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ProgressDomainSchema.dropVb(db)
            }
        }
        val MIGRATION_58_59 = object : Migration(58, 59) {
            override fun migrate(db: SupportSQLiteDatabase) {
                ProgressDomainSchema.create(db)
            }
        }
        val MIGRATION_57_58 = object : Migration(57, 58) {
            override fun migrate(db: SupportSQLiteDatabase) {
                com.tamerslab.rank.data.RankSchema.migrate(db)
            }
        }
        val MIGRATION_56_57 = object : Migration(56, 57) {
            override fun migrate(db: SupportSQLiteDatabase) {
                com.tamerslab.digiline.freechat.data.FreeChatSchema.migrate(db)
            }
        }
        val MIGRATION_55_56 = object : Migration(55, 56) {
            override fun migrate(db: SupportSQLiteDatabase) {
                DigilineSchema.migrate(db)
            }
        }
        val MIGRATION_54_55 = object : Migration(54, 55) {
            override fun migrate(db: SupportSQLiteDatabase) {
                GameEndpointPresenceSchema.create(db)
            }
        }
        val MIGRATION_53_54 = object : Migration(53, 54) {
            override fun migrate(db: SupportSQLiteDatabase) {
                SpritePreferenceSchema.migrate(db)
                WatchRepresentationSnapshotSchema.create(db)
            }
        }
        val MIGRATION_51_52 = object : Migration(51, 52) {
            override fun migrate(db: SupportSQLiteDatabase) { ProvisionalSnapshotSchema.migrate(db) }
        }
        val MIGRATION_52_53 = object : Migration(52, 53) {
            override fun migrate(db: SupportSQLiteDatabase) { WorldDsAcquisitionSchema.migrate(db) }
        }
        val MIGRATION_50_51 = object : Migration(50, 51) {
            override fun migrate(db: SupportSQLiteDatabase) { TransferContinuitySchema.migrate(db) }
        }
        val MIGRATION_49_50 = object : Migration(49, 50) {
            override fun migrate(db: SupportSQLiteDatabase) { NativeMaterializationSchema.migrate(db) }
        }
        val MIGRATION_48_49 = object : Migration(48, 49) {
            override fun migrate(db: SupportSQLiteDatabase) { NativeRefreshSchema.migrate(db) }
        }
        val MIGRATION_47_48 = object : Migration(47, 48) {
            override fun migrate(db: SupportSQLiteDatabase) { NativeGameArchiveSchema.migrate(db) }
        }
        val MIGRATION_46_47 = object : Migration(46, 47) {
            override fun migrate(db: SupportSQLiteDatabase) { SaveImportDraftSchema.migrate(db) }
        }
        val MIGRATION_45_46 = object : Migration(45, 46) {
            override fun migrate(db: SupportSQLiteDatabase) { LinkedGameSaveSchema.migrate(db) }
        }
        val MIGRATION_44_45 = object : Migration(44, 45) {
            override fun migrate(db: SupportSQLiteDatabase) { ModeSpriteSchema.migrate(db) }
        }
        val MIGRATION_43_44 = object : Migration(43, 44) {
            override fun migrate(db: SupportSQLiteDatabase) { SourceIndependentSchema.migrate(db) }
        }
        val MIGRATION_42_43 = object : Migration(42, 43) {
            override fun migrate(db: SupportSQLiteDatabase) { ParticipationPauseSchema.migrate(db) }
        }

        val MIGRATION_41_42 = object : Migration(41, 42) {
            override fun migrate(db: SupportSQLiteDatabase) { SpeciesBindingSchema.migrate(db) }
        }

        val MIGRATION_40_41 = object : Migration(40, 41) {
            override fun migrate(db: SupportSQLiteDatabase) { SpeciesCustomizationSchema.migrate(db) }
        }

        val MIGRATION_39_40 = object : Migration(39, 40) {
            override fun migrate(db: SupportSQLiteDatabase) { ArenaSchema.create(db) }
        }

        val MIGRATION_38_39 = object : Migration(38, 39) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "DigimonIndividual", "blastMode")) {
                    db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN blastMode TEXT NOT NULL DEFAULT 'NONE'")
                }
                if (!hasColumn(db, "DigimonIndividual", "blastTargetSpecies")) {
                    db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN blastTargetSpecies TEXT")
                }
                if (!hasColumn(db, "DigimonIndividual", "jogressResultSpecies")) {
                    db.execSQL("ALTER TABLE DigimonIndividual ADD COLUMN jogressResultSpecies TEXT")
                }
            }
        }

        val MIGRATION_37_38 = object : Migration(37, 38) {
            override fun migrate(db: SupportSQLiteDatabase) { QuestSchema.addWatchCapabilities(db) }
        }

        val MIGRATION_36_37 = object : Migration(36, 37) {
            override fun migrate(db: SupportSQLiteDatabase) { QuestSchema.addFollowUps(db) }
        }

        val MIGRATION_35_36 = object : Migration(35, 36) {
            override fun migrate(db: SupportSQLiteDatabase) { QuestSchema.addBattleConditions(db) }
        }

        val MIGRATION_34_35 = object : Migration(34, 35) {
            override fun migrate(db: SupportSQLiteDatabase) { QuestSchema.addStorySteps(db) }
        }

        val MIGRATION_33_34 = object : Migration(33, 34) {
            override fun migrate(db: SupportSQLiteDatabase) { QuestSchema.create(db) }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) { WorldSocialSchema.create(db) }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) { DigimonScanSchema.create(db) }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) { CardJogressSchema.create(db) }
        }

        val MIGRATION_29_30 = object : Migration(29,30) {
            override fun migrate(db: SupportSQLiteDatabase) { WorldChatMemorySchema.create(db) }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) { CardAttackArtSchema.create(db) }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) { WorldDialogueSchema.create(db) }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "WorldSpawn", "movementTick")) db.execSQL("ALTER TABLE WorldSpawn ADD COLUMN movementTick INTEGER NOT NULL DEFAULT 0")
                if (!hasColumn(db, "WorldSpawn", "nextDecisionTick")) db.execSQL("ALTER TABLE WorldSpawn ADD COLUMN nextDecisionTick INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS WorldDen (id TEXT NOT NULL PRIMARY KEY, latitude REAL NOT NULL, longitude REAL NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS WildPairBond (
                        individualA TEXT NOT NULL, individualB TEXT NOT NULL, affinity INTEGER NOT NULL,
                        chatCount INTEGER NOT NULL, aWins INTEGER NOT NULL, bWins INTEGER NOT NULL, draws INTEGER NOT NULL,
                        lastInteractionTick INTEGER NOT NULL, lastInteractionAt INTEGER NOT NULL, cooldownUntilTick INTEGER NOT NULL,
                        PRIMARY KEY(individualA,individualB),
                        FOREIGN KEY(individualA) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(individualB) REFERENCES DigimonIndividual(individualId) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_WildPairBond_individualA ON WildPairBond(individualA)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_WildPairBond_individualB ON WildPairBond(individualB)")
                db.execSQL("CREATE TABLE IF NOT EXISTS WorldEcosystemInput (id TEXT NOT NULL PRIMARY KEY,tick INTEGER NOT NULL,kind TEXT NOT NULL,latitude REAL,longitude REAL,payload TEXT,createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldEcosystemInput_tick ON WorldEcosystemInput(tick)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_WorldEcosystemInput_createdAt ON WorldEcosystemInput(createdAt)")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                WorldInteractionSchema.create(db)
                WorldInteractionIntegrity.install(db)
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Guards also support the existing artificial version-restamp tests.
                if (!hasColumn(db, "WorldSpawn", "homeLatitude")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `homeLatitude` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("UPDATE `WorldSpawn` SET `homeLatitude` = `latitude`")
                }
                if (!hasColumn(db, "WorldSpawn", "homeLongitude")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `homeLongitude` REAL NOT NULL DEFAULT 0.0")
                    db.execSQL("UPDATE `WorldSpawn` SET `homeLongitude` = `longitude`")
                }
                if (!hasColumn(db, "WorldSpawn", "wanderTargetLatitude")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `wanderTargetLatitude` REAL")
                }
                if (!hasColumn(db, "WorldSpawn", "wanderTargetLongitude")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `wanderTargetLongitude` REAL")
                }
                if (!hasColumn(db, "WorldSpawn", "movementState")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `movementState` TEXT NOT NULL DEFAULT 'HOME'")
                }
                if (!hasColumn(db, "WorldSpawn", "anchorRadiusMeters")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `anchorRadiusMeters` REAL NOT NULL DEFAULT 25.0")
                }
                if (!hasColumn(db, "WorldSpawn", "denId")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `denId` TEXT")
                }
                if (!hasColumn(db, "WorldSpawn", "ecosystemEmotion")) {
                    db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `ecosystemEmotion` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WorldEcosystemSession` (
                        `id` TEXT NOT NULL,
                        `seed` INTEGER NOT NULL,
                        `rulesVersion` INTEGER NOT NULL,
                        `tickIndex` INTEGER NOT NULL,
                        `tickRemainderMillis` INTEGER NOT NULL,
                        `lastCheckpointAt` INTEGER NOT NULL,
                        `revision` INTEGER NOT NULL,
                        `regionLatitude` REAL,
                        `regionLongitude` REAL,
                        `pauseReason` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `Farm` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mapId` TEXT NOT NULL,
                        `mapVersion` INTEGER NOT NULL,
                        `capacity` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastSimulatedAt` INTEGER NOT NULL,
                        `randomSeed` INTEGER NOT NULL,
                        `cameraScale` REAL NOT NULL,
                        `cameraX` REAL NOT NULL,
                        `cameraY` REAL NOT NULL,
                        `autonomousDialogueEnabled` INTEGER NOT NULL,
                        `archivedAt` INTEGER,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmResident` (
                        `individualId` TEXT NOT NULL,
                        `farmId` TEXT NOT NULL,
                        `positionX` REAL NOT NULL,
                        `positionY` REAL NOT NULL,
                        `targetX` REAL NOT NULL,
                        `targetY` REAL NOT NULL,
                        `facingLeft` INTEGER NOT NULL,
                        `activity` TEXT NOT NULL,
                        `energy` INTEGER NOT NULL,
                        `satiety` INTEGER NOT NULL,
                        `social` INTEGER NOT NULL,
                        `funLevel` INTEGER NOT NULL,
                        `activityStartedAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`individualId`),
                        FOREIGN KEY(`farmId`) REFERENCES `Farm`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmResident_farmId` ON `FarmResident` (`farmId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_FarmResident_individualId` ON `FarmResident` (`individualId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmMessage` (
                        `id` TEXT NOT NULL,
                        `farmId` TEXT NOT NULL,
                        `sequence` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `authorIndividualId` TEXT,
                        `authorNameSnapshot` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `audience` TEXT NOT NULL,
                        `replyToMessageId` TEXT,
                        `sessionId` TEXT,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`farmId`) REFERENCES `Farm`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_FarmMessage_farmId_sequence` ON `FarmMessage` (`farmId`, `sequence`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmMessage_timestamp` ON `FarmMessage` (`timestamp`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmMessageRecipient` (
                        `messageId` TEXT NOT NULL,
                        `individualId` TEXT NOT NULL,
                        PRIMARY KEY(`messageId`, `individualId`),
                        FOREIGN KEY(`messageId`) REFERENCES `FarmMessage`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmMessageRecipient_messageId` ON `FarmMessageRecipient` (`messageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmMessageRecipient_individualId` ON `FarmMessageRecipient` (`individualId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmReadState` (
                        `farmId` TEXT NOT NULL,
                        `lastReadSequence` INTEGER NOT NULL,
                        PRIMARY KEY(`farmId`),
                        FOREIGN KEY(`farmId`) REFERENCES `Farm`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmReadState_farmId` ON `FarmReadState` (`farmId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmRelationship` (
                        `observerId` TEXT NOT NULL,
                        `otherId` TEXT NOT NULL,
                        `affinity` INTEGER NOT NULL,
                        `familiarity` INTEGER NOT NULL,
                        `lastInteractionAt` INTEGER NOT NULL,
                        PRIMARY KEY(`observerId`, `otherId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmRelationship_otherId` ON `FarmRelationship` (`otherId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `FarmMemory` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `observerId` TEXT NOT NULL,
                        `relatedIndividualId` TEXT,
                        `eventId` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `relevance` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmMemory_observerId` ON `FarmMemory` (`observerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_FarmMemory_relatedIndividualId` ON `FarmMemory` (`relatedIndividualId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_FarmMemory_eventId_observerId` ON `FarmMemory` (`eventId`, `observerId`)")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WildRelationship` (
                        `individualId` TEXT NOT NULL,
                        `cardCharacterId` INTEGER NOT NULL,
                        `speciesNameSnapshot` TEXT,
                        `trust` INTEGER NOT NULL,
                        `contactUnlockedAt` INTEGER,
                        `recruitmentState` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`individualId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WildRelationship_contactUnlockedAt` ON `WildRelationship` (`contactUnlockedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_WildRelationship_updatedAt` ON `WildRelationship` (`updatedAt`)")
                db.execSQL("""
                    INSERT OR IGNORE INTO WildRelationship(
                        individualId, cardCharacterId, speciesNameSnapshot, trust,
                        contactUnlockedAt, recruitmentState, createdAt, updatedAt
                    )
                    SELECT ws.individualId, ws.cardCharacterId, sp.speciesName, ws.mood,
                           CASE WHEN ws.mood > 75 THEN CAST(strftime('%s','now') AS INTEGER) * 1000 ELSE NULL END,
                           ws.recruitmentState, ws.spawnedAt, CAST(strftime('%s','now') AS INTEGER) * 1000
                    FROM WorldSpawn ws
                    LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = ws.cardCharacterId
                """.trimIndent())
                db.execSQL("UPDATE WorldSpawn SET isFollowing = 0, followLastLat = NULL, followLastLon = NULL")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                if (!hasColumn(db, "Farm", "cameraYaw")) {
                    db.execSQL("ALTER TABLE `Farm` ADD COLUMN `cameraYaw` REAL NOT NULL DEFAULT 35.0")
                }
                if (!hasColumn(db, "Farm", "cameraPitch")) {
                    db.execSQL("ALTER TABLE `Farm` ADD COLUMN `cameraPitch` REAL NOT NULL DEFAULT 35.0")
                }
                if (!hasColumn(db, "Farm", "cameraDistance")) {
                    db.execSQL("ALTER TABLE `Farm` ADD COLUMN `cameraDistance` REAL NOT NULL DEFAULT 3.4")
                }
                if (!hasColumn(db, "Farm", "cameraTargetX")) {
                    db.execSQL("ALTER TABLE `Farm` ADD COLUMN `cameraTargetX` REAL NOT NULL DEFAULT 0.0")
                }
                if (!hasColumn(db, "Farm", "cameraTargetZ")) {
                    db.execSQL("ALTER TABLE `Farm` ADD COLUMN `cameraTargetZ` REAL NOT NULL DEFAULT 0.0")
                }
                // Existing farms keep their residents/history but use the new
                // scene. Their old 2D camera values remain available for rollback.
                db.execSQL("UPDATE `Farm` SET `mapId` = 'digi_farm_3d', `mapVersion` = 1")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `Dex` (`id`, `discoveredOn`)
                    SELECT th.stageId, MIN(th.transformationDate)
                    FROM `TransformationHistory` th
                    JOIN `CardCharacter` cc ON cc.id = th.stageId
                    GROUP BY th.stageId
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `Dex` (`id`, `discoveredOn`)
                    SELECT
                        uc.charId,
                        COALESCE(
                            (SELECT MIN(th.transformationDate)
                             FROM `TransformationHistory` th
                             WHERE th.stageId = uc.charId),
                            CAST(strftime('%s', 'now') AS INTEGER) * 1000
                        )
                    FROM `UserCharacter` uc
                    JOIN `CardCharacter` cc ON cc.id = uc.charId
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `DigimonTechniqueLoadout` (
                        `individualId` TEXT NOT NULL,
                        `slot` INTEGER NOT NULL,
                        `techniqueId` TEXT NOT NULL,
                        PRIMARY KEY(`individualId`, `slot`),
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_DigimonTechniqueLoadout_individualId` " +
                        "ON `DigimonTechniqueLoadout` (`individualId`)"
                )
            }
        }

        /** Replaces the old temperament/social-style/speech-quirk record. */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `DigimonPersonalityTraits_new` (
                        `individualId` TEXT NOT NULL,
                        `personalityType` TEXT NOT NULL,
                        `generatedAt` INTEGER NOT NULL,
                        `systemVersion` INTEGER NOT NULL,
                        PRIMARY KEY(`individualId`),
                        FOREIGN KEY(`individualId`) REFERENCES `DigimonIndividual`(`individualId`)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `DigimonPersonalityTraits_new`
                        (`individualId`, `personalityType`, `generatedAt`, `systemVersion`)
                    SELECT `individualId`, 'FRIENDLY', `generatedAt`, 1
                    FROM `DigimonPersonalityTraits`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `DigimonPersonalityTraits`")
                db.execSQL("ALTER TABLE `DigimonPersonalityTraits_new` RENAME TO `DigimonPersonalityTraits`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_DigimonPersonalityTraits_individualId` " +
                        "ON `DigimonPersonalityTraits` (`individualId`)"
                )
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Idempotent: never fail when the column already exists. Real 18
                // databases always take the ADD COLUMN path; only artificial
                // version restamps (tests) hit the guard.
                if (!hasColumn(db, "UserCharacter", "isFavorite")) {
                    db.execSQL("ALTER TABLE `UserCharacter` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0")
                }
                db.execSQL("UPDATE `UserCharacter` SET `isFavorite` = 1 WHERE `isActive` = 1")
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

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE WatchTransfer ADD COLUMN sourceCharacterId INTEGER")
                db.execSQL("ALTER TABLE WatchTransfer ADD COLUMN cardId INTEGER")
                db.execSQL("ALTER TABLE WatchTransfer ADD COLUMN sourceFingerprint TEXT")
                db.execSQL("ALTER TABLE WatchTransfer ADD COLUMN deviceKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS WatchImportReceipt (
                        fingerprint TEXT NOT NULL PRIMARY KEY,
                        characterId INTEGER NOT NULL,
                        individualId TEXT NOT NULL
                    )
                """.trimIndent())
                IndividualIntegrity.install(db)
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `WatchTransfer` (
                        `token` TEXT NOT NULL PRIMARY KEY,
                        `individualId` TEXT NOT NULL,
                        `deviceType` INTEGER NOT NULL,
                        `dimId` INTEGER NOT NULL,
                        `generation` INTEGER,
                        `ageInDays` INTEGER NOT NULL,
                        `totalBattlesWon` INTEGER NOT NULL,
                        `totalBattlesLost` INTEGER NOT NULL,
                        `history` TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_WatchTransfer_individualId` ON `WatchTransfer` (`individualId`)")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `Card` ADD COLUMN `worldSpawnsEnabled` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Existing chat history was already available to the user before
                // unread tracking existed, so it must not surface as a new bubble.
                db.execSQL(
                    "ALTER TABLE `ChatMessageEntity` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

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

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `isFollowing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `followLastLat` REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE `WorldSpawn` ADD COLUMN `followLastLon` REAL DEFAULT NULL")
            }
        }

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
