package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAttackSpriteColorTest {
    @Test fun visibleAttackHueWinsOverTransparentPixelsBlackOutlineAndWhiteCore() {
        val red = 0xFFFF3010.toInt()
        val pixels = IntArray(100) { 0xFFFFFFFF.toInt() } + IntArray(100) { 0xFF000000.toInt() } +
            IntArray(100) { 0x0000FF00 } + IntArray(20) { red }
        assertEquals(red, dominantAttackSpriteColor(pixels, 0xFF2DE1FC.toInt()))
    }

    @Test fun sparseTranslucentAccentDoesNotReplaceTheDominantSpriteHue() {
        val green = 0xFF20C040.toInt()
        assertEquals(green, dominantAttackSpriteColor(IntArray(30) { green } + intArrayOf(0x40FF0000), 0))
    }

    @Test fun monochromeSpritesKeepTheirOwnColourAndMissingArtUsesFallback() {
        val fallback = 0xFF2DE1FC.toInt()
        assertEquals(0xFFFFFFFF.toInt(), dominantAttackSpriteColor(intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), fallback))
        assertEquals(fallback, dominantAttackSpriteColor(intArrayOf(0, 0x00FF0000), fallback))
        assertEquals(fallback, dominantAttackSpriteColor(intArrayOf(), fallback))
    }

    @Test fun windupUsesTheMatchingSmallOrLargeAttackSprite() {
        val green = 0xFF20C040.toInt()
        val yellow = 0xFFFFD447.toInt()
        val fallback = 0xFF2DE1FC.toInt()
        val palette = mapOf("small" to green, "large" to yellow)
        assertEquals(green, startupAttackSpriteColor(TechniqueKind.MELEE, palette, fallback))
        assertEquals(green, startupAttackSpriteColor(TechniqueKind.PROJECTILE, palette, fallback))
        assertEquals(yellow, startupAttackSpriteColor(TechniqueKind.SPECIAL, palette, fallback))
        assertEquals(fallback, startupAttackSpriteColor(TechniqueKind.SPECIAL, emptyMap(), fallback))
        assertEquals(fallback, startupAttackSpriteColor(TechniqueKind.SUPPORT, palette, fallback))
    }
}
