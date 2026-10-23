package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class UniversalProgressTest {
    private fun vector(value: Long) = ProgressVector.of(mapOf("growth" to value))
    private fun record(value: Int) = ByteBuffer.allocate(8).putInt(value).putInt(0x12345678).array()
    private fun entry(state: UniversalProgressState, view: String, value: Int, scale: Long) =
        UniversalProgress.acceptVerifiedEntry(state, view, "$view-v1", record(value), vector(value * scale))

    @Test fun zeroProgressAtoBtoCtoAIsBitExactDespiteLossyIntermediateScales() {
        val a = record(1234)
        var state = entry(UniversalProgressState(), "a", 1234, 1)
        state = entry(state, "b", 12, 100)
        state = UniversalProgress.observe(state, "b", state.checkpoints.getValue("b").revision, "b-v1", record(12), vector(1200)).state
        state = entry(state, "storage-dim-basis", 50, 3)
        state = entry(state, "c", 176, 7)
        state = UniversalProgress.observe(state, "c", state.checkpoints.getValue("c").revision, "c-v1", record(176), vector(1232)).state
        assertTrue(state.total.isZero)
        assertArrayEquals(a, UniversalProgress.exactNativeRecordIfUnchanged(state, "a"))
    }

    @Test fun actualGameProgressIsCountedOnceAndRemainsPendingForOlderRepresentations() {
        var state = entry(UniversalProgressState(), "a", 1234, 1)
        state = entry(state, "b", 12, 100)
        val observed = UniversalProgress.observe(state, "b", state.checkpoints.getValue("b").revision, "b-v1", record(13), vector(1300))
        assertEquals(vector(100), observed.delta)
        state = observed.state
        assertEquals(vector(100), UniversalProgress.pendingFor(state, "a"))
        assertNull(UniversalProgress.exactNativeRecordIfUnchanged(state, "a"))
        assertArrayEquals(record(13), UniversalProgress.exactNativeRecordIfUnchanged(state, "b"))
        state = UniversalProgress.observe(state, "a", state.checkpoints.getValue("a").revision, "a-v1", record(1234), vector(1234)).state
        assertEquals(vector(100), UniversalProgress.pendingFor(state, "a"))
        state = entry(state, "c", 190, 7) // Adapter maps foundation + gains, then records actual encoded values.
        assertEquals(vector(100), state.total)
        state = entry(state, "a", 1334, 1) // Verified return readback rebases without awarding the same gains again.
        assertArrayEquals(record(1334), UniversalProgress.exactNativeRecordIfUnchanged(state, "a"))
        assertTrue(UniversalProgress.observe(state, "a", state.checkpoints.getValue("a").revision, "a-v1", record(1334), vector(1334)).delta.isZero)
    }

    @Test fun staleOrRegressedObservationsRequireReview() {
        val state = entry(UniversalProgressState(), "a", 1234, 1)
        assertThrows(ProgressReviewRequiredException::class.java) { UniversalProgress.observe(state, "a", 0, "a-v1", record(1234), vector(1234)) }
        assertThrows(ProgressReviewRequiredException::class.java) { UniversalProgress.observe(state, "a", 1, "a-v1", record(1000), vector(1000)) }
        assertThrows(ProgressReviewRequiredException::class.java) { UniversalProgress.observe(state, "a", 1, "changed-policy", record(1234), vector(1234)) }
    }

    @Test fun originInfluenceIsBalancedAndDoesNotBecomeEarnedProgress() {
        assertEquals(9000, OriginStatInfluence.fromKnownLevel(1, 1, 99))
        assertEquals(11000, OriginStatInfluence.fromKnownLevel(99, 1, 99))
        assertEquals(10000, OriginStatInfluence.fromKnownLevel(50, 1, 99))
        val state = entry(UniversalProgressState(), "a", 999, 1)
        OriginStatInfluence.fromKnownLevel(99, 1, 99)
        assertTrue(state.total.isZero)
    }
}
