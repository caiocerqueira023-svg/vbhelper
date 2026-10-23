import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.di.AppContainer
import com.github.nacabaro.vbhelper.source.CurrencyRepository
import com.github.nacabaro.vbhelper.source.DataStoreSecretsRepository
import com.github.nacabaro.vbhelper.source.LlmSettingsRepository
import com.github.nacabaro.vbhelper.source.SpeciesSettingsRepository
import com.github.nacabaro.vbhelper.companion.validation.ValidatedCardManager
import com.github.nacabaro.vbhelper.companion.logs.CompanionLogService
import com.github.nacabaro.vbhelper.source.SecretsSerializer
import com.github.nacabaro.vbhelper.source.proto.Secrets
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.chat.DigimonDiaryService
import com.github.nacabaro.vbhelper.chat.ReactionRepository
import com.github.nacabaro.vbhelper.chat.lorebook.LorebookRepository
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import com.github.nacabaro.vbhelper.world.WorldRepository
import com.github.nacabaro.vbhelper.digifarm.DigifarmRepository
import com.github.nacabaro.vbhelper.digifarm.FarmSessionCoordinator
import com.github.nacabaro.vbhelper.world.ecosystem.WorldEcosystemCoordinator
import com.github.nacabaro.vbhelper.world.ecosystem.RoomWorldEcosystemStore

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
            )
            // Missing migrations must preserve the database, never erase individuals/chats.
            .openHelperFactory(com.github.nacabaro.vbhelper.database.PreservingMigrationFactory())
            .addCallback(com.github.nacabaro.vbhelper.database.IndividualIntegrity.callback)
            .createFromAsset("items.db")
            .build().also { database ->
                database.expandedCatalog = com.github.nacabaro.vbhelper.species.ExpandedCatalogRepository(database,
                    { dotSpriteLibrary }, { spritePreferenceRepository.current })
                database.participation = com.github.nacabaro.vbhelper.species.PartnerParticipationGate(database,
                    { dotSpriteLibrary.catalogBlocking() })
            }
    }

    override val dataStoreSecretsRepository = DataStoreSecretsRepository(context.secretsStore)

    override val currencyRepository by lazy { CurrencyRepository(context.currencyStore, db) }
    override val debugSettingsRepository = com.github.nacabaro.vbhelper.source.DebugSettingsRepository(context)
    override val spritePreferenceRepository = com.github.nacabaro.vbhelper.source.SpritePreferenceRepository(context)
    val localSaveDocuments by lazy { com.github.nacabaro.vbhelper.source.gameSave.AndroidSaveDocuments(context) }
    val localCompanion by lazy { com.github.nacabaro.vbhelper.source.gameSave.LocalCompanionClient(db) }
    val saveLocations by lazy { com.github.nacabaro.vbhelper.source.gameSave.SaveLocationRepository(db, localSaveDocuments, localCompanion) }
    val linkedGameSaves: com.github.nacabaro.vbhelper.source.gameSave.LinkedGameSaveRepository by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.LinkedGameSaveRepository(db,
            com.github.nacabaro.vbhelper.source.gameSave.LinkedSaveDocuments(localSaveDocuments, localCompanion),
            beforeRefresh = { saveLocations.rescanAll { linkedGameSaves.link(it, discovered = true) } },
            beforeUnlink = { saveLocations.exclude(it) }, explicitLink = { saveLocations.include(it) },
            allowDiscovered = { !db.transferContinuityDao().isExcluded(it) },
            afterRefresh = { view -> latestSaveReconciler.reconcile(view) })
    }
    val saveImportPreviews by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.SaveImportPreviewRepository(db, linkedGameSaves)
    }
    val gameImportAcquisition by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.GameImportAcquisitionRepository(db, linkedGameSaves, saveImportPreviews)
    }
    val nativeMaterialization by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.NativeMaterializationRepository(db, gameImportAcquisition,
            { dotSpriteLibrary.catalog() }, { requireNotNull(db.expandedCatalog).definitionFor(it) },
            com.github.nacabaro.vbhelper.source.gameSave.WorldDsSpeciesTable.load(context))
    }
    val latestSaveReconciler by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.AutomaticSaveReconciler(db, nativeMaterialization)
    }
    val individualContinuity by lazy { com.github.nacabaro.vbhelper.source.gameSave.IndividualContinuityRepository(db) }
    val destinationPreviews by lazy { com.github.nacabaro.vbhelper.source.gameSave.DestinationPreviewRepository(db, linkedGameSaves) }
    val nativeSpeciesPresentation by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.NativeSpeciesPresentationRepository(db, dotSpriteLibrary,
            com.github.nacabaro.vbhelper.source.gameSave.WorldDsSpeciesTable.load(context))
    }
    val storageAway by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.StorageAwayRepository(db, linkedGameSaves, nativeSpeciesPresentation)
    }
    val experimentalGameMoves by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.ExperimentalWorldDsMoveRepository(db, linkedGameSaves,
            com.github.nacabaro.vbhelper.source.gameSave.AndroidSaveDocumentWrite(context),
            java.io.File(context.filesDir, "experimental-game-moves"), nativeMaterialization,
            nativeSpeciesPresentation)
    }
    val experimentalGameSends by lazy {
        com.github.nacabaro.vbhelper.source.gameSave.ExperimentalWorldDsSendRepository(db, linkedGameSaves,
            com.github.nacabaro.vbhelper.source.gameSave.AndroidSaveDocumentWrite(context),
            java.io.File(context.filesDir, "experimental-game-sends"),
            com.github.nacabaro.vbhelper.source.gameSave.WorldDsSpeciesTable.load(context),
            { dotSpriteLibrary.catalog() })
    }
    override val questRepository by lazy { com.github.nacabaro.vbhelper.quests.QuestRepository(db, currencyRepository,
        allowAnyDistance = { debugSettingsRepository.allowAnyDistance }) }

    override val validatedCardManager by lazy {
        ValidatedCardManager(db.validatedCardDao())
    }

    override val companionLogService = CompanionLogService()

    override val llmSettingsRepository = LlmSettingsRepository(context.llmSettingsStore,
        debugEnabled = { debugSettingsRepository.enabled })

    override val speciesSettingsRepository = SpeciesSettingsRepository(context.speciesSettingsStore)

    override val dotSpriteLibrary by lazy {
        com.github.nacabaro.vbhelper.species.BundledDotSpriteLibrary(context, db)
    }

    override val speciesRepository: com.github.nacabaro.vbhelper.species.SpeciesRepository by lazy {
        com.github.nacabaro.vbhelper.species.SpeciesRepository(
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
            spriteMatcher = com.github.nacabaro.vbhelper.species.bundledOfficialSpriteMatcher(context.assets),
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
        ChatRepository(db, llmSettingsRepository, lorebookRepository, speciesRepository, questRepository = questRepository)
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
    override val worldInteractionOrchestrator by lazy { com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionOrchestrator(db,chatRepository,
        allowAnyDistance = { debugSettingsRepository.allowAnyDistance }) }
    override val worldEcosystemCoordinator by lazy {
        WorldEcosystemCoordinator(RoomWorldEcosystemStore(db,interactions=worldInteractionOrchestrator),
            allowAnyDistance = { debugSettingsRepository.allowAnyDistance }).also { coordinator ->
            worldInteractionOrchestrator.commitInput={ action -> coordinator.commitExternalInput(action) }
        }
    }
    override val reactionRepository by lazy { ReactionRepository(db, chatRepository) }
    override val diaryService by lazy { DigimonDiaryService(db, chatRepository) }
}
