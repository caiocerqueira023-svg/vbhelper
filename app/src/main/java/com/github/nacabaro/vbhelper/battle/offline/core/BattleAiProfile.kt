package com.github.nacabaro.vbhelper.battle.offline.core

/** Historical ideas adapted to compact bracelet stats; these are not extracted PS1 Brains values. */
enum class BattleChargeMode { PRESSURE, BALANCED, FULL }
enum class BattleSelectionMode { UTILITY, WEIGHTED }
enum class BattleTargetPolicy { THREAT, CLOSEST, VULNERABLE, STATUS }

data class BattleAiProfile(
    val profileId: String = "balanced",
    val techniqueWeights: Map<String, Float> = emptyMap(),
    val selectionMode: BattleSelectionMode = BattleSelectionMode.UTILITY,
    val chargeMode: BattleChargeMode = BattleChargeMode.PRESSURE,
    val targetPolicy: BattleTargetPolicy = BattleTargetPolicy.THREAT,
    val buffLimit: Int = 3,
    val preparationPriority: Float = 18f,
    val targetSwitchThreshold: Float = 0.18f,
    val counterChance: Float = 0.25f,
    val autonomousSpecial: Boolean = false
)

object BattleRules {
    const val LEGACY_VERSION = 2
    const val CURRENT_VERSION = 3
    const val STEP_MILLIS = 34L
    const val READINESS_MAX = 100f
    const val READINESS_MIN = -155f
    const val POSITIONING_STALL_MILLIS = 1_500L
    const val POSITIONING_TIMEOUT_MILLIS = 6_000L
    const val CONTROL_IMMUNITY_MILLIS = 1_000L

    fun readinessCost(technique: TechniqueDefinition): Float = technique.readinessCost ?:
        if (technique.power == 0) 40f else (technique.power.toFloat() * technique.hitCount).coerceIn(15f, 255f)

    fun readinessRequired(mode: BattleChargeMode, technique: TechniqueDefinition): Float = when (mode) {
        BattleChargeMode.PRESSURE -> 0.01f
        BattleChargeMode.BALANCED -> readinessCost(technique).coerceAtMost(READINESS_MAX)
        BattleChargeMode.FULL -> READINESS_MAX
    }
}
