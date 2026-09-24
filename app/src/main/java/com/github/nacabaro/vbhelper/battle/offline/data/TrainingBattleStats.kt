package com.github.nacabaro.vbhelper.battle.offline.data

import kotlin.math.roundToInt

enum class VitalStatScale { DIM, BEM }

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
    val mood: Int = 50
) {
    val hasUsableBaseStats: Boolean
        get() = listOf(baseHp, baseBp, baseAp).all { it in 1 until UNKNOWN_CARD_STAT }

    private companion object {
        const val UNKNOWN_CARD_STAT = 65_535
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
data class TrainingBattleStats(val health: Int, val attack: Int, val defense: Int, val energy: Int) {
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
            return TrainingBattleStats(
                health = 900 + level * 180,
                attack = 110 + level * 14,
                defense = 40 + level * 6,
                energy = 300 + level * 40
            )
        }

        fun forParticipant(stage: Int, vitalStats: VitalBattleProfile?): TrainingBattleStats {
            val baseline = forStage(stage)
            if (vitalStats == null || !vitalStats.hasUsableBaseStats || stage < 2) return baseline

            val bemPhase = stage.coerceIn(2, averageBemStats.lastIndex)
            val equivalent = when (vitalStats.scale) {
                VitalStatScale.DIM -> convertDimProfile(vitalStats, bemPhase)
                VitalStatScale.BEM -> BraceletStats(
                    bp = vitalStats.baseBp + vitalStats.trainingBp.coerceIn(0, MAX_BE_TRAINING).toFloat(),
                    hp = vitalStats.baseHp + vitalStats.trainingHp.coerceIn(0, MAX_BE_TRAINING).toFloat(),
                    ap = vitalStats.baseAp + vitalStats.trainingAp.coerceIn(0, MAX_BE_TRAINING).toFloat()
                )
            }
            val average = averageBemStats[bemPhase]
            val healthRatio = relativeToAverage(equivalent.hp, average.hp) * vitalHealthMultiplier(vitalStats.vitalPoints)
            val attackRatio = relativeToAverage(equivalent.ap, average.ap) * moodAttackMultiplier(vitalStats.mood)
            val defenseRatio = relativeToAverage(equivalent.bp, average.bp)

            return TrainingBattleStats(
                health = (baseline.health * healthRatio).roundToInt().coerceAtLeast(1),
                attack = (baseline.attack * attackRatio).roundToInt().coerceAtLeast(1),
                defense = (baseline.defense * defenseRatio).roundToInt().coerceAtLeast(0),
                energy = baseline.energy
            )
        }

        private fun convertDimProfile(profile: VitalBattleProfile, bemPhase: Int): BraceletStats {
            val dimPhase = bemPhase.coerceAtMost(maxDimStats.lastIndex)
            return BraceletStats(
                bp = convertDimStat(profile.baseBp, dimPhase, bemPhase) { it.bp },
                hp = convertDimStat(profile.baseHp, dimPhase, bemPhase) { it.hp },
                ap = convertDimStat(profile.baseAp, dimPhase, bemPhase) { it.ap }
            )
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
            return (value / average).coerceIn(MIN_RELATIVE_STAT, MAX_RELATIVE_STAT)
        }

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
