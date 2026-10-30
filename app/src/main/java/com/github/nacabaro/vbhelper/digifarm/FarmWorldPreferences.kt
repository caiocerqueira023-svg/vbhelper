package com.github.nacabaro.vbhelper.digifarm

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Presentation-only preferences for the always-mounted farm surface (PRESENTATION_REDESIGN_PLAN §7).
 *
 * These live in their own Preferences DataStore on purpose: the product's Room upgrades must keep
 * preserving collections, so the farm-home selection and the camera follow target never become new
 * `Farm` columns. The existing `Farm.cameraYaw/Pitch/Distance/TargetX/TargetZ` columns still hold the
 * camera itself — this file only remembers *which* farm and *who* the camera follows.
 */
private const val FARM_WORLD_DATA_STORE = "farm_world_preferences"

val Context.farmWorldStore: DataStore<Preferences> by preferencesDataStore(name = FARM_WORLD_DATA_STORE)

/** Selected farm + follow target; every value may be absent on a fresh install. */
data class FarmWorldPreferencesSnapshot(
    val selectedFarmId: String? = null,
    val followedIndividualId: String? = null,
    /** Used to validate that the remembered follow target still lives on the selected farm. */
    val followedCharacterId: Long? = null,
    /** How many residents the diorama may show (owner setting). */
    val residentLimit: Int = FARM_MAX_RESIDENTS,
    /** Which artwork the diorama is allowed to draw (owner setting). */
    val spriteFilter: FarmSpriteFilter = FarmSpriteFilter.MIXED,
)

class FarmWorldPreferences(private val store: DataStore<Preferences>) {

    val snapshot: Flow<FarmWorldPreferencesSnapshot> = store.data
        .map { preferences ->
            FarmWorldPreferencesSnapshot(
                selectedFarmId = preferences[KEY_SELECTED_FARM]?.takeIf { it.isNotBlank() },
                followedIndividualId = preferences[KEY_FOLLOW_INDIVIDUAL]?.takeIf { it.isNotBlank() },
                followedCharacterId = preferences[KEY_FOLLOW_CHARACTER]?.takeIf { it > 0L },
                residentLimit = preferences[KEY_RESIDENT_LIMIT]
                    ?.coerceIn(FARM_MIN_RESIDENTS, FARM_MAX_RESIDENTS)
                    ?: FARM_MAX_RESIDENTS,
                spriteFilter = FarmSpriteFilter.from(preferences[KEY_SPRITE_FILTER]),
            )
        }
        .distinctUntilChanged()

    suspend fun setSelectedFarm(farmId: String?) {
        store.edit { preferences ->
            if (farmId.isNullOrBlank()) preferences.remove(KEY_SELECTED_FARM)
            else preferences[KEY_SELECTED_FARM] = farmId
        }
    }

    suspend fun setResidentLimit(limit: Int) {
        store.edit { preferences ->
            preferences[KEY_RESIDENT_LIMIT] = limit.coerceIn(FARM_MIN_RESIDENTS, FARM_MAX_RESIDENTS)
        }
    }

    suspend fun setSpriteFilter(filter: FarmSpriteFilter) {
        store.edit { preferences -> preferences[KEY_SPRITE_FILTER] = filter.key }
    }

    /** `individualId` drives the camera; `characterId` lets later screens validate farm membership. */
    suspend fun setFollowTarget(individualId: String?, characterId: Long?) {
        store.edit { preferences ->
            if (individualId.isNullOrBlank()) {
                preferences.remove(KEY_FOLLOW_INDIVIDUAL)
                preferences.remove(KEY_FOLLOW_CHARACTER)
            } else {
                preferences[KEY_FOLLOW_INDIVIDUAL] = individualId
                if (characterId != null && characterId > 0L) {
                    preferences[KEY_FOLLOW_CHARACTER] = characterId
                } else {
                    preferences.remove(KEY_FOLLOW_CHARACTER)
                }
            }
        }
    }

    companion object {
        const val NAME_SELECTED_FARM = "farm_home_selected_farm_id"
        const val NAME_FOLLOW_INDIVIDUAL = "farm_home_follow_individual_id"
        const val NAME_FOLLOW_CHARACTER = "farm_home_follow_character_id"
        const val NAME_RESIDENT_LIMIT = "farm_home_resident_limit"
        const val NAME_SPRITE_FILTER = "farm_home_sprite_filter"

        private val KEY_SELECTED_FARM = stringPreferencesKey(NAME_SELECTED_FARM)
        private val KEY_FOLLOW_INDIVIDUAL = stringPreferencesKey(NAME_FOLLOW_INDIVIDUAL)
        private val KEY_FOLLOW_CHARACTER = longPreferencesKey(NAME_FOLLOW_CHARACTER)
        private val KEY_RESIDENT_LIMIT = intPreferencesKey(NAME_RESIDENT_LIMIT)
        private val KEY_SPRITE_FILTER = stringPreferencesKey(NAME_SPRITE_FILTER)
    }
}
