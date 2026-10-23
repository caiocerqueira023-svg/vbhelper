package com.github.nacabaro.vbhelper.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.nacabaro.vbhelper.components.ChatStatusPanel
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Rule
import org.junit.Test

class ConditionDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summariesAndExactReadingsAreBothVisibleAcrossEveryPalette() {
        val theme = mutableStateOf(AppTheme.VB_HELPER)
        compose.setContent {
            VBHelperTheme(appTheme = theme.value) {
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
