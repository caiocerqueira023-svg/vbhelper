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
                AppDatabase.MIGRATION_11_12
            )
            // Escolha mais segura: se não houver caminho de migração explícito
            // (ex.: usuário vindo de uma versão sem migration mapeada), o Room
            // recria o banco em vez de travar o app com uma exceção de migração.
            .fallbackToDestructiveMigration()
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

    override val lorebookRepository by lazy {
        LorebookRepository(
            lorebookEntryDao = db.lorebookEntryDao(),
            speciesRepository = SpeciesRepository(db, speciesSettingsRepository)
        )
    }

    override val chatRepository by lazy {
        ChatRepository(db, llmSettingsRepository, lorebookRepository)
    }
    override val worldRepository by lazy { WorldRepository(db) }
    override val reactionRepository by lazy { ReactionRepository(db, chatRepository) }
    override val diaryService by lazy { DigimonDiaryService(db, chatRepository) }
}
