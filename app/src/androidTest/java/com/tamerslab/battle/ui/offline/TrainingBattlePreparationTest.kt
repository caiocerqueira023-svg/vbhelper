package com.tamerslab.battle.ui.offline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tamerslab.components.LocalPartnerParticipationOverride
import com.tamerslab.components.PartnerParticipationOverride
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.battle.ui.assetOfflineBattleParticipant
import com.tamerslab.battle.ui.offlineBattleParticipant
import com.tamerslab.utils.DeviceType
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
            TestPreparation {
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
        composeRule.onNodeWithTag("storage-character-picker-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Gabumon").performClick()
        composeRule.onNodeWithTag(TrainingBattleTags.AllyOneSlot)
            .assertTextContains("Gabumon")
    }

    @Test
    fun combatantPickerOpensAsAFullScreenSelectionSurface() {
        composeRule.setContent {
            TestPreparation {
                TrainingBattlePreparation(
                    activePartner = null,
                    availablePartners = listOf(assetOfflineBattleParticipant("agumon", "Agumon", 120, 18)),
                    availableOpponents = listOf(assetOfflineBattleParticipant("patamon", "Patamon", 115, 17)),
                    onBack = {},
                    onStart = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag(TrainingBattleTags.AllyOneSlot).performClick()

        composeRule.onNodeWithTag("storage-character-picker-screen").assertIsDisplayed()
    }

    @Test
    fun storedCombatantOpensTheSharedStoragePickerScreen() {
        val stored = storedPickerCharacter(91, "Agumon")
        composeRule.setContent {
            TestPreparation {
                TrainingBattlePreparation(
                    activePartner = null,
                    availablePartners = listOf(offlineBattleParticipant(stored, maxHp = 120, attackPower = 18)),
                    availableOpponents = listOf(assetOfflineBattleParticipant("patamon", "Patamon", 115, 17)),
                    onBack = {},
                    onStart = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag(TrainingBattleTags.AllyOneSlot).performClick()

        composeRule.onNodeWithTag("storage-character-picker-screen").assertIsDisplayed()
    }

    @Test
    fun mixedOpponentChoicesUseTheSharedPickerAndCanSelectBundledDigimon() {
        val stored = storedPickerCharacter(92, "Gabumon")
        composeRule.setContent {
            TestPreparation {
                TrainingBattlePreparation(
                    activePartner = null,
                    availablePartners = listOf(assetOfflineBattleParticipant("agumon", "Agumon", 120, 18)),
                    availableOpponents = listOf(
                        offlineBattleParticipant(stored, maxHp = 120, attackPower = 18),
                        offlineBattleParticipant(
                            storedPickerCharacter(93, "Veemon"),
                            maxHp = 122,
                            attackPower = 19
                        ),
                        assetOfflineBattleParticipant("patamon", "Patamon", 115, 17)
                    ),
                    onBack = {},
                    onStart = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag(TrainingBattleTags.OpponentOneSlot).performClick()

        composeRule.onNodeWithTag("storage-character-picker-screen").assertIsDisplayed()
        composeRule.onAllNodesWithText("Gabumon").onFirst().assertExists()
        composeRule.onNodeWithTag("offline-battle-picker-option-patamon")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag(TrainingBattleTags.OpponentOneSlot)
            .assertTextContains("Patamon")
    }

    @Test
    fun primaryActionsRemainAvailableWhenTheFormationChanges() {
        composeRule.setContent {
            TestPreparation {
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

@Composable
private fun TestPreparation(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPartnerParticipationOverride provides PartnerParticipationOverride(null)) {
        androidx.compose.material3.MaterialTheme { content() }
    }
}

private fun storedPickerCharacter(id: Long, name: String) = CharacterDtos.CharacterWithSprites(
    id = id,
    charId = 12,
    stage = 3,
    attribute = NfcCharacter.Attribute.Data,
    ageInDays = 10,
    mood = 80,
    vitalPoints = 1200,
    transformationCountdown = 0,
    injuryStatus = NfcCharacter.InjuryStatus.None,
    trophies = 0,
    currentPhaseBattlesWon = 0,
    currentPhaseBattlesLost = 0,
    totalBattlesWon = 0,
    totalBattlesLost = 0,
    activityLevel = 0,
    heartRateCurrent = 0,
    characterType = DeviceType.VBDevice,
    spriteIdle = byteArrayOf(0, 0, 0),
    spriteIdle2 = byteArrayOf(0, 0, 0),
    spriteRun1 = byteArrayOf(0, 0, 0),
    spriteRun2 = byteArrayOf(0, 0, 0),
    spriteWidth = 1,
    spriteHeight = 1,
    nameSprite = byteArrayOf(),
    nameSpriteWidth = 0,
    nameSpriteHeight = 0,
    isBemCard = false,
    nickname = name,
    speciesName = name,
    isInAdventure = false,
    active = false,
    isFavorite = false
)
