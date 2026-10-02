package com.github.nacabaro.vbhelper.world.ecosystem

import org.junit.Assert.*
import org.junit.Test

class WorldEcosystemClockTest {
    private val start = WorldEcosystemSession(seed = 42L, lastCheckpointAt = 100_000L)

    @Test fun `short gaps preserve the fractional tick and replay once`() {
        val first = WorldEcosystemClock.advance(start, 101_000L)
        assertEquals(0, first.steps)
        assertEquals(1_000L, first.session.tickRemainderMillis)
        val next = WorldEcosystemClock.advance(first.session, 101_500L)
        assertEquals(1, next.steps)
        assertEquals(1L, next.session.tickIndex)
        assertEquals(0L, next.session.tickRemainderMillis)
        assertEquals(next.session, WorldEcosystemClock.advance(next.session, 101_500L).session)
    }

    @Test fun `live and suspended processing give the same clock state`() {
        listOf(1_000L, 59_000L, 60_000L, 1_800_000L).forEach { duration ->
            var live = start
            var elapsed = 0L
            while (elapsed < duration) {
                elapsed = (elapsed + 1_000L).coerceAtMost(duration)
                live = WorldEcosystemClock.advance(live, start.lastCheckpointAt + elapsed).session
            }
            val resumed = WorldEcosystemClock.advance(start, start.lastCheckpointAt + duration).session
            assertEquals(live.tickIndex, resumed.tickIndex)
            assertEquals(live.tickRemainderMillis, resumed.tickRemainderMillis)
            assertEquals(live.lastCheckpointAt, resumed.lastCheckpointAt)
        }
        assertEquals(1_200, WorldEcosystemClock.advance(start, start.lastCheckpointAt + 1_800_000L).steps)
    }

    @Test fun `a battle freeze excludes its duration without losing partial ticks`() {
        val frozen = start.copy(tickRemainderMillis = 900L, pauseReason = WorldPauseReason.PLAYER_BATTLE)
        val returned = WorldEcosystemClock.advance(frozen, 700_000L)
        assertEquals(0, returned.steps)
        assertEquals(900L, returned.session.tickRemainderMillis)
        val resumed = returned.session.copy(pauseReason = null)
        assertEquals(1, WorldEcosystemClock.advance(resumed, 700_600L).steps)
    }

    @Test fun `hidden radar time is eligible for catch-up`() {
        val hidden = start.copy(pauseReason = WorldPauseReason.RADAR_HIDDEN)
        assertEquals(40, WorldEcosystemClock.advance(hidden, 160_000L).steps)
    }

    @Test fun `clock rollback never replays elapsed time twice`() {
        val rolledBack = WorldEcosystemClock.advance(start, 90_000L)
        assertEquals(start, rolledBack.session)
        assertEquals(0, rolledBack.steps)
        assertEquals(0, WorldEcosystemClock.advance(rolledBack.session, 100_000L).steps)
        assertEquals(1, WorldEcosystemClock.advance(rolledBack.session, 101_500L).steps)
    }

    @Test fun `over-cap gaps discard the unsimulated backlog and rebase once`() {
        val advance = WorldEcosystemClock.advance(start.copy(tickRemainderMillis = 500L), 3_700_000L)
        assertEquals(1_200, advance.steps)
        assertEquals(1_800_000L, advance.dormantMillis)
        assertEquals(0L, advance.session.tickRemainderMillis)
        assertEquals(0, WorldEcosystemClock.advance(advance.session, 3_700_000L).steps)
        assertEquals(1, WorldEcosystemClock.advance(advance.session, 3_701_500L).steps)
    }

    @Test fun `unsupported rules cannot silently replay using current formulas`() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldEcosystemClock.advance(start.copy(rulesVersion = 99), 101_500L)
        }
    }

    @Test fun `seed decisions do not depend on candidate iteration order`() {
        // Cross-language v1 fixture, independently calculated with Python struct/64-bit arithmetic.
        assertEquals(5_012_290_398_285_703_213L, EcosystemSeed.mix(42L, "wild-a", "wander", 17L))
        val ids = listOf("wild-a", "野生-b", "wild-c")
        val forward = ids.associateWith { EcosystemSeed.mix(42L, it, "wander", 17L) }
        val backward = ids.reversed().associateWith { EcosystemSeed.mix(42L, it, "wander", 17L) }
        assertEquals(forward, backward)
        assertNotEquals(forward["wild-a"], EcosystemSeed.mix(42L, "wild-a", "social", 17L))
        assertNotEquals(forward["wild-a"], EcosystemSeed.mix(42L, "wild-a", "wander", 18L))
        assertNotEquals(EcosystemSeed.mix(42L, "ab", "c", 1L), EcosystemSeed.mix(42L, "a", "bc", 1L))
    }
}
