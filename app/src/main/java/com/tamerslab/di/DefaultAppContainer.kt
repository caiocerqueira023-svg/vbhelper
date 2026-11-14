package com.tamerslab.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.di.AppContainer
import com.tamerslab.items.data.CurrencyRepository
import com.tamerslab.security.DataStoreSecretsRepository
import com.tamerslab.settings.data.LlmSettingsRepository
import com.tamerslab.settings.data.SpeciesSettingsRepository
import com.tamerslab.companion.validation.ValidatedCardManager
import com.tamerslab.companion.logs.CompanionLogService
import com.tamerslab.security.SecretsSerializer
import com.tamerslab.security.proto.Secrets
import com.tamerslab.chat.ChatRepository
import com.tamerslab.chat.DigimonDiaryService
import com.tamerslab.chat.ReactionRepository
import com.tamerslab.chat.lorebook.LorebookRepository
import com.tamerslab.species.SpeciesRepository
import com.tamerslab.world.WorldRepository
import com.tamerslab.farm.DigifarmRepository
import com.tamerslab.farm.FarmSessionCoordinator
import com.tamerslab.world.ecosystem.WorldEcosystemCoordinator
import com.tamerslab.world.ecosystem.RoomWorldEcosystemStore

private const val SECRETS_DATA_STORE_NAME = "secrets.pb"
private const val USER_PREFERENCES_NAME = "user_preferences"
private const val LLM_SETTINGS_STORE_NAME = "llm_settings"
private const val SPECIES_SETTINGS_STORE_NAME = "species_settings"

val Context.secretsStore: DataStore<Secrets> by dataStore(
    fileName = SECRETS_DATA_STORE_NAME,
    serializer = SecretsSerializer
)

val Context.currencyStore: DataStore<Preferences> by preferencesDataStore(
    name = USER_PREFERENCES_NAME
)

val Context.llmSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = LLM_SETTINGS_STORE_NAME
)

