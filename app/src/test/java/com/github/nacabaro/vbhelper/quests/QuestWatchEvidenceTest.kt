package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Assert.*
import org.junit.Test

class QuestWatchEvidenceTest {
    private fun baseline(family: DeviceType = DeviceType.BEDevice) = QuestWatchBaseline(
        "token", "partner", 2, "2:2026:10:1;", 20, 5, 8,
        if (family == DeviceType.VBDevice) 30 else null, 1000,
        family = family, cardId = 7, adventureNext = 4, adventureLimit = 16)

    private fun observation() = QuestWatchObservation(DeviceType.BEDevice, 7, 2, "2:2026:10:1;", 23, 7, 12, null, 6)

    @Test fun `only outgoing to incoming differences count as new activity`() {
        val delta = QuestWatchEvidence.evaluate(baseline(), observation())
        assertEquals(5, delta.battles)
        assertEquals(3, delta.wins)
        assertEquals(4, delta.ppEarned)
        assertEquals(12, delta.ppCurrent)
        assertEquals(2, delta.adventureAdvance)
    }

    @Test fun `app totals already included in outgoing data cannot count again`() {
        val sent = baseline().copy(won = 1000, lost = 800, trophies = 500, adventureNext = 10)
        val returned = observation().copy(won = 1000, lost = 800, trophies = 500, adventureNext = 10)
        val delta = QuestWatchEvidence.evaluate(sent, returned)
        assertEquals(0, delta.battles)
        assertEquals(0, delta.ppEarned)
        assertEquals(0, delta.adventureAdvance)
    }

    @Test fun `evolution does not invent PP earned in a previous form`() {
        val delta = QuestWatchEvidence.evaluate(baseline(), observation().copy(charIndex = 3, history = "2:2026:10:1;3:2026:10:2;", trophies = 20))
        assertNull(delta.ppEarned)
        assertEquals(20, delta.ppCurrent)
        assertEquals(5, delta.battles)
        assertTrue(QuestWatchNotice.PP_DISCONTINUITY in delta.notices)
    }

    @Test fun `same index with a different history is not a continuous form`() {
        assertNull(QuestWatchEvidence.evaluate(baseline(), observation().copy(history = "2:2026:10:2;")).ppEarned)
    }

    @Test fun `counter decreases never become positive activity`() {
        val delta = QuestWatchEvidence.evaluate(baseline(), observation().copy(won = 19, trophies = 3, adventureNext = 2))
        assertNull(delta.battles)
        assertNull(delta.wins)
        assertNull(delta.ppEarned)
        assertNull(delta.adventureAdvance)
    }

    @Test fun `VB lifetime trophies can count across evolution but are not BE PP`() {
        val returned = observation().copy(family = DeviceType.VBDevice, charIndex = 3, history = "2:2026:10:1;3:2026:10:2;",
            trophies = 1, lifetimeTrophies = 34)
        val delta = QuestWatchEvidence.evaluate(baseline(DeviceType.VBDevice), returned)
        assertEquals(4, delta.trophiesEarned)
        assertNull(delta.ppEarned)
        assertNull(delta.ppCurrent)
    }

    @Test fun `card wide progress cannot satisfy another card or missing baseline`() {
        assertNull(QuestWatchEvidence.evaluate(baseline(), observation().copy(cardId = 9)).adventureAdvance)
        assertNull(QuestWatchEvidence.evaluate(baseline().copy(adventureNext = null), observation()).adventureAdvance)
        assertNull(QuestWatchEvidence.evaluate(baseline().copy(cardId = null), observation()).adventureAdvance)
    }

    @Test fun `only valid next-area bounds count including the final clear sentinel`() {
        assertEquals(1, QuestWatchEvidence.evaluate(baseline().copy(adventureNext = 15), observation().copy(adventureNext = 16)).adventureAdvance)
        assertNull(QuestWatchEvidence.evaluate(baseline(), observation().copy(adventureNext = 255)).adventureAdvance)
        assertNull(QuestWatchEvidence.evaluate(baseline().copy(adventureNext = 0), observation()).adventureAdvance)
    }

    @Test fun `changed device family cannot create watch evidence`() {
        val delta = QuestWatchEvidence.evaluate(baseline(), observation().copy(family = DeviceType.VBDevice, lifetimeTrophies = 300))
        assertNull(delta.battles)
        assertNull(delta.ppCurrent)
        assertNull(delta.adventureAdvance)
        assertTrue(QuestWatchNotice.DEVICE_MISMATCH in delta.notices)
    }

    @Test fun `legacy baselines preserve battle evidence without guessing new PP and adventure fields`() {
        val old = baseline().copy(family = null, cardId = null, adventureNext = null, adventureLimit = null)
        val delta = QuestWatchEvidence.evaluate(old, observation())
        assertEquals(5, delta.battles)
        assertNull(delta.ppEarned)
        assertNull(delta.adventureAdvance)
    }
}
