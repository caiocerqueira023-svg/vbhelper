package com.tamerslab.digiline

import com.tamerslab.digiline.engine.DiaryComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryComposerTest {
    private val lines = listOf(
        eventLine("ev.evolution.shared.1", "evolution", text = "Something changed in me."),
        eventLine("ev.battle_win.6.1", "battle_win", types = listOf(6), text = "We held the line."),
        eventLine("ev.diary.shared.1", "diary", text = "A quiet day."),
    )

    @Test
    fun sameInputsAlwaysComposeTheSameEntry() {
        val composer = DiaryComposer(lines)
        val first = composer.compose("battle_win", personalityType = 6, facts = listOf("Agumon is a Child."))
        val second = composer.compose("battle_win", personalityType = 6, facts = listOf("Agumon is a Child."))
        assertEquals(first, second)
    }

    @Test
    fun factsAreTrimmedDeduplicatedAndCapped() {
        val composer = DiaryComposer(lines)
        val entry = composer.compose(
            event = "evolution",
            personalityType = 2,
            facts = listOf("  Fact A. ", "Fact A.", "", "Fact B.", "Fact C.", "Fact D."),
            maxFacts = 2,
        )
        assertEquals(listOf("Fact A.", "Fact B."), entry.facts)
        assertTrue(entry.body.contains("Fact A."))
        assertTrue(entry.body.contains("Fact B."))
        assertFalse(entry.body.contains("Fact C."))
    }

    @Test
    fun missingContentFallsBackToASafeEntry() {
        val composer = DiaryComposer()
        val entry = composer.compose(event = "battle_win", personalityType = 6)
        assertNotNull(entry)
        assertTrue(entry.body.isNotBlank())
        assertEquals("A Hard-Won Victory", entry.title)
        assertNull(entry.lineId)
        assertEquals(0, entry.moodDelta)
    }

    @Test
    fun factsStillProduceABodyWithoutAuthoredLines() {
        val entry = DiaryComposer().compose(
            event = "unknown_event",
            personalityType = 1,
            facts = listOf("We traveled to the forest."),
        )
        assertTrue(entry.body.contains("We traveled to the forest."))
        assertEquals("Unknown Event", entry.title)
    }

    @Test
    fun authoredEventLinesAreUsedWhenAvailable() {
        val entry = DiaryComposer(lines).compose(event = "battle_win", personalityType = 6)
        assertEquals("We held the line.", entry.body)
        assertEquals("ev.battle_win.6.1", entry.lineId)
        assertEquals("A Hard-Won Victory", entry.title)
    }

    @Test
    fun dayIndexVariesTheEntryDeterministically() {
        val composer = DiaryComposer(
            listOf(
                eventLine("ev.diary.1", "diary", text = "first"),
                eventLine("ev.diary.2", "diary", text = "second"),
            ),
        )
        val sameDay = composer.compose("diary", 3, dayIndex = 4L)
        assertEquals(sameDay, composer.compose("diary", 3, dayIndex = 4L))
        val seen = (0L..20L).map { day -> composer.compose("diary", 3, dayIndex = day).body }.toSet()
        assertTrue(seen.size >= 2)
        assertTrue(seen.all { it == "first" || it == "second" })
    }

    @Test
    fun blankEventUsesTheDefaultDiaryEvent() {
        val entry = DiaryComposer(lines).compose(event = "   ", personalityType = 6)
        assertEquals("diary", entry.event)
    }
}
