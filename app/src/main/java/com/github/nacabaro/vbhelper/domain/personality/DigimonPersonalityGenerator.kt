package com.github.nacabaro.vbhelper.domain.personality

import com.github.cfogrady.vbnfc.data.NfcCharacter
import kotlin.random.Random

/** Creates the one Story: Time Stranger personality assigned to an individual. */
object DigimonPersonalityGenerator {
    fun generate(
        individualId: String,
        attribute: NfcCharacter.Attribute?,
        stage: Int,
        now: Long = System.currentTimeMillis(),
        random: Random = Random.Default
    ): DigimonPersonalityTraits {
        // Attribute and stage stay in the API because callers already have this
        // context and future assignment rules may use it. Story Time Stranger's
        // personality is an individual type, so every type remains available
        // instead of collapsing it into the old temperament/social/quirk axes.
        @Suppress("UNUSED_VARIABLE")
        val assignmentContext = Triple(individualId, attribute, stage)
        val type = DigimonPersonalityType.entries[random.nextInt(DigimonPersonalityType.entries.size)]
        return DigimonPersonalityTraits(
            individualId = individualId,
            personalityType = type,
            generatedAt = now,
            systemVersion = CURRENT_PERSONALITY_SYSTEM_VERSION
        )
    }
}
