package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R

/**
 * Locale-aware display names for the neutral VBHelper technique catalogue and
 * status effects. Lookup is by stable ID with fallback to the catalogue text,
 * so unknown or future techniques never render blank.
 */
fun techniqueNameRes(techniqueId: String): Int? = when (techniqueId.removePrefix("generic_")) {
    "pressure_short_burst" -> R.string.ui_battle_tech_pressure_short_burst
    "pressure_delayed_marker" -> R.string.ui_battle_tech_pressure_delayed_marker
    "pressure_circular_pulse" -> R.string.ui_battle_tech_pressure_circular_pulse
    "pressure_remote_trap" -> R.string.ui_battle_tech_pressure_remote_trap
    "pressure_expanding_wave" -> R.string.ui_battle_tech_pressure_expanding_wave
    "pressure_guided_drop" -> R.string.ui_battle_tech_pressure_guided_drop
    "pressure_field_break" -> R.string.ui_battle_tech_pressure_field_break
    "control_inhibiting_breath" -> R.string.ui_battle_tech_control_inhibiting_breath
    "control_impact_shot" -> R.string.ui_battle_tech_control_impact_shot
    "control_disorienting_blast" -> R.string.ui_battle_tech_control_disorienting_blast
    "control_disruptive_drop" -> R.string.ui_battle_tech_control_disruptive_drop
    "control_attrition_barrage" -> R.string.ui_battle_tech_control_attrition_barrage
    "control_impact_zone" -> R.string.ui_battle_tech_control_impact_zone
    "control_containment_field" -> R.string.ui_battle_tech_control_containment_field
    "tempo_interrupting_touch" -> R.string.ui_battle_tech_tempo_interrupting_touch
    "tempo_acceleration" -> R.string.ui_battle_tech_tempo_acceleration
    "tempo_linear_cutter" -> R.string.ui_battle_tech_tempo_linear_cutter
    "tempo_interference_cloud" -> R.string.ui_battle_tech_tempo_interference_cloud
    "tempo_blocking_pulse" -> R.string.ui_battle_tech_tempo_blocking_pulse
    "tempo_disorienting_field" -> R.string.ui_battle_tech_tempo_disorienting_field
    "tempo_total_overload" -> R.string.ui_battle_tech_tempo_total_overload
    "endurance_attrition_dust" -> R.string.ui_battle_tech_endurance_attrition_dust
    "endurance_resistant_stance" -> R.string.ui_battle_tech_endurance_resistant_stance
    "endurance_attrition_ring" -> R.string.ui_battle_tech_endurance_attrition_ring
    "endurance_restricting_bind" -> R.string.ui_battle_tech_endurance_restricting_bind
    "endurance_concussive_drop" -> R.string.ui_battle_tech_endurance_concussive_drop
    "endurance_rupture_wave" -> R.string.ui_battle_tech_endurance_rupture_wave
    "endurance_persistent_storm" -> R.string.ui_battle_tech_endurance_persistent_storm
    "assault_quick_combo" -> R.string.ui_battle_tech_assault_quick_combo
    "assault_offensive_charge" -> R.string.ui_battle_tech_assault_offensive_charge
    "assault_pressure_spin" -> R.string.ui_battle_tech_assault_pressure_spin
    "assault_stunning_impact" -> R.string.ui_battle_tech_assault_stunning_impact
    "assault_combat_focus" -> R.string.ui_battle_tech_assault_combat_focus
    "assault_rupture_charge" -> R.string.ui_battle_tech_assault_rupture_charge
    "assault_pressure_aura" -> R.string.ui_battle_tech_assault_pressure_aura
    "tactics_disruptive_contact" -> R.string.ui_battle_tech_tactics_disruptive_contact
    "tactics_focused_shot" -> R.string.ui_battle_tech_tactics_focused_shot
    "tactics_disorienting_mist" -> R.string.ui_battle_tech_tactics_disorienting_mist
    "tactics_impact_flash" -> R.string.ui_battle_tech_tactics_impact_flash
    "tactics_interference_field" -> R.string.ui_battle_tech_tactics_interference_field
    "tactics_concentrated_pulse" -> R.string.ui_battle_tech_tactics_concentrated_pulse
    "tactics_system_lock" -> R.string.ui_battle_tech_tactics_system_lock
    "precision_calibrated_strike" -> R.string.ui_battle_tech_precision_calibrated_strike
    "precision_optimization" -> R.string.ui_battle_tech_precision_optimization
    "precision_contact_orb" -> R.string.ui_battle_tech_precision_contact_orb
    "precision_counter_field" -> R.string.ui_battle_tech_precision_counter_field
    "precision_heavy_beam" -> R.string.ui_battle_tech_precision_heavy_beam
    "precision_precision_drop" -> R.string.ui_battle_tech_precision_precision_drop
    "precision_integral_lock" -> R.string.ui_battle_tech_precision_integral_lock
    "chaos_erratic_strike" -> R.string.ui_battle_tech_chaos_erratic_strike
    "chaos_shock_burst" -> R.string.ui_battle_tech_chaos_shock_burst
    "chaos_attrition_trap" -> R.string.ui_battle_tech_chaos_attrition_trap
    "chaos_fragmented_barrier" -> R.string.ui_battle_tech_chaos_fragmented_barrier
    "chaos_chaotic_barrage" -> R.string.ui_battle_tech_chaos_chaotic_barrage
    "chaos_slowing_drop" -> R.string.ui_battle_tech_chaos_slowing_drop
    "chaos_interference_meteors" -> R.string.ui_battle_tech_chaos_interference_meteors
    "counter_reaction" -> R.string.ui_battle_tech_counter_reaction
    "basic_attack" -> R.string.ui_battle_tech_basic_attack
    else -> null
}

@Composable
fun battleTechniqueName(techniqueId: String, fallback: String): String =
    techniqueNameRes(techniqueId)?.let { stringResource(it) } ?: fallback

fun statusLabelRes(effectId: String): Int? = when (effectId) {
    "decode_poison" -> R.string.ui_battle_status_decode_poison
    "battle_burn" -> R.string.ui_battle_status_battle_burn
    "battle_freeze" -> R.string.ui_battle_status_battle_freeze
    "battle_shock" -> R.string.ui_battle_status_battle_shock
    "decode_paralysis" -> R.string.ui_battle_status_decode_paralysis
    "decode_stun" -> R.string.ui_battle_status_decode_stun
    "decode_slow" -> R.string.ui_battle_status_decode_slow
    "decode_confusion" -> R.string.ui_battle_status_decode_confusion
    "decode_liquid_crystalization" -> R.string.ui_battle_status_decode_liquid_crystalization
    "decode_noise" -> R.string.ui_battle_status_decode_noise
    "decode_attack_up" -> R.string.ui_battle_status_decode_attack_up
    "decode_defense_up" -> R.string.ui_battle_status_decode_defense_up
    "decode_speed_up" -> R.string.ui_battle_status_decode_speed_up
    "decode_all_stats_up" -> R.string.ui_battle_status_decode_all_stats_up
    else -> null
}

@Composable
fun battleStatusLabel(effectId: String, fallback: String): String =
    statusLabelRes(effectId)?.let { stringResource(it) } ?: fallback
