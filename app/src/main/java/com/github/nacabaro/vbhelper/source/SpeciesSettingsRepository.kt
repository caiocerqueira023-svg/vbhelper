package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SpeciesSettingsRepository(private val dataStore: DataStore<Preferences>) {
    private companion object {
        val SPECIES_DATABASE_JSON = stringPreferencesKey("species_db_json_cache")
        val SPECIES_DATABASE_VERSION = intPreferencesKey("species_db_version")
        val PROMPT_ORIGIN_AT_IMPORT_TIME = booleanPreferencesKey("species_prompt_at_import_time")
    }

    val cachedDatabaseJson: Flow<String?> = dataStore.data.map { it[SPECIES_DATABASE_JSON] }
    val cachedDatabaseVersion: Flow<Int> = dataStore.data.map { it[SPECIES_DATABASE_VERSION] ?: -1 }
    val promptOriginAtImportTime: Flow<Boolean> = dataStore.data.map {
        it[PROMPT_ORIGIN_AT_IMPORT_TIME] ?: false
    }

    suspend fun setPromptOriginAtImportTime(value: Boolean) {
        dataStore.edit { it[PROMPT_ORIGIN_AT_IMPORT_TIME] = value }
    }

    suspend fun cacheDatabase(json: String, version: Int) {
        dataStore.edit {
            it[SPECIES_DATABASE_JSON] = json
            it[SPECIES_DATABASE_VERSION] = version
        }
    }
}
