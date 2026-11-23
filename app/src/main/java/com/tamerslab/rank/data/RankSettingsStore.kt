package com.tamerslab.rank.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User preferences for the Tamer Rank surfaces (home card and notifications). */
class RankSettingsStore(private val dataStore: DataStore<Preferences>) {
    private companion object {
        val HOME_CARD_ENABLED = booleanPreferencesKey("rank_home_card_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("rank_notifications_enabled")
    }

    val homeCardEnabled: Flow<Boolean> = dataStore.data.map { it[HOME_CARD_ENABLED] ?: true }
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setHomeCardEnabled(enabled: Boolean) {
        dataStore.edit { it[HOME_CARD_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }
}
