package com.github.nacabaro.vbhelper.source

import android.content.SharedPreferences
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Loads synchronously to avoid showing the default palette before a saved choice. */
class AppThemeSettings(private val preferences: SharedPreferences) {
    private val selectedTheme = MutableStateFlow(readTheme(preferences))
    val currentTheme = selectedTheme.asStateFlow()

    fun setTheme(theme: AppTheme) {
        preferences.edit().putString(PREFERENCE_KEY, theme.preferenceValue).apply()
        selectedTheme.value = theme
    }

    companion object {
        private const val PREFERENCE_KEY = "app_theme"

        fun readTheme(preferences: SharedPreferences): AppTheme =
            AppTheme.fromPreference(preferences.getString(PREFERENCE_KEY, null))
    }
}
