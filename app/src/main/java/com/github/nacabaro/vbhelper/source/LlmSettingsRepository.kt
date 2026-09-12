package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.github.nacabaro.vbhelper.chat.ChatApiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LlmProviderSettings(
    val apiKey: String? = null,
    val model: String = "openrouter/auto",
    val baseUrl: String = ""
)

class LlmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        const val DEFAULT_CHAT_COMPLETIONS_BASE_URL = "https://openrouter.ai/api/v1/"
        val API_KEY = stringPreferencesKey("openrouter_api_key")
        val MODEL = stringPreferencesKey("openrouter_model")
        val CHAT_COMPLETIONS_BASE_URL = stringPreferencesKey("chat_completions_base_url")
        val SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("openrouter_system_prompt_template")
        val WILD_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("openrouter_wild_system_prompt_template")
        val TAMER_NAME = stringPreferencesKey("tamer_name")
        val ACTIVE_PROVIDER = stringPreferencesKey("active_chat_api_provider")
    }

    val activeProvider: Flow<ChatApiProvider> = dataStore.data.map(::activeProvider)
    val providerSettings: Flow<Map<ChatApiProvider, LlmProviderSettings>> = dataStore.data.map { preferences ->
        ChatApiProvider.entries.associateWith { provider -> providerSettings(preferences, provider) }
    }
    private val activeSettings: Flow<LlmProviderSettings> = dataStore.data.map { preferences ->
        providerSettings(preferences, activeProvider(preferences))
    }
    val apiKey: Flow<String?> = activeSettings.map { it.apiKey }
    val model: Flow<String> = activeSettings.map { it.model }
    val chatCompletionsBaseUrl: Flow<String> = activeSettings.map { it.baseUrl }
    val systemPromptTemplate: Flow<String?> = dataStore.data.map { it[SYSTEM_PROMPT_TEMPLATE] }
    val wildSystemPromptTemplate: Flow<String?> = dataStore.data.map { it[WILD_SYSTEM_PROMPT_TEMPLATE] }
    val tamerName: Flow<String> = dataStore.data.map { it[TAMER_NAME] ?: "" }

    suspend fun setApiKey(key: String) {
        dataStore.edit { preferences ->
            val provider = activeProvider(preferences)
            preferences[apiKeyKey(provider)] = key
            preferences[API_KEY] = key
        }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { preferences ->
            val provider = activeProvider(preferences)
            preferences[modelKey(provider)] = model
            preferences[MODEL] = model
        }
    }

    suspend fun setChatCompletionsBaseUrl(baseUrl: String) {
        dataStore.edit { preferences ->
            val normalizedUrl = baseUrl.trim().ensureTrailingSlash()
            val provider = activeProvider(preferences)
            preferences[baseUrlKey(provider)] = normalizedUrl
            preferences[CHAT_COMPLETIONS_BASE_URL] = normalizedUrl
        }
    }

    suspend fun saveProviderSettings(
        provider: ChatApiProvider,
        apiKey: String,
        model: String,
        baseUrl: String
    ) {
        val normalizedUrl = baseUrl.trim().ensureTrailingSlash()
        dataStore.edit { preferences ->
            preferences[ACTIVE_PROVIDER] = provider.name
            preferences[apiKeyKey(provider)] = apiKey.trim()
            preferences[modelKey(provider)] = model.trim().ifBlank { provider.suggestedModel ?: "openrouter/auto" }
            preferences[baseUrlKey(provider)] = normalizedUrl

            // Keep legacy values in sync so existing installations retain their active setup.
            preferences[API_KEY] = apiKey.trim()
            preferences[MODEL] = model.trim().ifBlank { provider.suggestedModel ?: "openrouter/auto" }
            preferences[CHAT_COMPLETIONS_BASE_URL] = normalizedUrl
        }
    }

    suspend fun setSystemPromptTemplate(template: String?) {
        dataStore.edit { preferences ->
            if (template.isNullOrBlank()) preferences.remove(SYSTEM_PROMPT_TEMPLATE)
            else preferences[SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    suspend fun setWildSystemPromptTemplate(template: String?) {
        dataStore.edit { preferences ->
            if (template.isNullOrBlank()) preferences.remove(WILD_SYSTEM_PROMPT_TEMPLATE)
            else preferences[WILD_SYSTEM_PROMPT_TEMPLATE] = template
        }
    }

    suspend fun setTamerName(name: String) {
        dataStore.edit { preferences ->
            if (name.isBlank()) preferences.remove(TAMER_NAME)
            else preferences[TAMER_NAME] = name.trim()
        }
    }

    private fun String.ensureTrailingSlash() = if (endsWith('/')) this else "$this/"

    private fun activeProvider(preferences: Preferences): ChatApiProvider =
        preferences[ACTIVE_PROVIDER]
            ?.let { name -> ChatApiProvider.entries.firstOrNull { it.name == name } }
            ?: ChatApiProvider.fromBaseUrl(
                preferences[CHAT_COMPLETIONS_BASE_URL] ?: DEFAULT_CHAT_COMPLETIONS_BASE_URL
            )

    private fun providerSettings(
        preferences: Preferences,
        provider: ChatApiProvider
    ): LlmProviderSettings {
        val isLegacyActiveProvider = preferences[ACTIVE_PROVIDER] == null &&
            provider == activeProvider(preferences)
        val defaultUrl = provider.baseUrl ?: ""
        val savedApiKey = preferences[apiKeyKey(provider)]
            ?: if (isLegacyActiveProvider) preferences[API_KEY] else null
        val savedModel = preferences[modelKey(provider)]
            ?: if (isLegacyActiveProvider) preferences[MODEL] else null
        val savedBaseUrl = preferences[baseUrlKey(provider)]
            ?: if (isLegacyActiveProvider) preferences[CHAT_COMPLETIONS_BASE_URL] else null
        return LlmProviderSettings(
            apiKey = savedApiKey,
            model = savedModel ?: provider.suggestedModel ?: "openrouter/auto",
            baseUrl = savedBaseUrl ?: defaultUrl
        )
    }

    private fun apiKeyKey(provider: ChatApiProvider) =
        stringPreferencesKey("chat_api_${provider.name.lowercase()}_key")

    private fun modelKey(provider: ChatApiProvider) =
        stringPreferencesKey("chat_api_${provider.name.lowercase()}_model")

    private fun baseUrlKey(provider: ChatApiProvider) =
        stringPreferencesKey("chat_api_${provider.name.lowercase()}_base_url")

}