val Context.speciesSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = SPECIES_SETTINGS_STORE_NAME
)

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val db: AppDatabase by lazy {
        Room.databaseBuilder(
            context = context,
            klass = AppDatabase::class.java,
            "internalDb"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41,
                AppDatabase.MIGRATION_41_42,
                AppDatabase.MIGRATION_42_43,
                AppDatabase.MIGRATION_43_44,
                AppDatabase.MIGRATION_44_45,
                AppDatabase.MIGRATION_45_46,
                AppDatabase.MIGRATION_46_47,
                AppDatabase.MIGRATION_47_48,
                AppDatabase.MIGRATION_48_49,
                AppDatabase.MIGRATION_49_50,
                AppDatabase.MIGRATION_50_51,
                AppDatabase.MIGRATION_51_52,
                AppDatabase.MIGRATION_52_53,
                AppDatabase.MIGRATION_53_54,
                AppDatabase.MIGRATION_54_55,
                AppDatabase.MIGRATION_55_56,
            )
            // Missing migrations must preserve the database, never erase individuals/chats.
            .openHelperFactory(com.tamerslab.data.database.PreservingMigrationFactory())
            .addCallback(com.tamerslab.data.database.IndividualIntegrity.callback)
            .createFromAsset("items.db")
            .build().also { database ->
                database.expandedCatalog = com.tamerslab.species.ExpandedCatalogRepository(database,
                    { dotSpriteLibrary }, { spritePreferenceRepository.current })
                database.participation = com.tamerslab.species.PartnerParticipationGate(database,
                    { dotSpriteLibrary.catalogBlocking() })
            }
    }

    override val dataStoreSecretsRepository = DataStoreSecretsRepository(context.secretsStore)

    override val currencyRepository by lazy { CurrencyRepository(context.currencyStore, db) }
    override val debugSettingsRepository = com.tamerslab.settings.data.DebugSettingsRepository(context)
    override val spritePreferenceRepository = com.tamerslab.settings.data.SpritePreferenceRepository(context)
    val localSaveDocuments by lazy { com.tamerslab.transfer.data.gameSave.AndroidSaveDocuments(context) }
    val localCompanion by lazy { com.tamerslab.transfer.data.gameSave.LocalCompanionClient(db) }
    val saveLocations by lazy { com.tamerslab.transfer.data.gameSave.SaveLocationRepository(db, localSaveDocuments, localCompanion) }
    val linkedGameSaves: com.tamerslab.transfer.data.gameSave.LinkedGameSaveRepository by lazy {
        com.tamerslab.transfer.data.gameSave.LinkedGameSaveRepository(db,
            com.tamerslab.transfer.data.gameSave.LinkedSaveDocuments(localSaveDocuments, localCompanion),
            beforeRefresh = { saveLocations.rescanAll { linkedGameSaves.link(it, discovered = true) } },
            beforeUnlink = { saveLocations.exclude(it) }, explicitLink = { saveLocations.include(it) },
            allowDiscovered = { !db.transferContinuityDao().isExcluded(it) },
            afterRefresh = { view -> latestSaveReconciler.reconcile(view) })
    }
    val saveImportPreviews by lazy {
        com.tamerslab.transfer.data.gameSave.SaveImportPreviewRepository(db, linkedGameSaves)
    }
    val gameImportAcquisition by lazy {
        com.tamerslab.transfer.data.gameSave.GameImportAcquisitionRepository(db, linkedGameSaves, saveImportPreviews)
    }
    val nativeMaterialization by lazy {
        com.tamerslab.transfer.data.gameSave.NativeMaterializationRepository(db, gameImportAcquisition,
            { dotSpriteLibrary.catalog() }, { requireNotNull(db.expandedCatalog).definitionFor(it) },
            com.tamerslab.transfer.data.gameSave.WorldDsSpeciesTable.load(context))
    }
    val latestSaveReconciler by lazy {
        com.tamerslab.transfer.data.gameSave.AutomaticSaveReconciler(db, nativeMaterialization)
    }
    val individualContinuity by lazy { com.tamerslab.transfer.data.gameSave.IndividualContinuityRepository(db) }
    val destinationPreviews by lazy { com.tamerslab.transfer.data.gameSave.DestinationPreviewRepository(db, linkedGameSaves) }
    val nativeSpeciesPresentation by lazy {
        com.tamerslab.transfer.data.gameSave.NativeSpeciesPresentationRepository(db, dotSpriteLibrary,
            com.tamerslab.transfer.data.gameSave.WorldDsSpeciesTable.load(context))
    }
    val storageAway by lazy {
        com.tamerslab.transfer.data.gameSave.StorageAwayRepository(db, linkedGameSaves, nativeSpeciesPresentation)
    }
    val experimentalGameMoves by lazy {
        com.tamerslab.transfer.data.gameSave.ExperimentalWorldDsMoveRepository(db, linkedGameSaves,
            com.tamerslab.transfer.data.gameSave.AndroidSaveDocumentWrite(context),
            java.io.File(context.filesDir, "experimental-game-moves"), nativeMaterialization,
            nativeSpeciesPresentation)
    }
    val experimentalGameSends by lazy {
        com.tamerslab.transfer.data.gameSave.ExperimentalWorldDsSendRepository(db, linkedGameSaves,
            com.tamerslab.transfer.data.gameSave.AndroidSaveDocumentWrite(context),
            java.io.File(context.filesDir, "experimental-game-sends"),
            com.tamerslab.transfer.data.gameSave.WorldDsSpeciesTable.load(context),
            { dotSpriteLibrary.catalog() })
    }
    val cardRepository by lazy { com.tamerslab.species.data.CardRepository(db) }
    val spriteRepository by lazy { com.tamerslab.species.data.SpriteRepository(db) }
    val storageRepository by lazy { com.tamerslab.storage.data.StorageRepository(db) }
    val gameplayRepository by lazy { com.tamerslab.items.data.GameplayRepository(db, currencyRepository) }

    override val questRepository by lazy { com.tamerslab.quests.QuestRepository(db, currencyRepository,
        allowAnyDistance = { debugSettingsRepository.allowAnyDistance }) }

    override val validatedCardManager by lazy {
        ValidatedCardManager(db.validatedCardDao())
    }

    override val companionLogService = CompanionLogService()

    override val llmSettingsRepository = LlmSettingsRepository(context.llmSettingsStore,
        debugEnabled = { debugSettingsRepository.enabled })

    override val speciesSettingsRepository = SpeciesSettingsRepository(context.speciesSettingsStore)

    override val dotSpriteLibrary by lazy {
        com.tamerslab.species.BundledDotSpriteLibrary(context, db)
    }

    override val speciesRepository: com.tamerslab.species.SpeciesRepository by lazy {
        com.tamerslab.species.SpeciesRepository(
            database = db,
            settingsRepository = speciesSettingsRepository,
            assetLoader = {
                runCatching {
                    context.assets.open("species.json").bufferedReader().use { it.readText() }
                }.getOrNull()
            },
            conversationExamplesLoader = {
                runCatching {
                    context.assets.open("species_chat_examples.json").bufferedReader().use { it.readText() }
                }.getOrNull()
            },
            spriteMatcher = com.tamerslab.species.bundledOfficialSpriteMatcher(context.assets),
            debugEnabled = { debugSettingsRepository.enabled },
            canonicalCatalogLoader = { dotSpriteLibrary.catalog() }
        )
    }

    override val lorebookRepository by lazy {
        LorebookRepository(
            lorebookEntryDao = db.lorebookEntryDao(),
            speciesRepository = speciesRepository
        )
    }

    override val chatRepository by lazy {
        ChatRepository(db, llmSettingsRepository, lorebookRepository, speciesRepository,
            questRepository = questRepository,
            legacyLlmEnabled = { debugSettingsRepository.legacyLlmEnabled })
    }
    override val worldRepository by lazy { WorldRepository(db,
        scanPercentagePerDefeat = { debugSettingsRepository.state.value.percentagePerDefeat },
        debugEnabled = { debugSettingsRepository.enabled },
        allowAnyDistance = { debugSettingsRepository.allowAnyDistance }) }
    override val digifarmRepository by lazy { DigifarmRepository(db) }
    override val farmConversationOrchestrator by lazy { digifarmRepository.conversationOwner(chatRepository) }
    override val farmSessionCoordinator by lazy { FarmSessionCoordinator(digifarmRepository).also {
        it.onSuspend = { farmId -> farmConversationOrchestrator.suspendFarm(farmId) }
    } }
    override val worldInteractionOrchestrator by lazy { com.tamerslab.world.ecosystem.WorldInteractionOrchestrator(db,chatRepository,
        allowAnyDistance = { debugSettingsRepository.allowAnyDistance }) }
    override val worldEcosystemCoordinator by lazy {
        WorldEcosystemCoordinator(RoomWorldEcosystemStore(db,interactions=worldInteractionOrchestrator),
            allowAnyDistance = { debugSettingsRepository.allowAnyDistance }).also { coordinator ->
            worldInteractionOrchestrator.commitInput={ action -> coordinator.commitExternalInput(action) }
        }
    }
    override val reactionRepository by lazy { ReactionRepository(db, chatRepository) }
    override val digilineContentProvider by lazy {
        com.tamerslab.digiline.content.DigilineContentProvider(
            com.tamerslab.digiline.content.DigilineContentLoader.fromAssets(context.assets)
        )
    }
    override val ownedDigilineRepository by lazy {
        com.tamerslab.digiline.data.OwnedDigilineRepository(db, digilineContentProvider, speciesRepository)
    }
    private val wildDigilineRepository by lazy {
        com.tamerslab.digiline.data.WildDigilineRepository(
            db, digilineContentProvider, speciesRepository, currencyRepository, questRepository
        )
    }
    override val digilineGateway by lazy {
        com.tamerslab.digiline.gateway.ScriptedDigilineGateway(
            db, ownedDigilineRepository, wildDigilineRepository
        )
    }
    override val diaryService by lazy { DigimonDiaryService(db, chatRepository, digilineContentProvider) }
}
