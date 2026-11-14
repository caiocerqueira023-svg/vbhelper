package com.tamerslab.digiline

import com.tamerslab.digiline.model.DigilineSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DigilineSeedTest {
    @Test
    fun stableHashIsStableAndSensitiveToInput() {
        assertEquals(DigilineSeed.stableHash("aegiomon"), DigilineSeed.stableHash("aegiomon"))
        assertNotEquals(DigilineSeed.stableHash("aegiomon"), DigilineSeed.stableHash("agumon"))
        assertNotEquals(DigilineSeed.stableHash(""), DigilineSeed.stableHash("a"))
    }

    @Test
    fun sessionSeedIsStableAndChangesWithEveryInput() {
        val base = DigilineSeed.sessionSeed("ind-1", dayIndex = 4L, sessionCounter = 2L, contentVersion = 1)
        assertEquals(base, DigilineSeed.sessionSeed("ind-1", dayIndex = 4L, sessionCounter = 2L, contentVersion = 1))
        assertNotEquals(base, DigilineSeed.sessionSeed("ind-2", dayIndex = 4L, sessionCounter = 2L, contentVersion = 1))
        assertNotEquals(base, DigilineSeed.sessionSeed("ind-1", dayIndex = 5L, sessionCounter = 2L, contentVersion = 1))
        assertNotEquals(base, DigilineSeed.sessionSeed("ind-1", dayIndex = 4L, sessionCounter = 3L, contentVersion = 1))
        assertNotEquals(base, DigilineSeed.sessionSeed("ind-1", dayIndex = 4L, sessionCounter = 2L, contentVersion = 2))
    }

    @Test
    fun selectionSeedsUseNamedSubStreams() {
        val base = 123456789L
        val quiz = DigilineSeed.selectionSeed(base, "quiz")
        val generic = DigilineSeed.selectionSeed(base, "generic", 6)
        assertEquals(quiz, DigilineSeed.selectionSeed(base, "quiz"))
        assertNotEquals(quiz, generic)
        assertNotEquals(generic, DigilineSeed.selectionSeed(base, "generic", 7))
    }

    @Test
    fun dayIndexFloorsAndHandlesPreEpochTimes() {
        assertEquals(0L, DigilineSeed.dayIndex(0L))
        assertEquals(0L, DigilineSeed.dayIndex(TEST_DAY - 1))
        assertEquals(1L, DigilineSeed.dayIndex(TEST_DAY))
        assertEquals(10L, DigilineSeed.dayIndex(10L * TEST_DAY + 5))
        assertEquals(-1L, DigilineSeed.dayIndex(-1L))
        assertEquals(-1L, DigilineSeed.dayIndex(-TEST_DAY))
        assertEquals(-2L, DigilineSeed.dayIndex(-TEST_DAY - 1))
    }

    @Test
    fun randomStreamsAreReproducible() {
        assertEquals(DigilineSeed.random(7L).nextInt(1_000), DigilineSeed.random(7L).nextInt(1_000))
        val values = (1..10).map { DigilineSeed.combine(7L, it.toLong()) }
        assertEquals(values, (1..10).map { DigilineSeed.combine(7L, it.toLong()) })
        assertTrue(values.toSet().size > 1)
    }

    @Test
    fun combineTextIsDeterministic() {
        val seeded = DigilineSeed.combineText(42L, "battle_win")
        assertEquals(seeded, DigilineSeed.combineText(42L, "battle_win"))
        assertNotEquals(seeded, DigilineSeed.combineText(42L, "injury"))
        assertNotEquals(seeded, DigilineSeed.combineText(43L, "battle_win"))
    }
}
