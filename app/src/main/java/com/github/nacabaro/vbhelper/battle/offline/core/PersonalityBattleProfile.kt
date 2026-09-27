package com.github.nacabaro.vbhelper.battle.offline.core

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType

/** Four groups are derived from the existing 1..16 personality numbering; none are persisted. */
enum class PersonalityCore { PHILANTHROPY, VALOR, WISDOM, AMICABILITY }

/**
 * Bounded behavioral tendencies. These values change decisions and positioning only; they never
 * modify HP, damage, accuracy, cooldowns, or any other combat capability.
 */
data class PersonalityBattleProfile(
    val core: PersonalityCore,
    val offenseWeight: Float = 1f,
    val safetyWeight: Float = 1f,
    val efficiencyWeight: Float = 1f,
    val teamWeight: Float = 1f,
    /** Preferred offset in arena units; negative closes distance, positive creates space. */
    val preferredRangeBias: Float = 0f,
    /** Fraction of maximum energy the AI prefers to keep in reserve. */
    val energyReserve: Float = 0.15f,
    /** Chance to guard a readable incoming startup, before low-HP urgency is applied. */
    val guardReadBias: Float = 0.14f,
    /** Larger values require a stronger threat lead before switching targets. */
    val targetStickiness: Float = 0.5f,
    /** Below this fraction of HP, the profile's safety tendency becomes more prominent. */
    val lowHpRiskThreshold: Float = 0.3f,
    /** Softmax temperature in score points; low is deliberate, high is exploratory. */
    val actionTemperature: Float = 30f,
    /** Initial observation delay adjustment, limited to a small percentage. */
    val openingBias: Float = 0f,
    /** Extra preference for converting a genuine low-HP opening into a finish. */
    val opportunityWeight: Float = 0.5f
) {
    companion object {
        fun from(type: DigimonPersonalityType): PersonalityBattleProfile {
            val core = when ((type.number - 1) / 4) {
                0 -> PersonalityCore.PHILANTHROPY
                1 -> PersonalityCore.VALOR
                2 -> PersonalityCore.WISDOM
                else -> PersonalityCore.AMICABILITY
            }

            return when (type) {
                DigimonPersonalityType.ADORING -> profile(core, 0.94f, 1.10f, 1.02f, 1.18f, 0.20f, 0.22f, 0.28f, 0.62f, 0.40f, 25f, 0.03f)
                DigimonPersonalityType.DEVOTED -> profile(core, 1.00f, 1.02f, 1.05f, 1.10f, 0.02f, 0.18f, 0.24f, 0.90f, 0.30f, 26f, -0.02f)
                DigimonPersonalityType.TOLERANT -> profile(core, 0.93f, 1.14f, 1.10f, 1.04f, 0.34f, 0.24f, 0.27f, 0.58f, 0.38f, 22f, 0.08f)
                DigimonPersonalityType.OVERPROTECTIVE -> profile(core, 0.94f, 1.18f, 1.00f, 1.20f, 0.25f, 0.22f, 0.42f, 0.62f, 0.46f, 24f, -0.10f)
                DigimonPersonalityType.ZEALOUS -> profile(core, 1.10f, 0.88f, 0.91f, 0.96f, -0.16f, 0.08f, 0.13f, 0.48f, 0.16f, 37f, -0.12f)
                DigimonPersonalityType.BRAVE -> profile(core, 1.08f, 0.94f, 0.94f, 1.00f, -0.08f, 0.10f, 0.16f, 0.52f, 0.14f, 32f, -0.07f)
                DigimonPersonalityType.RECKLESS -> profile(core, 1.12f, 0.78f, 0.84f, 0.92f, -0.28f, 0.04f, 0.06f, 0.34f, 0.10f, 44f, -0.13f)
                DigimonPersonalityType.DARING -> profile(core, 1.10f, 0.89f, 0.96f, 0.98f, -0.04f, 0.12f, 0.13f, 0.44f, 0.22f, 39f, -0.06f, 0.76f)
                DigimonPersonalityType.ENLIGHTENED -> profile(core, 0.99f, 1.04f, 1.16f, 1.02f, 0.12f, 0.27f, 0.22f, 0.58f, 0.34f, 18f, 0.04f)
                DigimonPersonalityType.SLY -> profile(core, 1.02f, 1.02f, 1.04f, 0.98f, 0.48f, 0.17f, 0.15f, 0.36f, 0.28f, 35f, 0.02f, 0.66f)
                DigimonPersonalityType.ASTUTE -> profile(core, 1.04f, 1.12f, 1.10f, 1.02f, 0.10f, 0.20f, 0.36f, 0.70f, 0.36f, 20f, -0.07f, 0.72f)
                DigimonPersonalityType.STRATEGIC -> profile(core, 1.01f, 1.08f, 1.14f, 1.04f, 0.22f, 0.29f, 0.25f, 0.78f, 0.34f, 22f, 0.09f)
                DigimonPersonalityType.OPPORTUNISTIC -> profile(core, 1.06f, 0.98f, 1.04f, 1.03f, 0.02f, 0.14f, 0.17f, 0.40f, 0.24f, 33f, 0.07f, 0.92f)
                DigimonPersonalityType.FRIENDLY -> profile(core, team = 1.08f, stickiness = 0.58f)
                DigimonPersonalityType.SOCIABLE -> profile(core, 0.99f, 0.99f, 0.98f, 1.16f, 0.04f, 0.16f, 0.13f, 0.58f, 0.28f, 34f, -0.03f)
                DigimonPersonalityType.COMPASSIONATE -> profile(core, 0.93f, 1.18f, 1.06f, 1.22f, 0.24f, 0.26f, 0.34f, 0.64f, 0.48f, 24f, 0.03f)
            }
        }

        private fun profile(
            core: PersonalityCore,
            offense: Float = 1f,
            safety: Float = 1f,
            efficiency: Float = 1f,
            team: Float = 1f,
            range: Float = 0f,
            reserve: Float = 0.15f,
            guard: Float = 0.14f,
            stickiness: Float = 0.5f,
            lowHpThreshold: Float = 0.3f,
            temperature: Float = 30f,
            opening: Float = 0f,
            opportunity: Float = 0.5f
        ) = PersonalityBattleProfile(
            core = core,
            offenseWeight = offense,
            safetyWeight = safety,
            efficiencyWeight = efficiency,
            teamWeight = team,
            preferredRangeBias = range,
            energyReserve = reserve,
            guardReadBias = guard,
            targetStickiness = stickiness,
            lowHpRiskThreshold = lowHpThreshold,
            actionTemperature = temperature,
            openingBias = opening,
            opportunityWeight = opportunity
        )
    }
}
