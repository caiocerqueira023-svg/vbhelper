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
    }

    val apiKey: Flow<String?> = dataStore.data.map { it[API_KEY] }
    val model: Flow<String> = dataStore.data.map { it[MODEL] ?: "openrouter/auto" }

    suspend fun setApiKey(key: String) {
        dataStore.edit { it[API_KEY] = key }
    }

    suspend fun setModel(model: String) {
        dataStore.edit { it[MODEL] = model }
    }
}