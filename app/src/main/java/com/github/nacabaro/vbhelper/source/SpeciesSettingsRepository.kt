package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class PendingCardOriginPrompt(
    val cardId: Long,
    val cardName: String,
    val isImporting: Boolean = false,
    val selectedStatus: OfficialStatus? = null
)

class SpeciesSettingsRepository(private val dataStore: DataStore<Preferences>) {
    private companion object {
        val SPECIES_DATABASE_JSON = stringPreferencesKey("species_db_json_cache")
        val SPECIES_DATABASE_VERSION = intPreferencesKey("species_db_version")
        val PROMPT_ORIGIN_AT_IMPORT_TIME = booleanPreferencesKey("species_prompt_at_import_time")
        val PENDING_CARD_ORIGIN_PROMPTS = stringPreferencesKey("species_pending_card_origin_prompts")
        // Read legacy single-prompt values once so an upgrade does not lose a pending choice.
        val PENDING_CARD_ORIGIN_ID = longPreferencesKey("species_pending_card_origin_id")
        val PENDING_CARD_ORIGIN_NAME = stringPreferencesKey("species_pending_card_origin_name")
        val PENDING_CARD_ORIGIN_IMPORTING = booleanPreferencesKey("species_pending_card_origin_importing")
    }

    private val gson = Gson()

    val cachedDatabaseJson: Flow<String?> = dataStore.data.map { it[SPECIES_DATABASE_JSON] }
    val cachedDatabaseVersion: Flow<Int> = dataStore.data.map { it[SPECIES_DATABASE_VERSION] ?: -1 }
    val promptOriginAtImportTime: Flow<Boolean> = dataStore.data.map {
        it[PROMPT_ORIGIN_AT_IMPORT_TIME] ?: false
    }
    val pendingCardOriginPrompts: Flow<List<PendingCardOriginPrompt>> = dataStore.data.map(::readPendingPrompts)
    val pendingCardOriginPrompt: Flow<PendingCardOriginPrompt?> = pendingCardOriginPrompts.map { it.firstOrNull() }

    suspend fun setPromptOriginAtImportTime(value: Boolean) {
        dataStore.edit { it[PROMPT_ORIGIN_AT_IMPORT_TIME] = value }
    }

    suspend fun setPendingCardOriginPrompt(
        cardId: Long,
        cardName: String,
        isImporting: Boolean = false
    ) {
        updatePendingPrompts { prompts ->
            val prompt = PendingCardOriginPrompt(cardId, cardName, isImporting)
            val existingIndex = prompts.indexOfFirst { it.cardId == cardId }
            if (existingIndex == -1) prompts + prompt else prompts.toMutableList().apply {
                this[existingIndex] = prompt
            }
        }
    }

    suspend fun finishPendingCardOriginImport(cardId: Long): OfficialStatus? {
        var selectedStatus: OfficialStatus? = null
        updatePendingPrompts { prompts ->
            val prompt = prompts.firstOrNull { it.cardId == cardId }
            if (prompt?.selectedStatus != null) {
                selectedStatus = prompt.selectedStatus
                prompts.filterNot { it.cardId == cardId }
            } else {
                prompts.map { item ->
                    if (item.cardId == cardId) item.copy(isImporting = false) else item
                }
            }
        }
        return selectedStatus
    }

    suspend fun recoverInterruptedCardOriginImports() {
        updatePendingPrompts { prompts ->
            prompts.map { prompt ->
                if (prompt.isImporting) prompt.copy(isImporting = false, selectedStatus = null) else prompt
            }
        }
    }

    suspend fun selectPendingCardOrigin(cardId: Long, status: OfficialStatus): Boolean? {
        var isImporting: Boolean? = null
        updatePendingPrompts { prompts ->
            val prompt = prompts.firstOrNull { it.cardId == cardId } ?: return@updatePendingPrompts prompts
            isImporting = prompt.isImporting
            if (prompt.isImporting) {
                prompts.map { item ->
                    if (item.cardId == cardId) item.copy(selectedStatus = status) else item
                }
            } else {
                prompts.filterNot { it.cardId == cardId }
            }
        }
        return isImporting
    }

    suspend fun clearPendingCardOriginPrompt(cardId: Long) {
        updatePendingPrompts { prompts -> prompts.filterNot { it.cardId == cardId } }
    }

    private suspend fun updatePendingPrompts(
        update: (List<PendingCardOriginPrompt>) -> List<PendingCardOriginPrompt>
    ) {
        dataStore.edit { preferences ->
            val updated = update(readPendingPrompts(preferences))
            if (updated.isEmpty()) {
                preferences.remove(PENDING_CARD_ORIGIN_PROMPTS)
            } else {
                preferences[PENDING_CARD_ORIGIN_PROMPTS] = gson.toJson(updated)
            }
            preferences.remove(PENDING_CARD_ORIGIN_ID)
            preferences.remove(PENDING_CARD_ORIGIN_NAME)
            preferences.remove(PENDING_CARD_ORIGIN_IMPORTING)
        }
    }

    private fun readPendingPrompts(preferences: Preferences): List<PendingCardOriginPrompt> {
        preferences[PENDING_CARD_ORIGIN_PROMPTS]?.let { encoded ->
            return runCatching {
                gson.fromJson<List<PendingCardOriginPrompt>>(
                    encoded,
                    object : TypeToken<List<PendingCardOriginPrompt>>() {}.type
                ).orEmpty()
            }.getOrDefault(emptyList())
        }
        val cardId = preferences[PENDING_CARD_ORIGIN_ID] ?: return emptyList()
        val cardName = preferences[PENDING_CARD_ORIGIN_NAME] ?: return emptyList()
        return listOf(
            PendingCardOriginPrompt(
                cardId = cardId,
                cardName = cardName,
                isImporting = preferences[PENDING_CARD_ORIGIN_IMPORTING] ?: false
            )
        )
    }

    suspend fun cacheDatabase(json: String, version: Int) {
        dataStore.edit {
            it[SPECIES_DATABASE_JSON] = json
            it[SPECIES_DATABASE_VERSION] = version
        }
    }
}
