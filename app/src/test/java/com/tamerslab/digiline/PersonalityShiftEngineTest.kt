package com.tamerslab.digiline

import com.tamerslab.digiline.engine.PersonalityShiftEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalityShiftEngineTest {
    private val now = 40L * TEST_DAY

    @Test
    fun sameInputsAlwaysProduceTheSameOutcome() {
        val points = mapOf(6 to 9, 7 to 4, 10 to 2)
        val first = PersonalityShiftEngine.apply(points, 6, mapOf("7" to 2), now, null, 3)
        val second = PersonalityShiftEngine.apply(points, 6, mapOf("7" to 2), now, null, 3)
        assertEquals(first, second)
    }

    @Test
    fun aDifferentTypeMustLeadTheCurrentTypeByAtLeastFourPoints() {
        val belowThreshold = PersonalityShiftEngine.apply(
            points = mapOf(6 to 10, 7 to 13),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 5,
        )
        assertFalse(belowThreshold.shifted)
        assertEquals(6, belowThreshold.newType)
        assertEquals(6, belowThreshold.answeredSinceShift)

        val atThreshold = PersonalityShiftEngine.apply(
            points = mapOf(6 to 10, 7 to 14),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 5,
        )
        assertTrue(atThreshold.shifted)
        assertEquals(7, atThreshold.newType)
        assertEquals(6, atThreshold.previousType)
    }

    @Test
    fun atLeastTwoAnswersAreRequiredBeforeAShift() {
        val firstAnswer = PersonalityShiftEngine.apply(
            points = mapOf(6 to 2, 7 to 10),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
        )
        assertFalse(firstAnswer.shifted)
        assertEquals(1, firstAnswer.answeredSinceShift)

        val secondAnswer = PersonalityShiftEngine.apply(
            points = mapOf(6 to 2, 7 to 10),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 1,
        )
        assertTrue(secondAnswer.shifted)
        assertEquals(0, secondAnswer.answeredSinceShift)
    }

    @Test
    fun theTwentyFourHourCooldownBlocksShifts() {
        val lastShift = now - TEST_HOUR
        val blocked = PersonalityShiftEngine.apply(
            points = mapOf(6 to 5, 7 to 15),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = lastShift,
            answeredSinceShift = 10,
        )
        assertFalse(blocked.shifted)
        assertEquals(lastShift, blocked.lastShiftAt)
        assertEquals(11, blocked.answeredSinceShift)

        val allowed = PersonalityShiftEngine.apply(
            points = mapOf(6 to 5, 7 to 15),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = now - 25L * TEST_HOUR,
            answeredSinceShift = 10,
        )
        assertTrue(allowed.shifted)
        assertEquals(now, allowed.lastShiftAt)
    }

    @Test
    fun shiftResetsTheOldTypeByTheLeadingValueAndKeepsMomentum() {
        val outcome = PersonalityShiftEngine.apply(
            points = mapOf(6 to 3, 9 to 10, 11 to 5),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 3,
        )
        assertTrue(outcome.shifted)
        assertEquals(9, outcome.newType)
        assertEquals(0, outcome.pointsOf(6))
        assertEquals(10, outcome.pointsOf(9))
        assertEquals(5, outcome.pointsOf(11))
        assertEquals(0, outcome.answeredSinceShift)
        assertEquals(9, outcome.leadingType)
        assertEquals(10, outcome.leadingPoints)
    }

    @Test
    fun pointsAreCappedAtTwenty() {
        val outcome = PersonalityShiftEngine.apply(
            points = mapOf(6 to 19),
            currentType = 6,
            optionShift = mapOf("6" to 5),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
        )
        assertEquals(PersonalityShiftEngine.MAX_POINTS, outcome.pointsOf(6))
        assertFalse(outcome.shifted)
    }

    @Test
    fun decayAppliesTenPercentPerFullDayRoundedDown() {
        val oneDay = PersonalityShiftEngine.apply(
            points = mapOf(6 to 20),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
            lastDecayAt = now - TEST_DAY,
        )
        assertEquals(18, oneDay.pointsOf(6))
        assertTrue(oneDay.decayApplied)

        val threeDays = PersonalityShiftEngine.apply(
            points = mapOf(6 to 20),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
            lastDecayAt = now - 3L * TEST_DAY,
        )
        assertEquals(14, threeDays.pointsOf(6))

        val noDayPassed = PersonalityShiftEngine.apply(
            points = mapOf(6 to 20),
            currentType = 6,
            optionShift = emptyMap(),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
            lastDecayAt = now,
        )
        assertEquals(20, noDayPassed.pointsOf(6))
        assertFalse(noDayPassed.decayApplied)
    }

    @Test
    fun decayReachesFloorZero() {
        assertEquals(emptyMap<Int, Int>(), PersonalityShiftEngine.decayPoints(mapOf(6 to 3), 5))
        assertEquals(mapOf(6 to 2), PersonalityShiftEngine.decayPoints(mapOf(6 to 3), 1))
    }

    @Test
    fun invalidKeysAndNegativeDeltasAreNormalizedAway() {
        val outcome = PersonalityShiftEngine.apply(
            points = mapOf(0 to 5, 17 to 5, 6 to -3),
            currentType = 6,
            optionShift = mapOf("x" to 3, "99" to 4, "7" to -2),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
        )
        assertTrue(outcome.points.isEmpty())
        assertTrue(outcome.points.keys.all { it in 1..16 })
    }

    @Test
    fun coresFollowTheQuadrantMapping() {
        assertEquals(1, PersonalityShiftEngine.coreOf(1))
        assertEquals(1, PersonalityShiftEngine.coreOf(4))
        assertEquals(2, PersonalityShiftEngine.coreOf(5))
        assertEquals(2, PersonalityShiftEngine.coreOf(8))
        assertEquals(3, PersonalityShiftEngine.coreOf(9))
        assertEquals(3, PersonalityShiftEngine.coreOf(12))
        assertEquals(4, PersonalityShiftEngine.coreOf(13))
        assertEquals(4, PersonalityShiftEngine.coreOf(16))
        assertEquals("VALOR", PersonalityShiftEngine.coreNameOf(6))
        assertEquals("PHILANTHROPY", PersonalityShiftEngine.coreNameOf(2))
        assertEquals(5..8, PersonalityShiftEngine.typesOfCore(2))
        // Out-of-range values are clamped, never thrown.
        assertEquals(1, PersonalityShiftEngine.coreOf(-3))
        assertEquals(4, PersonalityShiftEngine.coreOf(99))
    }

    @Test
    fun neverShiftedOutcomeKeepsNullTimestamp() {
        val outcome = PersonalityShiftEngine.apply(
            points = emptyMap(),
            currentType = 6,
            optionShift = mapOf("7" to 1),
            now = now,
            lastShiftAt = null,
            answeredSinceShift = 0,
        )
        assertNull(outcome.lastShiftAt)
        assertFalse(outcome.shifted)
        assertEquals(1, outcome.pointsOf(7))
    }
}
