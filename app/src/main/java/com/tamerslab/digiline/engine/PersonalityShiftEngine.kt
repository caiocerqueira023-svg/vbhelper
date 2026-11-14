package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.ShiftOutcome

/**
 * Deterministic 16-type personality tendency engine (plan §6.3, contract §3.1).
 *
 * Rules, in order:
 * 1. existing points are decayed by 10 % per full logical day (each day multiplies by 90 %,
 *    rounded down, floor 0);
 * 2. the answer's authored shift vector is added to the type accumulators (cap +20, floor 0);
 * 3. the safe type changes only when **all** hold:
 *    - a *different* type leads the current type by at least [SHIFT_LEAD_THRESHOLD] points,
 *    - at least [MIN_ANSWERS_BEFORE_SHIFT] answers were answered since the last shift
 *      (the current answer counts),
 *    - the last shift is at least [SHIFT_COOLDOWN_MILLIS] (24 h) old (or never happened);
 * 4. on a shift the old type's points are reduced by the leading value (floored at 0) instead of
 *    zeroing every accumulator, which prevents ping-pong (documented plan proposal).
 *
 * The engine is pure: the caller supplies `now` and the persisted counters, then stores the
 * returned normalized map/timestamps. Points are keyed by `DigimonPersonalityType.number` (1..16).
 */
object PersonalityShiftEngine {
    const val MIN_TYPE = 1
    const val MAX_TYPE = 16
    const val MAX_POINTS = 20
    const val SHIFT_LEAD_THRESHOLD = 4
    const val MIN_ANSWERS_BEFORE_SHIFT = 2
    const val SHIFT_COOLDOWN_MILLIS = 24L * 60L * 60L * 1000L
    const val DECAY_PERCENT_PER_DAY = 10

    private const val CORE_SIZE = 4
    private const val MAX_DECAY_DAYS = 400

    private val CORE_NAMES = listOf("PHILANTHROPY", "VALOR", "WISDOM", "AMICABILITY")

    /**
     * Applies one answered question.
     *
     * @param points persisted tendency points (types outside 1..16 and non-positive values are
     *   ignored).
     * @param currentType the individual's current personality type number (1..16).
     * @param optionShift the authored `shift` map of the chosen option, keyed by type number as
     *   a string (invalid keys/values are ignored).
     * @param now current logical time in epoch milliseconds.
     * @param lastShiftAt timestamp of the last accepted shift, or `null` if the individual never
     *   shifted.
     * @param answeredSinceShift number of answers already recorded since that shift (excluding the
     *   current one); the returned value is the updated counter (0 after a shift).
     * @param lastDecayAt optional timestamp of the last decay application; defaults to
     *   [lastShiftAt], then [now] (so the first call never decays).
     */
    fun apply(
        points: Map<Int, Int>,
        currentType: Int,
        optionShift: Map<String, Int>,
        now: Long,
        lastShiftAt: Long?,
        answeredSinceShift: Int,
        lastDecayAt: Long? = null,
    ): ShiftOutcome {
        val current = normalizeType(currentType)
        val prior = normalizePoints(points).toMutableMap()
        val decayAnchor = lastDecayAt ?: lastShiftAt ?: now
        val days = elapsedDays(decayAnchor, now)
        val decayApplied = days > 0 && prior.isNotEmpty()
        if (decayApplied) {
            val decayed = decayPoints(prior, days)
            prior.clear()
            prior.putAll(decayed)
        }

        for ((rawType, delta) in optionShift) {
            val type = rawType.trim().toIntOrNull() ?: continue
            if (type !in MIN_TYPE..MAX_TYPE || delta == 0) continue
            val updated = (prior[type] ?: 0) + delta
            val capped = updated.coerceIn(0, MAX_POINTS)
            if (capped == 0) prior.remove(type) else prior[type] = capped
        }

        val answersSinceShift = (answeredSinceShift.coerceAtLeast(0)) + 1
        val leader = leadingTypeExcluding(prior, current)
        val leaderPoints = leader?.let { prior[it] ?: 0 } ?: 0
        val currentPoints = prior[current] ?: 0
        val gap = leaderPoints - currentPoints
        val answersReady = answersSinceShift >= MIN_ANSWERS_BEFORE_SHIFT
        val cooldownReady = lastShiftAt == null || now - lastShiftAt >= SHIFT_COOLDOWN_MILLIS

        if (leader != null && gap >= SHIFT_LEAD_THRESHOLD && answersReady && cooldownReady) {
            // Hysteresis reset: reduce the old current type by the leading value instead of
            // zeroing every accumulator (prevents immediate flip-flop while keeping momentum).
            val reducedCurrent = (currentPoints - leaderPoints).coerceAtLeast(0)
            if (reducedCurrent == 0) prior.remove(current) else prior[current] = reducedCurrent
            return ShiftOutcome(
                points = sortedPoints(prior),
                previousType = current,
                newType = leader,
                shifted = true,
                lastShiftAt = now,
                answeredSinceShift = 0,
                decayApplied = decayApplied,
                leadingType = leader,
                leadingPoints = leaderPoints,
            )
        }

        return ShiftOutcome(
            points = sortedPoints(prior),
            previousType = current,
            newType = current,
            shifted = false,
            lastShiftAt = lastShiftAt,
            answeredSinceShift = answersSinceShift,
            decayApplied = decayApplied,
            leadingType = leader,
            leadingPoints = leaderPoints,
        )
    }

