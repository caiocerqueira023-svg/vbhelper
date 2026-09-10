package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LlmSettingsRepository(
    private val dataStore: DataStore<Preferences>
) {
    private companion object {
        val API_KEY = stringPreferencesKey("openrouter_api_key")
        val MODEL = stringPreferencesKey("openrouter_model")
        val SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("openrouter_system_prompt_template")
        val WILD_SYSTEM_PROMPT_TEMPLATE = stringPreferencesKey("openrouter_wild_system_prompt_template")
        val TAMER_NAME = stringPreferencesKey("tamer_name")
    }

    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }
    val model: Flow<String> = dataStore.data.map { it[MODEL] ?: "openrouter/auto" }
    val systemPromptTemplate: Flow<String?> = dataStore.data.map { it[SYSTEM_PROMPT_TEMPLATE] }
    val wildSystemPromptTemplate: Flow<String?> = dataStore.data.map { it[WILD_SYSTEM_PROMPT_TEMPLATE] }
    val tamerName: Flow<String> = dataStore.data.map { it[TAMER_NAME] ?: "" }

    suspend fun setApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[MODEL] = model }
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
}
