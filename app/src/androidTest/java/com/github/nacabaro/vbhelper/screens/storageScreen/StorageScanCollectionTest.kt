package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.DigimonScanEntry
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageScanCollectionTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val pixels = byteArrayOf(0, 0x7c)
    private fun entry(id: Long, percentage: Int) = DigimonScanEntry(
        CharacterDtos.CardCharaProgress(id, pixels, pixels, 1, 1, pixels, 1, 1,
            null, 100, 90, 80, 3, NfcCharacter.Attribute.Data, false),
        percentage, "Card $id", "Digimon $id")

    @Test fun topLeftScanActionOpensTheCollectionWithoutTakingTheAdventureAction() {
        var scans = 0
        var adventures = 0
        compose.setContent { VBHelperTheme {
            TopBanner("Storage", onScanDataClick = { scans++ }, onAdventureClick = { adventures++ })
        } }
        compose.onNodeWithContentDescription(context.getString(R.string.digimon_scan_title)).performClick()
        compose.runOnIdle { assertEquals(1, scans); assertEquals(0, adventures) }
        compose.onNodeWithContentDescription(context.getString(R.string.ui_adventure)).assertExists()
    }

    @Test fun partialScanIsVisibleButOnlyCompleteScanCanStartConversion() {
        var selected: Long? = null
        compose.setContent { VBHelperTheme {
            StorageScanCollectionContent(listOf(entry(100, 100), entry(200, 20)), false, null, false,
                onConvert = { selected = it.character.id }, onRetry = {}, onClose = {})
        } }
        compose.onNodeWithText("20%").assertExists()
        compose.onNodeWithText("Card 200").assertExists()
        compose.onNodeWithTag("scan-convert-200").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("scan-convert-100").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(100L, selected) }
    }

    @Test fun convertedEntryDisappearsAndEmptyCollectionKeepsFeedbackAndCloseVisible() {
        val entries = mutableStateOf(listOf(entry(100, 100)))
        compose.setContent { VBHelperTheme {
            StorageScanCollectionContent(entries.value, false, context.getString(R.string.digimon_scan_converted), false,
                onConvert = { entries.value = emptyList() }, onRetry = {}, onClose = {})
        } }
        compose.onNodeWithTag("scan-convert-100").performClick()
        compose.onNodeWithText(context.getString(R.string.storage_scan_empty)).assertExists()
        compose.onNodeWithText(context.getString(R.string.digimon_scan_converted)).assertExists()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }
}
