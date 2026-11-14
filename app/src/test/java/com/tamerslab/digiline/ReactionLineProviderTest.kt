package com.tamerslab.digiline

import com.tamerslab.digiline.engine.ReactionLineProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionLineProviderTest {
    private val lines = listOf(
        eventLine("ev.battle_win.shared.1", "battle_win", text = "shared one", moodDelta = 1),
        eventLine("ev.battle_win.shared.2", "battle_win", text = "shared two", moodDelta = 2),
        eventLine("ev.battle_win.6.1", "battle_win", types = listOf(6), text = "brave line", moodDelta = 2),
        eventLine("ev.injury.shared.1", "injury", text = "hurt", moodDelta = -1),
    )

    @Test
    fun sameSeedPicksTheSameLine() {
        val provider = ReactionLineProvider(lines)
        val first = provider.pick("battle_win", 9, seed = 123L)
        val second = provider.pick("battle_win", 9, seed = 123L)
        assertNotNull(first)
        assertEquals(first, second)
    }

    @Test
    fun personalitySpecificLinesArePreferredOverSharedOnes() {
        val provider = ReactionLineProvider(lines)
        repeat(20) { index ->
            val pick = provider.pick("battle_win", 6, seed = index.toLong())
            assertEquals("ev.battle_win.6.1", pick?.key)
        }
        val shared = provider.pick("battle_win", 9, seed = 1L)
        assertNotNull(shared)
        assertTrue(shared!!.key.startsWith("ev.battle_win.shared"))
    }

    @Test
    fun recentlyDeliveredLinesAreAvoided() {
        val provider = ReactionLineProvider(lines)
        // Type 9 only has two shared lines: excluding one must always pick the other.
        val pick = provider.pick("battle_win", 9, seed = 5L, excludedIds = setOf("ev.battle_win.shared.1"))
        assertEquals("ev.battle_win.shared.2", pick?.key)
    }

    @Test
    fun anExhaustedExclusionSetFallsBackInsteadOfReturningNull() {
        val provider = ReactionLineProvider(lines)
        val excluded = lines.map { it.id }.toSet()
        assertNotNull(provider.pick("battle_win", 6, seed = 5L, excludedIds = excluded))
    }

    @Test
    fun unknownEventsAndEmptyProvidersReturnNull() {
        assertNull(ReactionLineProvider().pick("battle_win", 6, seed = 1L))
        assertNull(ReactionLineProvider(lines).pick("unknown", 6, seed = 1L))
        assertNull(ReactionLineProvider(lines).pick("", 6, seed = 1L))
        assertTrue(ReactionLineProvider().linesFor("battle_win", 6).isEmpty())
    }

    @Test
    fun moodDeltaComesFromThePickedLine() {
        val provider = ReactionLineProvider(lines)
        val pick = provider.pick("battle_win", 6, seed = 1L)
        assertEquals(2, pick?.moodDelta)
        assertEquals(2, provider.moodDelta("battle_win", 6, seed = 1L))
        assertEquals(-1, provider.moodDelta("injury", 1, seed = 1L))
        assertEquals(0, provider.moodDelta("unknown", 1, seed = 1L))
        assertEquals(4, provider.lineCount)
    }

    @Test
    fun enabledTypesDoNotLeakIntoOtherPersonalities() {
        val provider = ReactionLineProvider(lines)
        assertFalse(provider.linesFor("injury", 1).any { it.types.isNotEmpty() })
    }
}
