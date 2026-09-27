package com.github.nacabaro.vbhelper.battle.offline.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

enum class VitalStatScale { DIM, BEM, ARENA_EXTRACTED, STAGE_FALLBACK }

/** Battle-relevant values read from the card and the character stored by the bracelet. */
data class VitalBattleProfile(
    val scale: VitalStatScale,
    val baseHp: Int,
    val baseBp: Int,
    val baseAp: Int,
    val trainingHp: Int = 0,
    val trainingBp: Int = 0,
    val trainingAp: Int = 0,
    val vitalPoints: Int = 0,
    val mood: Int = 50,
    val sourcePhase: Int? = null
) {
    val hasAnyUsableBaseStats: Boolean
        get() = listOf(baseHp, baseBp, baseAp).any(::isKnownBaseStat)

    val hasUsableBaseStats: Boolean
        get() = hasAnyUsableBaseStats

    companion object {
        private const val UNKNOWN_CARD_STAT = 65_535

        fun isKnownBaseStat(value: Int): Boolean = value in 1 until UNKNOWN_CARD_STAT
    }

}

/**
 * Compact real-time scale used by the simulator.
 *
 * Original DiM stats and BEM stats are different units. DiM values are first
 * projected into the BEM range for the same phase, preserving their relative
 * position in the DiM range. Only then are both generations reduced to this
 * simulator's compact HP/AP/BP scale.
 */
