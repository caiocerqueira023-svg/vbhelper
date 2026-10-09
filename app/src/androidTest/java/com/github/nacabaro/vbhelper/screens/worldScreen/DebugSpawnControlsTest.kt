package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import com.github.nacabaro.vbhelper.dtos.DebugSpawnCharacter
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebugSpawnControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun longPressChoosesWithoutAlsoSpawningRandomly() {
        var random = 0
        var choose = 0
        compose.setContent {
            VBHelperTheme {
                DebugSpawnButton(enabled = true, loading = false,
                    onRandomSpawn = { random++ }, onChooseSpawn = { choose++ })
            }
        }
        compose.onNodeWithTag("radar-debug-spawn").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, random); assertEquals(1, choose) }
        compose.onNodeWithTag("radar-debug-spawn").performClick()
        compose.runOnIdle { assertEquals(1, random); assertEquals(1, choose) }
    }

    @Test fun disabledButtonDoesNotChooseOrSpawn() {
        var actions = 0
        compose.setContent {
            VBHelperTheme {
                DebugSpawnButton(enabled = false, loading = true,
                    onRandomSpawn = { actions++ }, onChooseSpawn = { actions++ })
            }
        }
        compose.onNodeWithTag("radar-debug-spawn").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0, actions) }
    }

    @Test fun searchingByCardSelectsTheCorrectVariantOfTheSameSpecies() {
        var selected: Long? = null
        val characters = listOf(
            DebugSpawnCharacter(100, 3, "Enabled card", "Agumon", byteArrayOf(), 0, 0),
            DebugSpawnCharacter(200, 3, "Disabled card", "Agumon", byteArrayOf(), 0, 0))
        compose.setContent {
            VBHelperTheme {
                DebugSpawnPickerDialog(characters, failed = false, enabled = true,
                    onRetry = {}, onSelect = { selected = it }, onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Disabled")
        compose.onNodeWithTag("debug-spawn-character-100").assertDoesNotExist()
        compose.onNodeWithTag("debug-spawn-character-200").performClick()
        compose.runOnIdle { assertEquals(200L, selected) }
    }
}
