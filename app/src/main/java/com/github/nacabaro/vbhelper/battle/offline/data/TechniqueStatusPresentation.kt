package com.github.nacabaro.vbhelper.battle.offline.data

import com.github.nacabaro.vbhelper.battle.offline.core.BattleStatusEffect
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueDefinition

enum class TechniqueStatusTone {
    AILMENT,
    BOOST
}

data class TechniqueStatusLabel(
    val name: String,
    val tone: TechniqueStatusTone
)

/** User-facing Decode terminology for the neutral VBHelper technique catalogue. */
object TechniqueStatusPresentation {
    fun forTechnique(technique: TechniqueDefinition): List<TechniqueStatusLabel> = technique.statusEffects
        .mapNotNull(::forEffect)
        .distinctBy { it.name }

    private fun forEffect(effect: BattleStatusEffect): TechniqueStatusLabel? = when (effect.id) {
        "decode_poison" -> TechniqueStatusLabel("Veneno", TechniqueStatusTone.AILMENT)
        "decode_paralysis" -> TechniqueStatusLabel("Paralisia", TechniqueStatusTone.AILMENT)
        "decode_stun" -> TechniqueStatusLabel("Stun", TechniqueStatusTone.AILMENT)
        "decode_slow" -> TechniqueStatusLabel("Slow", TechniqueStatusTone.AILMENT)
        "decode_confusion" -> TechniqueStatusLabel("Confusão", TechniqueStatusTone.AILMENT)
        "decode_liquid_crystalization" -> TechniqueStatusLabel("Liquid Crystalization", TechniqueStatusTone.AILMENT)
        "decode_noise" -> TechniqueStatusLabel("Noise", TechniqueStatusTone.AILMENT)
        "decode_attack_up" -> TechniqueStatusLabel("Ataque ↑", TechniqueStatusTone.BOOST)
        "decode_defense_up" -> TechniqueStatusLabel("Defesa ↑", TechniqueStatusTone.BOOST)
        "decode_speed_up" -> TechniqueStatusLabel("Velocidade ↑", TechniqueStatusTone.BOOST)
        "decode_all_stats_up" -> TechniqueStatusLabel("Todos os atributos ↑", TechniqueStatusTone.BOOST)
        else -> null
    }
}
