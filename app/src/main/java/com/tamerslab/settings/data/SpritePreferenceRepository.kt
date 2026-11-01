package com.tamerslab.settings.data

import android.content.Context
import android.content.SharedPreferences
import com.tamerslab.species.SpritePreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Cosmetic preference. Retired mode values migrate without touching the collection. */
class SpritePreferenceRepository(
    private val preferences: SharedPreferences
) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("app_preferences", Context.MODE_PRIVATE))

    private val mutableState = MutableStateFlow(readPreference())
    val preference: StateFlow<SpritePreference> = mutableState.asStateFlow()
    val current: SpritePreference get() = mutableState.value

    private fun readPreference(): SpritePreference = when (preferences.getString(KEY_PREFERENCE, null)) {
        SpritePreference.DOT_FIRST.name -> SpritePreference.DOT_FIRST
        SpritePreference.VB_FIRST.name -> SpritePreference.VB_FIRST
        else -> if (preferences.getString(LEGACY_MODE, null) == "EXPANDED") SpritePreference.DOT_FIRST else SpritePreference.VB_FIRST
    }

    @Synchronized fun setPreference(preference: SpritePreference) {
        check(preferences.edit().putString(KEY_PREFERENCE, preference.name).remove(LEGACY_MODE).commit()) {
            "Could not save the sprite preference"
        }
        mutableState.value = preference
    }

    private companion object {
        const val KEY_PREFERENCE = "sprite_preference"
        const val LEGACY_MODE = "game_mode"
    }
}
