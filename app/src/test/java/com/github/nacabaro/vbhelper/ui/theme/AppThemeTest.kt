package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {
    @Test fun missingOrUnknownPreferenceKeepsTheExistingHelperAppearance() {
        for (value in listOf(null, "", "unknown", "system", "dark")) {
            assertEquals(AppTheme.VB_HELPER, AppTheme.fromPreference(value))
        }
    }

    @Test fun eachThemeHasAStableDistinctSavedValue() {
        assertEquals(4, AppTheme.entries.size)
        assertEquals(4, AppTheme.entries.map { it.preferenceValue }.toSet().size)
        for (theme in AppTheme.entries) {
            assertEquals(theme, AppTheme.fromPreference(theme.preferenceValue))
        }
    }

    @Test fun helperRetainsItsCurrentColors() {
        val palette = AppTheme.VB_HELPER.palette
        assertEquals(Color(0xFF120F1F), palette.background)
        assertEquals(Color(0xFF1C1730), palette.surface)
        assertEquals(Color(0xFF8B5CF6), palette.primary)
        assertEquals(Color(0xFFA78BFA), palette.primaryBright)
        assertEquals(Color(0xFF2DE1FC), palette.accent)
        assertEquals(Color(0xFFF4F1FF), palette.textPrimary)
        assertTrue(palette.isDark)
    }

    @Test fun labArenaAndDigimonNetFollowTheirReferenceColorFamilies() {
        val lab = AppTheme.VB_LAB.palette
        val arena = AppTheme.VB_ARENA.palette
        assertTrue(lab.isDark)
        assertEquals(lab.background.red, lab.background.green)
        assertEquals(lab.background.green, lab.background.blue)
        assertTrue(lab.accent.blue > lab.accent.red)
        assertTrue(lab.accent.green > lab.accent.red)
        assertTrue(!arena.isDark)
        assertTrue(arena.background.luminance() > .9f)
        assertTrue(arena.primary.green > arena.primary.red)
        assertTrue(arena.primary.green > arena.primary.blue)
        val website = AppTheme.DIGIMON_NET.palette
        assertTrue(!website.isDark)
        assertEquals(Color.White, website.background)
        assertEquals(Color(0xFF1251D0), website.primaryBright)
        assertEquals(Color(0xFFF6FF00), website.primaryDim)
        assertEquals(Color(0xFFE4E9FF), website.surfaceElevated)
    }

    @Test fun paletteTextAndActionLabelsAreReadable() {
        for (theme in AppTheme.entries) {
            val palette = theme.palette
            val surfaces = listOf(palette.background, palette.backgroundAlt, palette.surface,
                palette.surfaceElevated, palette.surfaceHighlight)
            for (surface in surfaces) {
                assertContrast(theme, palette.textPrimary, surface)
                assertContrast(theme, palette.textSecondary, surface)
            }
            assertContrast(theme, palette.onPrimary, palette.primaryBright)
        }
    }

    private fun assertContrast(theme: AppTheme, text: Color, background: Color) {
        val a = text.luminance()
        val b = background.luminance()
        val contrast = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
        assertTrue("${theme.displayName}: text contrast $contrast must reach 4.5:1", contrast >= 4.5f)
    }

    @Test fun blastConfirmationAndNeedleRemainReadableInEveryPalette() {
        for (theme in AppTheme.entries) {
            val palette = theme.palette
            assertContrast(theme, palette.onBlastControl, palette.blastControl)
            val light = maxOf(palette.blastNeedle.luminance(), palette.blastTrack.luminance())
            val dark = minOf(palette.blastNeedle.luminance(), palette.blastTrack.luminance())
            assertTrue((light + .05f) / (dark + .05f) >= 3f)
        }
    }
}