data class TrainingBattleStats(
    val health: Int,
    val attack: Int,
    val defense: Int,
    val energy: Int,
    val energyRegenerationPerSecond: Float,
    val movementSpeed: Float,
    val cooldownMultiplier: Float,
    val decisionDelayMinMillis: Long,
    val decisionDelayMaxMillis: Long
) {
    companion object {
        private const val MAX_BE_TRAINING = 999
        private const val MIN_RELATIVE_STAT = 0.65f
        private const val MAX_RELATIVE_STAT = 1.75f

        private data class BraceletStats(val bp: Float, val hp: Float, val ap: Float)

        // Published by VitalWear's DiM-to-BEM conversion. Indices are phases I..VI.
        private val minDimStats = listOf(
            BraceletStats(0f, 0f, 0f),
            BraceletStats(0f, 0f, 0f),
            BraceletStats(10f, 3f, 2f),
            BraceletStats(15f, 4f, 2f),
            BraceletStats(25f, 6f, 3f),
            BraceletStats(45f, 10f, 5f)
        )
        private val averageDimStats = listOf(
            BraceletStats(0f, 0f, 0f),
            BraceletStats(0f, 0f, 0f),
            BraceletStats(10f, 3f, 2f),
            BraceletStats(20.2727f, 4.5182f, 2.5727f),
            BraceletStats(32.9221f, 9.6494f, 4.0519f),
            BraceletStats(52.4771f, 13.5963f, 7.1743f)
        )
        private val maxDimStats = listOf(
            BraceletStats(0f, 0f, 0f),
            BraceletStats(0f, 0f, 0f),
            BraceletStats(10f, 3f, 2f),
            BraceletStats(25f, 6f, 3f),
            BraceletStats(40f, 12f, 6f),
            BraceletStats(70f, 22f, 12f)
        )
        private val bemStandardDeviation = listOf(
            BraceletStats(0f, 0f, 0f),
            BraceletStats(0f, 0f, 0f),
            BraceletStats(421.3150f, 249.2136f, 72.4568f),
            BraceletStats(470.1532f, 213.6744f, 66.5749f),
            BraceletStats(616.0121f, 367.4271f, 89.2335f),
            BraceletStats(679.0475f, 429.7718f, 98.3021f),
            BraceletStats(705.7586f, 498.9274f, 53.4522f)
        )
        private val averageBemStats = listOf(
            BraceletStats(0f, 0f, 0f),
            BraceletStats(0f, 0f, 0f),
            BraceletStats(4_657.5f, 3_005.5f, 1_022.5f),
            BraceletStats(5_171.528f, 3_496.5278f, 1_222.9167f),
            BraceletStats(5_664.4f, 4_408f, 1_505.7333f),
            BraceletStats(5_972.2974f, 5_540.5405f, 1_976.7568f),
            BraceletStats(6_314.2856f, 5_935.7144f, 2_092.8572f)
        )

        fun forStage(stage: Int): TrainingBattleStats {
            val level = stage.coerceIn(0, 6)
            return buildStats(level, hpRatio = 1f, bpRatio = 1f, apRatio = 1f)
        }

        fun forParticipant(stage: Int, vitalStats: VitalBattleProfile?): TrainingBattleStats {
            val baseline = forStage(stage)
            if (vitalStats == null || vitalStats.scale == VitalStatScale.STAGE_FALLBACK) return baseline
            val isArenaExtracted = vitalStats.scale == VitalStatScale.ARENA_EXTRACTED
            if (!isArenaExtracted && stage < 2) return baseline

            val bemPhase = stage.coerceIn(2, averageBemStats.lastIndex)
            val average = if (isArenaExtracted) {
                val sourcePhase = vitalStats.sourcePhase ?: return baseline
                OfflineBattleRuleset.arenaExtractedMediansByPhase
                    .getOrNull(sourcePhase - 1)
                    ?.let { BraceletStats(it.bp, it.hp, it.ap) }
                    ?.takeIf { it.hp > 0f && it.bp > 0f && it.ap > 0f }
                    ?: return baseline
            } else {
                averageBemStats[bemPhase]
            }
            val equivalent = when (vitalStats.scale) {
                VitalStatScale.DIM -> convertDimProfile(vitalStats, bemPhase, average)
                VitalStatScale.BEM -> BraceletStats(
                    bp = resolveBemStat(vitalStats.baseBp, vitalStats.trainingBp, average.bp),
                    hp = resolveBemStat(vitalStats.baseHp, vitalStats.trainingHp, average.hp),
                    ap = resolveBemStat(vitalStats.baseAp, vitalStats.trainingAp, average.ap)
                )
                VitalStatScale.ARENA_EXTRACTED -> BraceletStats(
                    bp = resolveArenaStat(vitalStats.baseBp, average.bp),
                    hp = resolveArenaStat(vitalStats.baseHp, average.hp),
                    ap = resolveArenaStat(vitalStats.baseAp, average.ap)
                )
                VitalStatScale.STAGE_FALLBACK -> return baseline
            }
            val hpRatio = relativeToAverage(equivalent.hp, average.hp)
            val bpRatio = relativeToAverage(equivalent.bp, average.bp)
            val apRatio = relativeToAverage(equivalent.ap, average.ap)
            val derived = buildStats(stage.coerceIn(0, 6), hpRatio, bpRatio, apRatio)

            return derived.copy(
                health = (derived.health * vitalHealthMultiplier(vitalStats.vitalPoints)).roundToInt().coerceAtLeast(1),
                attack = (derived.attack * moodAttackMultiplier(vitalStats.mood)).roundToInt().coerceAtLeast(1)
            )
        }

        private fun buildStats(level: Int, hpRatio: Float, bpRatio: Float, apRatio: Float): TrainingBattleStats {
            val healthBaseline = 900 + level * 180
            val attackBaseline = 110 + level * 14
            val defenseBaseline = 40 + level * 6
            val energyBaseline = 300 + level * 40

            // Decode separates HP, MP and Speed. The bracelet only provides
            // HP/BP/AP, so these secondary attributes are explicit composites:
            // HP adds capacity but physical mass costs tempo; BP favors sustained
            // control; AP favors explosive action. Geometric weights preserve 1.0
            // as the phase average and prevent one axis from dominating linearly.
            val energyRatio = weightedGeometricRatio(
                hpRatio to 0.25f,
                bpRatio to 0.40f,
                apRatio to 0.35f
            ).coerceIn(0.85f, 1.20f)
            val regenerationRatio = weightedGeometricRatio(
                hpRatio to -0.25f,
                bpRatio to 0.45f,
                apRatio to 0.30f
            ).coerceIn(0.82f, 1.22f)
            val tempo = tempoSignal(hpRatio, bpRatio, apRatio)

            val movementMinimum = OfflineBattleRuleset.MOVEMENT_STAGE_ZERO_MIN +
                level * OfflineBattleRuleset.MOVEMENT_STAGE_STEP
            val movementSpeed = movementMinimum +
                ((tempo + 1f) * 0.5f) * OfflineBattleRuleset.MOVEMENT_BAND_WIDTH
            val cooldownCenter = OfflineBattleRuleset.COOLDOWN_STAGE_ZERO_CENTER -
                level * OfflineBattleRuleset.COOLDOWN_STAGE_STEP
            val cooldownMultiplier = cooldownCenter - tempo * OfflineBattleRuleset.COOLDOWN_BAND_HALF_WIDTH
            val decisionDelayMin = ((1_200L - level * 80L) * cooldownMultiplier)
                .roundToInt().toLong().coerceAtLeast(350L)
            val decisionDelayMax = ((2_400L - level * 120L) * cooldownMultiplier)
                .roundToInt().toLong().coerceAtLeast(decisionDelayMin)

            return TrainingBattleStats(
                health = (healthBaseline * hpRatio).roundToInt().coerceAtLeast(1),
                attack = (attackBaseline * apRatio).roundToInt().coerceAtLeast(1),
                defense = (defenseBaseline * bpRatio).roundToInt().coerceAtLeast(0),
                energy = (energyBaseline * energyRatio).roundToInt().coerceAtLeast(1),
                energyRegenerationPerSecond = (
                    OfflineBattleRuleset.ENERGY_REGEN_STAGE_ZERO +
                        level * OfflineBattleRuleset.ENERGY_REGEN_STAGE_STEP
                    ) * regenerationRatio,
                movementSpeed = movementSpeed,
                cooldownMultiplier = cooldownMultiplier,
                decisionDelayMinMillis = decisionDelayMin,
                decisionDelayMaxMillis = decisionDelayMax
            )
        }

        private fun weightedGeometricRatio(vararg weightedRatios: Pair<Float, Float>): Float =
            exp(weightedRatios.sumOf { (ratio, weight) -> weight * ln(ratio.toDouble()) }).toFloat()

        private fun tempoSignal(hpRatio: Float, bpRatio: Float, apRatio: Float): Float {
            val maximumLog = ln(compressedRatio(MAX_RELATIVE_STAT).toDouble())
            if (maximumLog <= 0.0) return 0f
            return ((
                0.55 * ln(apRatio.toDouble()) +
                    0.25 * ln(bpRatio.toDouble()) -
                    0.65 * ln(hpRatio.toDouble())
                ) / maximumLog).toFloat().coerceIn(-1f, 1f)
        }

        private fun convertDimProfile(
            profile: VitalBattleProfile,
            bemPhase: Int,
            average: BraceletStats
        ): BraceletStats {
            val dimPhase = bemPhase.coerceAtMost(maxDimStats.lastIndex)
            return BraceletStats(
                bp = resolveDimStat(profile.baseBp, dimPhase, bemPhase, average.bp) { it.bp },
                hp = resolveDimStat(profile.baseHp, dimPhase, bemPhase, average.hp) { it.hp },
                ap = resolveDimStat(profile.baseAp, dimPhase, bemPhase, average.ap) { it.ap }
            )
        }

        private fun resolveBemStat(rawStat: Int, training: Int, average: Float): Float {
            val base = if (VitalBattleProfile.isKnownBaseStat(rawStat)) rawStat.toFloat() else average
            return base + training.coerceIn(0, MAX_BE_TRAINING).toFloat()
        }

        private fun resolveArenaStat(rawStat: Int, average: Float): Float =
            if (VitalBattleProfile.isKnownBaseStat(rawStat)) rawStat.toFloat() else average

        private fun resolveDimStat(
            rawStat: Int,
            dimPhase: Int,
            bemPhase: Int,
            fallback: Float,
            select: (BraceletStats) -> Float
        ): Float = if (VitalBattleProfile.isKnownBaseStat(rawStat)) {
            convertDimStat(rawStat, dimPhase, bemPhase, select)
        } else {
            fallback
        }

        private fun convertDimStat(
            rawStat: Int,
            originalDimPhase: Int,
            originalBemPhase: Int,
            select: (BraceletStats) -> Float
        ): Float {
            var dimPhase = originalDimPhase
            var bemPhase = originalBemPhase
            if (bemPhase == dimPhase && dimPhase < averageDimStats.lastIndex &&
                rawStat > select(averageDimStats[dimPhase + 1])) {
                dimPhase++
                bemPhase++
            }
            val minimum = select(minDimStats[dimPhase])
            val maximum = select(maxDimStats[dimPhase])
            val range = maximum - minimum
            val bemAverage = select(averageBemStats[bemPhase])
            if (range <= 0f) return bemAverage
            val percentile = (rawStat - minimum) / range
            val deviation = select(bemStandardDeviation[bemPhase])
            return percentile * deviation * 2f + (bemAverage - deviation)
        }

        private fun relativeToAverage(value: Float, average: Float): Float {
            if (!value.isFinite() || average <= 0f) return 1f
            return compressedRatio((value / average).coerceIn(MIN_RELATIVE_STAT, MAX_RELATIVE_STAT))
        }

        private fun compressedRatio(ratio: Float): Float =
            exp(OfflineBattleRuleset.STAT_COMPRESSION * ln(ratio.toDouble())).toFloat()

        private fun vitalHealthMultiplier(vitalPoints: Int): Float = when {
            vitalPoints > 7_500 -> 1.10f
            vitalPoints > 5_000 -> 1.06f
            vitalPoints > 2_500 -> 1.03f
            else -> 1f
        }

        private fun moodAttackMultiplier(mood: Int): Float = when {
            mood > 70 -> 1.05f
            mood > 20 -> 1f
            else -> 0.95f
        }
    }
}
