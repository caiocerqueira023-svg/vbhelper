package com.github.nacabaro.vbhelper.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BattleAuthenticationPromptTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nacaBattleIsOpenedOnlyAfterTheUserTapsTheButton() {
        val openRequests = AtomicInteger(0)

        composeRule.setContent {
            MaterialTheme {
                BattleAuthenticationPrompt(
                    isCheckingAuth = false,
                    onOpenNacaBattle = { openRequests.incrementAndGet() }
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, openRequests.get())

        composeRule.onNodeWithTag(BattleAuthenticationPromptTags.NacaBattleButton)
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, openRequests.get()) }
    }
}
