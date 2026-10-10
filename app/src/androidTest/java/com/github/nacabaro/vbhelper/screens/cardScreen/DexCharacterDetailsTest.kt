package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharacterDetailsContent
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DigimonScanProgressContent
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DigimonScanConvertButton
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DexCharacterDetailsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pixels = byteArrayOf(0, 0x7c)
    private val character = CharacterDtos.CardCharaProgress(101, pixels, pixels, 1, 1, pixels, 1, 1,
        1234, 100, 90, 80, 2, NfcCharacter.Attribute.Data, false)
    private val evolution = CharacterDtos.EvolutionRequirementsWithSpritesAndObtained(102, 101, pixels, 1, 1,
        null, 10, 1200, 15, 70, 12, 0)

    @Test fun requirementsAreReadableAndEvolutionSelectionKeepsTheCorrectIdentity() {
        var selected: Long? = null
        compose.setContent {
            VBHelperTheme {
                DexCharacterDetailsContent(character, false, null, listOf(evolution), true, false, false,
                    onClose = {}, onJogress = {}, onSelectCharacter = { selected = it }, onPickSpecies = {},
                    modifier = Modifier.size(320.dp, 600.dp))
            }
        }
        compose.onNodeWithText("100").performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_detail_evolutions)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_detail_trophies, 10)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_detail_vitals, 1200)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_detail_adventure, 1)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.dex_detail_open_evolution)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(102L, selected) }
        compose.onNodeWithText(context.getString(R.string.dex_chara_fusions_button)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }

    @Test fun obscuredInformationKeepsPrivateStatsHiddenAndCloseAvailable() {
        compose.setContent {
            VBHelperTheme {
                DexCharacterDetailsContent(character, true, null, emptyList(), false, false, false,
                    onClose = {}, onJogress = {}, onSelectCharacter = {}, onPickSpecies = {},
                    modifier = Modifier.size(320.dp, 480.dp))
            }
        }
        compose.onNodeWithText("100").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.dex_chara_stats_unknown)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }

    @Test fun completeScanExposesManualConversionAndItsStorageDestination() {
        var conversions = 0
        compose.setContent {
            VBHelperTheme {
                DexCharacterDetailsContent(character, false, null, emptyList(), false, false, false,
                    onClose = {}, onJogress = {}, onSelectCharacter = {}, onPickSpecies = {},
                    modifier = Modifier.size(320.dp, 600.dp),
                    scanContent = { DigimonScanProgressContent(100, null, false) },
                    primaryAction = { DigimonScanConvertButton(100, false) { conversions++ } })
            }
        }
        compose.onNodeWithText(context.getString(R.string.digimon_scan_ready)).performScrollTo().assertExists()
        compose.onNodeWithText(context.getString(R.string.digimon_scan_convert)).performClick()
        compose.runOnIdle { assertEquals(1, conversions) }
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }

    @Test fun incompleteScanCannotBeConverted() {
        compose.setContent { VBHelperTheme { DigimonScanConvertButton(80, false) {} } }
        compose.onNodeWithText(context.getString(R.string.digimon_scan_convert)).assertDoesNotExist()
    }

    @Test fun zeroScanDetailsHideTheProgressSectionAndConversion() {
        compose.setContent {
            VBHelperTheme {
                DexCharacterDetailsContent(character, false, null, emptyList(), false, false, false,
                    onClose = {}, onJogress = {}, onSelectCharacter = {}, onPickSpecies = {},
                    modifier = Modifier.size(320.dp, 600.dp),
                    scanContent = { DigimonScanProgressContent(0, null, false) },
                    primaryAction = { DigimonScanConvertButton(0, false) {} })
            }
        }
        compose.onNodeWithText(context.getString(R.string.digimon_scan_title)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.digimon_scan_help)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.digimon_scan_convert)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }

    @Test fun busyConversionCannotBeSubmittedAgain() {
        compose.setContent { VBHelperTheme { DigimonScanConvertButton(100, true) {} } }
        compose.onNodeWithText(context.getString(R.string.digimon_scan_converting)).assertIsNotEnabled()
    }
}
