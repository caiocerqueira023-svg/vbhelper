package com.github.nacabaro.vbhelper.battle.offline.diagnostics

import com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSide
import com.github.nacabaro.vbhelper.battle.offline.core.BattleDamageFormula
import com.github.nacabaro.vbhelper.battle.offline.data.OfflineBattleRuleset
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.battle.offline.data.VitalBattleProfile
import com.github.nacabaro.vbhelper.battle.offline.data.VitalStatScale
import kotlin.math.roundToInt

data class BattleBalanceSample(
    val rulesetVersion: Int,
    val seed: Long,
    val sideSwapped: Boolean,
    val firstId: String,
    val secondId: String,
    val firstPersonality: String,
    val secondPersonality: String,
    val firstRawHp: Int?,
    val firstRawBp: Int?,
    val firstRawAp: Int?,
    val secondRawHp: Int?,
    val secondRawBp: Int?,
    val secondRawAp: Int?,
    val firstFallbackHp: Boolean,
    val firstFallbackBp: Boolean,
    val firstFallbackAp: Boolean,
    val secondFallbackHp: Boolean,
    val secondFallbackBp: Boolean,
    val secondFallbackAp: Boolean,
    val firstBattleHp: Int,
    val firstBattleBp: Int,
    val firstBattleAp: Int,
    val firstBattleEnergy: Int,
    val firstEnergyRegeneration: Float,
    val firstMovementSpeed: Float,
    val firstCooldownMultiplier: Float,
    val secondBattleHp: Int,
    val secondBattleBp: Int,
    val secondBattleAp: Int,
    val secondBattleEnergy: Int,
    val secondEnergyRegeneration: Float,
    val secondMovementSpeed: Float,
    val secondCooldownMultiplier: Float,
    val outcome: String,
    val winner: String,
    val durationMillis: Long,
    val firstRemainingHealthRatio: Float,
    val secondRemainingHealthRatio: Float,
    val damageDealt: Int,
    val damageReceived: Int,
    val damagePrevented: Int,
    val projectilesHit: Int,
    val projectilesMissed: Int,
    val firstTechniqueUses: Map<String, Int>,
    val secondTechniqueUses: Map<String, Int>,
    val firstMeanReadiness: Float,
    val secondMeanReadiness: Float,
    val firstMeanDistance: Float,
    val secondMeanDistance: Float,
    val firstIncapacitatedMillis: Long,
    val secondIncapacitatedMillis: Long,
    val firstPositioningReplans: Int,
    val secondPositioningReplans: Int,
    val firstCounterCount: Int,
    val secondCounterCount: Int,
    val catalogVersion: Int,
    val damageFormula: String
)

data class BattleBalanceSummary(
    val battles: Int,
    val firstWins: Int,
    val secondWins: Int,
    val draws: Int,
    val timeouts: Int,
    val firstWinRate: Float,
    val medianDurationMillis: Long,
    val p95DurationMillis: Long
)

/**
 * Developer-only simulation harness. It is compiled into debug builds only and
 * never writes battle telemetry into user storage.
 */
object BattleBalanceRunner {
    private const val FRAME_MILLIS = 50L

    fun runPairedMatrix(
        first: TrainingParticipantInput,
        second: TrainingParticipantInput,
        seeds: Iterable<Long>,
        maxDurationMillis: Long = BattleConfiguration().maxDurationMillis,
        rulesetVersion: Int = OfflineBattleRuleset.VERSION,
        damageFormula: BattleDamageFormula = BattleDamageFormula.ADAPTED
    ): List<BattleBalanceSample> = buildList {
        require(maxDurationMillis >= 2_000L)
        seeds.forEach { seed ->
            add(runBattle(first, second, seed, sideSwapped = false, maxDurationMillis = maxDurationMillis,
                rulesetVersion = rulesetVersion, damageFormula = damageFormula))
            add(runBattle(first, second, seed, sideSwapped = true, maxDurationMillis = maxDurationMillis,
                rulesetVersion = rulesetVersion, damageFormula = damageFormula))
        }
    }

    fun summarize(samples: List<BattleBalanceSample>): BattleBalanceSummary {
        require(samples.isNotEmpty()) { "A matriz precisa conter ao menos uma batalha." }
        val durations = samples.map { it.durationMillis }.sorted()
        val firstWins = samples.count { it.winner == "FIRST" }
        return BattleBalanceSummary(
            battles = samples.size,
            firstWins = firstWins,
            secondWins = samples.count { it.winner == "SECOND" },
            draws = samples.count { it.winner == "DRAW" },
            timeouts = samples.count { it.winner == "TIMEOUT" },
            firstWinRate = firstWins.toFloat() / samples.size,
            medianDurationMillis = percentile(durations, 0.50f),
            p95DurationMillis = percentile(durations, 0.95f)
        )
    }

