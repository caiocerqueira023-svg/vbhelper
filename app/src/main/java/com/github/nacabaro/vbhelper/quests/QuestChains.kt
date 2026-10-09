package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile

/** An authored, bounded graph. The selected edge is saved with the parent offer, not chosen by dialogue. */
object QuestChains {
    const val GENERATION_VERSION = 4
    const val MAX_DEPTH = 2

    fun nextTemplate(templateId: String, depth: Int, profile: DigimonSocialProfile): String? {
        if (depth >= MAX_DEPTH) return null
        return when (templateId) {
            "missing" -> "rescue"
            "supplies", "property" -> "courier"
            "courier" -> if (profile.training > profile.empathy) "technique_trial" else "supply_round"
            "rescue", "supply_round" -> if (profile.challenge > profile.empathy) "time_trial" else "training"
            "training" -> "technique_trial"
            "rival", "technique_trial" -> "time_trial"
            "time_trial" -> "careful_victory"
            "careful_victory" -> "training"
            "medicine" -> "supply_round"
            else -> null
        }
    }
}
