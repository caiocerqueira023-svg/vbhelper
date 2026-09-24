package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nacabaro.vbhelper.screens.assetOfflineBattleParticipant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingBattlePreparationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun participantSlotOpensStorageStylePickerAndUsesTheChosenDigimon() {
        composeRule.setContent {
            MaterialTheme {
                TrainingBattlePreparation(
                    activePartner = null,
                    availablePartners = listOf(
                        assetOfflineBattleParticipant("agumon", "Agumon", 120, 18),
                        assetOfflineBattleParticipant("gabumon", "Gabumon", 130, 16)
                    ),
                    availableOpponents = listOf(
                        assetOfflineBattleParticipant("patamon", "Patamon", 115, 17)
                    ),
                    onBack = {},
                    onStart = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag(TrainingBattleTags.AllyOneSlot)
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithText("Escolher Digimon").assertIsDisplayed()
        composeRule.onNodeWithText("Gabumon").performClick()
        composeRule.onNodeWithTag(TrainingBattleTags.AllyOneSlot)
            .assertTextContains("Gabumon")
    }

    @Test
    fun primaryActionsRemainAvailableWhenTheFormationChanges() {
        composeRule.setContent {
            MaterialTheme {
                TrainingBattlePreparation(
                    activePartner = assetOfflineBattleParticipant("agumon", "Agumon", 120, 18),
                    availablePartners = listOf(
                        assetOfflineBattleParticipant("agumon", "Agumon", 120, 18),
                        assetOfflineBattleParticipant("gabumon", "Gabumon", 130, 16)
                    ),
                    availableOpponents = listOf(
                        assetOfflineBattleParticipant("patamon", "Patamon", 115, 17),
                        assetOfflineBattleParticipant("gomamon", "Gomamon", 125, 15)
                    ),
                    onBack = {},
                    onStart = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Entrar na arena").assertIsDisplayed()
        composeRule.onNodeWithText("2 × 2").performClick()
        composeRule.onNodeWithText("Entrar na arena").assertIsDisplayed()
        composeRule.onNodeWithText("Voltar").assertIsDisplayed()

    }
}
