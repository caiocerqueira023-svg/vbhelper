package com.tamerslab.world

import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.domain.personality.DigimonSocialProfile
import com.tamerslab.domain.personality.SocialEventAppraisal

/** Compatibility entry point: every conversational turn moves trust; generated warmth alone never manufactures it. */
object WildMoodAnalyzer {
    fun resolveDelta(userText: String, reply: String, llmDelta: Int?): Int {
        if (llmDelta != null) {
            val coerced = llmDelta.coerceIn(-4, 4)
            return if (coerced == 0) 2 else coerced
        }
        return SocialEventAppraisal.resolve(DigimonSocialProfile.forIndividual("fallback", DigimonPersonalityType.FRIENDLY),
            userText, null)
    }

    fun scaleDelta(rawDelta: Int): Int = rawDelta.coerceIn(-4, 4) * 2
}