    /** Core index 1..4 of a personality type: 1 PHILANTHROPY, 2 VALOR, 3 WISDOM, 4 AMICABILITY. */
    fun coreOf(type: Int): Int = ((normalizeType(type) - 1) / CORE_SIZE) + 1

    /** English core name of a personality type (matches the authored `core` field casing). */
    fun coreNameOf(type: Int): String = CORE_NAMES[coreOf(type) - 1]

    /** Personality types of one core (1-based core). */
    fun typesOfCore(core: Int): IntRange {
        val normalized = core.coerceIn(1, CORE_SIZE)
        val first = (normalized - 1) * CORE_SIZE + 1
        return first until first + CORE_SIZE
    }

    /** Clamps any value into the valid type range. */
    fun normalizeType(type: Int): Int = type.coerceIn(MIN_TYPE, MAX_TYPE)

    /** Keeps only valid, positive, capped type entries; ascending type order (deterministic). */
    fun normalizePoints(points: Map<Int, Int>): Map<Int, Int> {
        val normalized = LinkedHashMap<Int, Int>()
        for (type in MIN_TYPE..MAX_TYPE) {
            val value = points[type] ?: continue
            if (value <= 0) continue
            normalized[type] = value.coerceAtMost(MAX_POINTS)
        }
        return normalized
    }

    /** Applies [days] full days of 10 %-per-day decay (each day: `floor(points * 0.9)`). */
    fun decayPoints(points: Map<Int, Int>, days: Int): Map<Int, Int> {
        if (days <= 0) return normalizePoints(points)
        val result = normalizePoints(points).toMutableMap()
        val effectiveDays = days.coerceAtMost(MAX_DECAY_DAYS)
        repeat(effectiveDays) {
            var allZero = true
            for (type in MIN_TYPE..MAX_TYPE) {
                val value = result[type] ?: continue
                val decayed = value * (100 - DECAY_PERCENT_PER_DAY) / 100
                if (decayed > 0) {
                    result[type] = decayed
                    allZero = false
                } else {
                    result.remove(type)
                }
            }
            if (allZero) return emptyMap()
        }
        return result
    }

    /** Full days elapsed between two timestamps; negative/clock-skewed ranges count as 0. */
    fun elapsedDays(from: Long, to: Long): Int {
        if (to <= from) return 0
        val delta = to - from
        if (delta < DigilineSeed.DAY_MILLIS) return 0
        return (delta / DigilineSeed.DAY_MILLIS).coerceAtMost(MAX_DECAY_DAYS.toLong()).toInt()
    }

    private fun leadingTypeExcluding(points: Map<Int, Int>, excludedType: Int): Int? {
        var leader: Int? = null
        var leaderPoints = 0
        for (type in MIN_TYPE..MAX_TYPE) {
            if (type == excludedType) continue
            val value = points[type] ?: continue
            // Ascending iteration + strict comparison => smallest type wins ties (deterministic).
            if (value > leaderPoints) {
                leader = type
                leaderPoints = value
            }
        }
        return leader
    }

    private fun sortedPoints(points: Map<Int, Int>): Map<Int, Int> {
        val sorted = LinkedHashMap<Int, Int>()
        for (type in MIN_TYPE..MAX_TYPE) {
            val value = points[type] ?: continue
            if (value > 0) sorted[type] = value
        }
        return sorted
    }
}
