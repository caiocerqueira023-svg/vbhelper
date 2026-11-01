package com.tamerslab.battle.offline.data

import com.tamerslab.battle.domain.BattleStatusEffect
import com.tamerslab.battle.domain.TechniqueDefinition

enum class TechniqueStatusTone {
    AILMENT,
    BOOST
}

data class TechniqueStatusLabel(
    val effectId: String,
    val name: String,
    val tone: TechniqueStatusTone,
    val chancePercent: Int = 100
)

/** User-facing Decode terminology for the neutral Tamers Lab technique catalogue. */
object TechniqueStatusPresentation {
    fun forTechnique(technique: TechniqueDefinition): List<TechniqueStatusLabel> = technique.statusEffects
        .mapNotNull { effect -> forEffect(effect)?.copy(chancePercent = (effect.procChance * 100).toInt()) }
        .distinctBy { it.name }

    private fun forEffect(effect: BattleStatusEffect): TechniqueStatusLabel? = when (effect.id) {
        "decode_poison" -> TechniqueStatusLabel(effect.id, "Veneno", TechniqueStatusTone.AILMENT)
        "battle_burn" -> TechniqueStatusLabel(effect.id, "Queimadura", TechniqueStatusTone.AILMENT)
        "battle_freeze" -> TechniqueStatusLabel(effect.id, "Congelamento", TechniqueStatusTone.AILMENT)
        "battle_shock" -> TechniqueStatusLabel(effect.id, "Choque", TechniqueStatusTone.AILMENT)
        "decode_paralysis" -> TechniqueStatusLabel(effect.id, "Paralisia", TechniqueStatusTone.AILMENT)
        "decode_stun" -> TechniqueStatusLabel(effect.id, "Stun", TechniqueStatusTone.AILMENT)
        "decode_slow" -> TechniqueStatusLabel(effect.id, "Slow", TechniqueStatusTone.AILMENT)
        "decode_confusion" -> TechniqueStatusLabel(effect.id, "Confusão", TechniqueStatusTone.AILMENT)
        "decode_liquid_crystalization" -> TechniqueStatusLabel(effect.id, "Liquid Crystalization", TechniqueStatusTone.AILMENT)
        "decode_noise" -> TechniqueStatusLabel(effect.id, "Noise", TechniqueStatusTone.AILMENT)
        "decode_attack_up" -> TechniqueStatusLabel(effect.id, "Ataque ↑", TechniqueStatusTone.BOOST)
        "decode_defense_up" -> TechniqueStatusLabel(effect.id, "Defesa ↑", TechniqueStatusTone.BOOST)
        "decode_speed_up" -> TechniqueStatusLabel(effect.id, "Velocidade ↑", TechniqueStatusTone.BOOST)
        "decode_all_stats_up" -> TechniqueStatusLabel(effect.id, "Todos os atributos ↑", TechniqueStatusTone.BOOST)
        else -> null
    }
}