    fun toCsv(samples: List<BattleBalanceSample>): String = buildString {
        appendLine(CSV_HEADER)
        samples.forEach { sample ->
            appendLine(sample.csvRow())
        }
    }

    private fun runBattle(
        first: TrainingParticipantInput,
        second: TrainingParticipantInput,
        seed: Long,
        sideSwapped: Boolean,
        maxDurationMillis: Long,
        rulesetVersion: Int,
        damageFormula: BattleDamageFormula
    ): BattleBalanceSample {
        val firstSide = if (sideSwapped) BattleSide.OPPOSING else BattleSide.ALLIED
        val secondSide = if (sideSwapped) BattleSide.ALLIED else BattleSide.OPPOSING
        val firstDefinition = TrainingBattleFactory.definition(first, firstSide)
        val secondDefinition = TrainingBattleFactory.definition(second, secondSide)
        val simulator = TrainingBattleFactory.create(
            allies = listOf(if (sideSwapped) second else first),
            opponents = listOf(if (sideSwapped) first else second),
            configuration = BattleConfiguration(
                randomSeed = seed,
                defaultPaused = false,
                maxDurationMillis = maxDurationMillis,
                rulesetVersion = rulesetVersion,
                damageFormula = damageFormula
            )
        )

        var snapshot = simulator.snapshot()
        while (snapshot.result == null && snapshot.elapsedMillis < maxDurationMillis) {
            simulator.advance(FRAME_MILLIS)
            snapshot = simulator.snapshot()
        }

        val firstSnapshot = if (sideSwapped) {
            snapshot.opposingMembers.single()
        } else {
            snapshot.alliedMembers.single()
        }
        val secondSnapshot = if (sideSwapped) {
            snapshot.alliedMembers.single()
        } else {
            snapshot.opposingMembers.single()
        }
        val result = snapshot.result
        val winner = when (result?.outcome) {
            BattleOutcome.ALLIED_VICTORY -> if (sideSwapped) "SECOND" else "FIRST"
            BattleOutcome.OPPOSING_VICTORY -> if (sideSwapped) "FIRST" else "SECOND"
            BattleOutcome.DRAW -> if (result.elapsedMillis >= maxDurationMillis) "TIMEOUT" else "DRAW"
            BattleOutcome.ABANDONED -> "ABANDONED"
            null -> "TIMEOUT"
        }

        return BattleBalanceSample(
            rulesetVersion = snapshot.rulesetVersion,
            seed = seed,
            sideSwapped = sideSwapped,
            firstId = first.stableRngKey,
            secondId = second.stableRngKey,
            firstPersonality = first.personalityType.name,
            secondPersonality = second.personalityType.name,
            firstRawHp = first.vitalStats?.baseHp,
            firstRawBp = first.vitalStats?.baseBp,
            firstRawAp = first.vitalStats?.baseAp,
            secondRawHp = second.vitalStats?.baseHp,
            secondRawBp = second.vitalStats?.baseBp,
            secondRawAp = second.vitalStats?.baseAp,
            firstFallbackHp = usesFallback(first, Axis.HP),
            firstFallbackBp = usesFallback(first, Axis.BP),
            firstFallbackAp = usesFallback(first, Axis.AP),
            secondFallbackHp = usesFallback(second, Axis.HP),
            secondFallbackBp = usesFallback(second, Axis.BP),
            secondFallbackAp = usesFallback(second, Axis.AP),
            firstBattleHp = firstDefinition.maxHealth,
            firstBattleBp = firstDefinition.defense,
            firstBattleAp = firstDefinition.attack,
            firstBattleEnergy = firstDefinition.maxEnergy,
            firstEnergyRegeneration = firstDefinition.energyRegenerationPerSecond,
            firstMovementSpeed = firstDefinition.movementSpeed,
            firstCooldownMultiplier = firstDefinition.techniqueCooldownMultiplier,
            secondBattleHp = secondDefinition.maxHealth,
            secondBattleBp = secondDefinition.defense,
            secondBattleAp = secondDefinition.attack,
            secondBattleEnergy = secondDefinition.maxEnergy,
            secondEnergyRegeneration = secondDefinition.energyRegenerationPerSecond,
            secondMovementSpeed = secondDefinition.movementSpeed,
            secondCooldownMultiplier = secondDefinition.techniqueCooldownMultiplier,
            outcome = result?.outcome?.name ?: "TIMEOUT",
            winner = winner,
            durationMillis = result?.elapsedMillis ?: snapshot.elapsedMillis,
            firstRemainingHealthRatio = firstSnapshot.health.toFloat() / firstSnapshot.maxHealth,
            secondRemainingHealthRatio = secondSnapshot.health.toFloat() / secondSnapshot.maxHealth,
            damageDealt = snapshot.statistics.damageDealt,
            damageReceived = snapshot.statistics.damageReceived,
            damagePrevented = snapshot.statistics.damagePrevented,
            projectilesHit = snapshot.statistics.projectilesHit,
            projectilesMissed = snapshot.statistics.projectilesMissed,
            firstTechniqueUses = firstSnapshot.debug.techniqueUses,
            secondTechniqueUses = secondSnapshot.debug.techniqueUses,
            firstMeanReadiness = firstSnapshot.debug.meanReadiness,
            secondMeanReadiness = secondSnapshot.debug.meanReadiness,
            firstMeanDistance = firstSnapshot.debug.meanTargetDistance,
            secondMeanDistance = secondSnapshot.debug.meanTargetDistance,
            firstIncapacitatedMillis = firstSnapshot.debug.incapacitatedMillis,
            secondIncapacitatedMillis = secondSnapshot.debug.incapacitatedMillis,
            firstPositioningReplans = firstSnapshot.debug.positioningReplans,
            secondPositioningReplans = secondSnapshot.debug.positioningReplans,
            firstCounterCount = firstSnapshot.debug.counterCount,
            secondCounterCount = secondSnapshot.debug.counterCount,
            catalogVersion = rulesetVersion,
            damageFormula = damageFormula.name
        )
    }

