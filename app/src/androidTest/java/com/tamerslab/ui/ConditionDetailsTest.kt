package com.tamerslab.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.tamerslab.components.ChatStatusPanel
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.ui.theme.TamersLabTheme
import org.junit.Rule
import org.junit.Test

class ConditionDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summariesAndExactReadingsAreBothVisibleAcrossEveryPalette() {
        val theme = mutableStateOf(AppTheme.VB_HELPER)
        compose.setContent {
            TamersLabTheme(appTheme = theme.value) {
                ChatStatusPanel("Trust: 84/100", 84, summary = "Established rapport")
            }
        }
        AppTheme.entries.forEach { palette ->
            compose.runOnIdle { theme.value = palette }
            compose.onNodeWithText("Established rapport").assertIsDisplayed()
            compose.onNodeWithText("Trust: 84/100").assertIsDisplayed()
        }
    }
}
