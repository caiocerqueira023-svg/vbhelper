package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.nacabaro.vbhelper.battle.offline.core.*

data class EncounterBattleProfile(val techniqueIds: List<String>, val ai: BattleAiProfile)

/** Authored neutral encounter templates, selected by stable species identity rather than battle RNG. */
object EncounterBattleProfiles {
    fun wild(speciesKey: String): EncounterBattleProfile {
        var hash = 0L
        speciesKey.forEach { hash = (hash * 31 + it.code) and 0x7fffffff }
        val template = (hash % 4).toInt()
        val ids = when (template) {
            0 -> listOf("generic_assault_quick_combo", "generic_pressure_guided_drop", "generic_pressure_circular_pulse")
            1 -> listOf("generic_precision_calibrated_strike", "generic_tactics_focused_shot", "generic_endurance_resistant_stance")
            2 -> listOf("generic_chaos_shock_burst", "generic_pressure_guided_drop", "generic_endurance_attrition_ring")
            else -> listOf("generic_pressure_short_burst", "generic_control_impact_shot", "generic_assault_offensive_charge")
        }
        return EncounterBattleProfile(ids, BattleAiProfile(
            profileId = "wild-v3:$template",
            techniqueWeights = ids.mapIndexed { index, id -> id to listOf(1.15f, 1f, 0.85f)[index] }.toMap(),
            selectionMode = BattleSelectionMode.WEIGHTED,
            chargeMode = if (template == 1) BattleChargeMode.BALANCED else BattleChargeMode.PRESSURE,
            targetPolicy = when (template) {
                1 -> BattleTargetPolicy.VULNERABLE
                2 -> BattleTargetPolicy.STATUS
                else -> BattleTargetPolicy.THREAT
            },
            buffLimit = if (template == 1 || template == 3) 2 else 3,
            autonomousSpecial = true
        ))
    }
}
