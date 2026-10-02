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
                AppDatabase.MIGRATION_29_30
            )
            // Missing migrations must preserve the database, never erase individuals/chats.
            .addCallback(com.github.nacabaro.vbhelper.database.IndividualIntegrity.callback)
            .createFromAsset("items.db")
            .build()
    }

    override val dataStoreSecretsRepository = DataStoreSecretsRepository(context.secretsStore)

    override val currencyRepository = CurrencyRepository(context.currencyStore)

    override val validatedCardManager by lazy {
        ValidatedCardManager(db.validatedCardDao())
    }

    override val companionLogService = CompanionLogService()

    override val llmSettingsRepository = LlmSettingsRepository(context.llmSettingsStore)

    override val speciesSettingsRepository = SpeciesSettingsRepository(context.speciesSettingsStore)

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
            }
        )
    }

    override val lorebookRepository by lazy {
        LorebookRepository(
            lorebookEntryDao = db.lorebookEntryDao(),
            speciesRepository = speciesRepository
        )
    }

    override val chatRepository by lazy {
        ChatRepository(db, llmSettingsRepository, lorebookRepository, speciesRepository)
    }
    override val worldRepository by lazy { WorldRepository(db) }
    override val digifarmRepository by lazy { DigifarmRepository(db) }
    override val farmSessionCoordinator by lazy { FarmSessionCoordinator(digifarmRepository) }
    override val worldInteractionOrchestrator by lazy { com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionOrchestrator(db,chatRepository) }
    override val worldEcosystemCoordinator by lazy {
        WorldEcosystemCoordinator(RoomWorldEcosystemStore(db,interactions=worldInteractionOrchestrator)).also { coordinator ->
            worldInteractionOrchestrator.commitInput={ action -> coordinator.commitExternalInput(action) }
        }
    }
    override val reactionRepository by lazy { ReactionRepository(db, chatRepository) }
    override val diaryService by lazy { DigimonDiaryService(db, chatRepository) }
}
