package com.github.nacabaro.vbhelper.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.screens.settingsScreen.AppThemePicker
import com.github.nacabaro.vbhelper.source.AppThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppThemeSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choosingEachThemeRecolorsTheUiAndRestoresTheSavedChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("theme_selection_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            val settings = AppThemeSettings(preferences)
            var observedText = Color.Unspecified
            var observedAccent = Color.Unspecified
            compose.setContent {
                val theme by settings.currentTheme.collectAsState()
                VBHelperTheme(appTheme = theme) {
                    val text = TextPrimaryOnDark
                    val accent = VitalCyan
                    SideEffect { observedText = text; observedAccent = accent }
                    AppThemePicker(theme, settings::setTheme)
                }
            }
            compose.onNodeWithText("VB Helper").assertIsSelected()
            for (theme in AppTheme.entries.drop(1) + AppTheme.VB_HELPER) {
                compose.onNodeWithText(theme.displayName).performClick().assertIsSelected()
                compose.runOnIdle {
                    assertEquals(theme.palette.textPrimary, observedText)
                    assertEquals(theme.palette.accent, observedAccent)
                    assertEquals(theme, AppThemeSettings(preferences).currentTheme.value)
                }
            }
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test fun phoneModeDoesNotChangeAnySelectedPaletteOrItsGeometryAndTypography() {
        val selectedTheme = mutableStateOf(AppTheme.VB_HELPER)
        val nightMode = mutableStateOf(Configuration.UI_MODE_NIGHT_NO)
        var observedBackground = Color.Unspecified
        var observedAccent = Color.Unspecified
        compose.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode.value
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                VBHelperTheme(appTheme = selectedTheme.value) {
                    val background = DeepPurpleBg
                    val accent = VitalCyan
                    val shapes = MaterialTheme.shapes
                    val typography = MaterialTheme.typography
                    SideEffect {
                        observedBackground = background
                        observedAccent = accent
                        assertEquals(VitalArenaShapes, shapes)
                        assertEquals(appTypography(appFontFamily(AppFont.OXANIUM)), typography)
                    }
                }
            }
        }
        for (theme in AppTheme.entries) {
            for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                compose.runOnIdle { selectedTheme.value = theme; nightMode.value = mode }
                compose.runOnIdle {
                    assertEquals(theme.palette.background, observedBackground)
                    assertEquals(theme.palette.accent, observedAccent)
                }
            }
        }
    }
}
