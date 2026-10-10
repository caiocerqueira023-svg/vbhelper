package com.github.nacabaro.vbhelper.screens.cardScreen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.battle.BattleAssetPaths
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharacterDetailsContent
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DigimonScanConvertButton
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DigimonScanProgressContent
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DexSpeciesProfileLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val profile = SpeciesProfile(101, "Pulsemon", "Pulsemon", "Rookie", "Beast Man",
        "Profile text used to verify the reading layout.\n\n" +
            "Long descriptions should wrap naturally beneath the Profile heading while the actions remain reachable.",
        listOf("Elec Rush", "Petit Thunder"), SpeciesSource.OFFICIAL_MATCHED)

    @Test fun portraitLeadsTheFactsAndAllThemesKeepActionsVisible() {
        val selectedTheme = mutableStateOf(AppTheme.VB_HELPER)
        val character = fixtureCharacter()
        compose.setContent { key(selectedTheme.value) { ProfileCard(character, profile, selectedTheme.value) } }
        for (theme in AppTheme.entries) {
            compose.runOnIdle { selectedTheme.value = theme }
            val portrait = compose.onNodeWithTag("dex-species-portrait").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("dex-profile-body").fetchSemanticsNode().boundsInRoot
            val name = compose.onNodeWithText("Pulsemon").fetchSemanticsNode().boundsInRoot
            assertTrue(name.bottom < portrait.top)
            assertTrue(kotlin.math.abs(portrait.center.x - body.center.x) < 2f)
            compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.dex_chara_fusions_button)).assertIsDisplayed()
            capture("${theme.preferenceValue}-portrait")
            compose.onNodeWithText(context.getString(R.string.ui_profile)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Rookie").assertExists()
            compose.onNodeWithText("Beast Man").assertExists()
            compose.onNodeWithText("Elec Rush\nPetit Thunder").assertExists()
            compose.onNodeWithText(profile.profileDescription!!).performScrollTo().assertIsDisplayed()
            capture("${theme.preferenceValue}-profile")
        }
    }

    @Test fun longProfileAndLargeTextKeepTheFooterPinnedAndSpeciesPickerUsable() {
        var closed = 0
        var jogress = 0
        var picked = 0
        val character = fixtureCharacter()
        compose.setContent {
            ProfileCard(character, profile.copy(profileDescription = profile.profileDescription!!.repeat(16)),
                AppTheme.DIGIMON_NET, fontScale = 1.3f, custom = true,
                onClose = { closed++ }, onJogress = { jogress++ }, onPickSpecies = { picked++ })
        }
        compose.onNodeWithContentDescription(context.getString(R.string.ui_choose_species_from_dim)).performClick()
        compose.onNodeWithText(context.getString(R.string.dex_chara_fusions_button)).assertIsDisplayed().performClick()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, closed); assertEquals(1, jogress); assertEquals(1, picked) }
        compose.onNodeWithText("100").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
        capture("large-text-footer")
    }

    @Test fun obscuredSpeciesStillHideFactsAndProfile() {
        val character = fixtureCharacter()
        compose.setContent { ProfileCard(character, profile, AppTheme.VB_HELPER, obscure = true) }
        compose.onNodeWithText("Pulsemon").assertDoesNotExist()
        compose.onNodeWithText("Rookie").assertDoesNotExist()
        compose.onNodeWithText("Beast Man").assertDoesNotExist()
        compose.onNodeWithText("Elec Rush\nPetit Thunder").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.ui_profile)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.dex_chara_close_button)).assertIsDisplayed()
    }

    @Composable
    private fun ProfileCard(character: CharacterDtos.CardCharaProgress, profile: SpeciesProfile, theme: AppTheme,
        fontScale: Float = 1f, custom: Boolean = false, obscure: Boolean = false,
        onClose: () -> Unit = {}, onJogress: () -> Unit = {}, onPickSpecies: () -> Unit = {}) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            VBHelperTheme(appTheme = theme) {
                val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .88f }
                Box(Modifier.fillMaxSize().background(theme.palette.background).safeDrawingPadding(),
                    contentAlignment = Alignment.Center) {
                    Card(Modifier.widthIn(max = 480.dp).fillMaxWidth(.92f).heightIn(max = maxHeight),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        DexCharacterDetailsContent(character, obscure, profile, emptyList(), true, custom, true,
                            onClose, onJogress, {}, onPickSpecies,
                            scanContent = { DigimonScanProgressContent(20, null, false) },
                            primaryAction = { DigimonScanConvertButton(20, false) {} })
                    }
                }
            }
        }
    }

    /** Existing bundled art, encoded in the same RGB565 format as an imported card. */
    private fun fixtureCharacter(): CharacterDtos.CardCharaProgress {
        val bitmap = context.assets.open(BattleAssetPaths.characterFrame("dim000_mon03", 1)).use {
            checkNotNull(BitmapFactory.decodeStream(it))
        }
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        val bytes = ByteBuffer.allocate(pixels.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        pixels.forEach { pixel ->
            val rgb565 = if ((pixel ushr 24) == 0) 0x07e0 else
                (((pixel ushr 19) and 0x1f) shl 11) or
                    (((pixel ushr 10) and 0x3f) shl 5) or ((pixel ushr 3) and 0x1f)
            bytes.putShort(rgb565.toShort())
        }
        return CharacterDtos.CardCharaProgress(101, bytes.array(), bytes.array(), width, height,
            byteArrayOf(0xe0.toByte(), 0x07), 1, 1, 1234, 100, 90, 80, 3, NfcCharacter.Attribute.Vaccine, false)
    }

    private fun capture(name: String) {
        val folder = File(context.getExternalFilesDir(null), "dex-profile-layout").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