    private enum class Axis { HP, BP, AP }

    private fun usesFallback(input: TrainingParticipantInput, axis: Axis): Boolean {
        val profile = input.vitalStats ?: return true
        if (profile.scale == VitalStatScale.STAGE_FALLBACK) return true
        if (profile.scale != VitalStatScale.ARENA_EXTRACTED && input.stage < 2) return true
        if (profile.scale == VitalStatScale.ARENA_EXTRACTED && profile.sourcePhase?.let { it in 3..6 } != true) return true
        val raw = when (axis) {
            Axis.HP -> profile.baseHp
            Axis.BP -> profile.baseBp
            Axis.AP -> profile.baseAp
        }
        return !VitalBattleProfile.isKnownBaseStat(raw)
    }

    private fun percentile(sortedValues: List<Long>, percentile: Float): Long {
        val index = ((sortedValues.lastIndex) * percentile).roundToInt().coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    private fun BattleBalanceSample.csvRow(): String = listOf(
        rulesetVersion, seed, sideSwapped, firstId, secondId, firstPersonality, secondPersonality,
        firstRawHp, firstRawBp, firstRawAp, secondRawHp, secondRawBp, secondRawAp,
        firstFallbackHp, firstFallbackBp, firstFallbackAp, secondFallbackHp, secondFallbackBp, secondFallbackAp,
        firstBattleHp, firstBattleBp, firstBattleAp, firstBattleEnergy, firstEnergyRegeneration,
        firstMovementSpeed, firstCooldownMultiplier,
        secondBattleHp, secondBattleBp, secondBattleAp, secondBattleEnergy, secondEnergyRegeneration,
        secondMovementSpeed, secondCooldownMultiplier,
        outcome, winner, durationMillis, firstRemainingHealthRatio, secondRemainingHealthRatio,
        damageDealt, damageReceived, damagePrevented, projectilesHit, projectilesMissed,
        firstTechniqueUses.toSortedMap(), secondTechniqueUses.toSortedMap(), firstMeanReadiness, secondMeanReadiness,
        firstMeanDistance, secondMeanDistance, firstIncapacitatedMillis, secondIncapacitatedMillis,
        firstPositioningReplans, secondPositioningReplans, firstCounterCount, secondCounterCount, catalogVersion, damageFormula
    ).joinToString(",") { value -> csv(value?.toString().orEmpty()) }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private const val CSV_HEADER = "rulesetVersion,seed,sideSwapped,firstId,secondId,firstPersonality,secondPersonality," +
        "firstRawHp,firstRawBp,firstRawAp,secondRawHp,secondRawBp,secondRawAp," +
        "firstFallbackHp,firstFallbackBp,firstFallbackAp,secondFallbackHp,secondFallbackBp,secondFallbackAp," +
        "firstBattleHp,firstBattleBp,firstBattleAp,firstBattleEnergy,firstEnergyRegeneration," +
        "firstMovementSpeed,firstCooldownMultiplier," +
        "secondBattleHp,secondBattleBp,secondBattleAp,secondBattleEnergy,secondEnergyRegeneration," +
        "secondMovementSpeed,secondCooldownMultiplier," +
        "outcome,winner,durationMillis,firstRemainingHealthRatio,secondRemainingHealthRatio," +
        "damageDealt,damageReceived,damagePrevented,projectilesHit,projectilesMissed," +
        "firstTechniqueUses,secondTechniqueUses,firstMeanReadiness,secondMeanReadiness,firstMeanDistance,secondMeanDistance," +
        "firstIncapacitatedMillis,secondIncapacitatedMillis,firstPositioningReplans,secondPositioningReplans," +
        "firstCounterCount,secondCounterCount,catalogVersion,damageFormula"
}
