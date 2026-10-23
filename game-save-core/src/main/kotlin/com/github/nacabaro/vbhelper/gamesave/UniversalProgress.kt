package com.github.nacabaro.vbhelper.gamesave

import java.util.Base64
import java.util.Collections
import java.util.TreeMap

/** Fixed-point semantic units supplied by a versioned adapter; never raw EXP assumed equivalent across games. */
@ConsistentCopyVisibility
data class ProgressVector private constructor(val units: Map<String, Long>) {
    val isZero: Boolean get() = units.isEmpty()
    val hasRegression: Boolean get() = units.values.any { it < 0 }
    operator fun plus(other: ProgressVector) = combine(other, false)
    operator fun minus(other: ProgressVector) = combine(other, true)
    private fun combine(other: ProgressVector, subtract: Boolean): ProgressVector = of((units.keys + other.units.keys).associateWith {
        if (subtract) Math.subtractExact(units[it] ?: 0L, other.units[it] ?: 0L) else Math.addExact(units[it] ?: 0L, other.units[it] ?: 0L)
    })
    companion object {
        fun of(values: Map<String, Long> = emptyMap()): ProgressVector {
            require(values.size <= 64 && values.keys.all { it.matches(Regex("[a-z][a-z0-9_.]{0,63}")) })
            require(values.values.all { it in -1_000_000_000_000L..1_000_000_000_000L })
            return ProgressVector(Collections.unmodifiableMap(TreeMap(values.filterValues { it != 0L })))
        }
    }
}

data class ProgressCheckpoint(val normalizationPolicy: String, val nativeRecordBase64: String,
    val lastObserved: ProgressVector, val ledgerAtNativeAnchor: ProgressVector, val revision: Long)
data class UniversalProgressState(val version: Int = 1, val total: ProgressVector = ProgressVector.of(),
    val checkpoints: Map<String, ProgressCheckpoint> = emptyMap())
data class ProgressObservation(val state: UniversalProgressState, val delta: ProgressVector)
class ProgressReviewRequiredException : IllegalStateException("Progress baseline changed or a regression needs review")

/** Representation changes rebase to actual encoded/read-back values, and never award progression. */
object UniversalProgress {
    fun acceptVerifiedEntry(state: UniversalProgressState, view: String, normalizationPolicy: String,
        nativeRecord: ByteArray, actualNormalizedValues: ProgressVector): UniversalProgressState {
        require(state.version == 1 && view.isNotBlank() && view.length <= 160 && normalizationPolicy.isNotBlank())
        require(nativeRecord.size in 1..16384 && (view in state.checkpoints || state.checkpoints.size < 64))
        val checkpoint = ProgressCheckpoint(normalizationPolicy, Base64.getEncoder().encodeToString(nativeRecord),
            actualNormalizedValues, state.total, (state.checkpoints[view]?.revision ?: 0) + 1)
        return state.copy(checkpoints = state.checkpoints + (view to checkpoint))
    }

    fun observe(state: UniversalProgressState, view: String, expectedRevision: Long, normalizationPolicy: String,
        nativeRecord: ByteArray, actualNormalizedValues: ProgressVector, allowReviewedRegression: Boolean = false): ProgressObservation {
        require(state.version == 1 && nativeRecord.size in 1..16384)
        val checkpoint = state.checkpoints[view] ?: throw ProgressReviewRequiredException()
        if (checkpoint.revision != expectedRevision || checkpoint.normalizationPolicy != normalizationPolicy) throw ProgressReviewRequiredException()
        val delta = actualNormalizedValues - checkpoint.lastObserved
        if (delta.hasRegression && !allowReviewedRegression) throw ProgressReviewRequiredException()
        // Only gains actually observed in this representation advance its native anchor.
        // Observing an unchanged old save must not swallow progress earned in another game.
        val next = checkpoint.copy(nativeRecordBase64 = Base64.getEncoder().encodeToString(nativeRecord), lastObserved = actualNormalizedValues,
            ledgerAtNativeAnchor = checkpoint.ledgerAtNativeAnchor + delta, revision = checkpoint.revision + 1)
        return ProgressObservation(state.copy(total = state.total + delta, checkpoints = state.checkpoints + (view to next)), delta)
    }

    fun pendingFor(state: UniversalProgressState, view: String): ProgressVector = state.total - requireNotNull(state.checkpoints[view]).ledgerAtNativeAnchor
    fun exactNativeRecordIfUnchanged(state: UniversalProgressState, view: String): ByteArray? =
        if (pendingFor(state, view).isZero) Base64.getDecoder().decode(state.checkpoints.getValue(view).nativeRecordBase64) else null
}

/** Authored in-app influence, capped to +/-10%; a DIM basis bypasses this multiplier. */
object OriginStatInfluence {
    const val VERSION = 2
    fun fromKnownLevel(level: Int, minimum: Int, maximum: Int): Int {
        require(minimum < maximum && level in minimum..maximum)
        val fraction = (level.toLong() - minimum) * 2000 / (maximum.toLong() - minimum)
        return (9000 + fraction.toInt()).coerceIn(9000, 11000)
    }
}
