package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.SocialEventAppraisal
import com.github.nacabaro.vbhelper.domain.personality.SocialStimulus

/** Participation is not agreement. Only the recipient can accept an activity invitation. */
object WorldSocialExchangePolicy {
    fun response(choice: SocialEncounterDecision?, lines: List<DialogueLine>): SocialResponse {
        if (choice == null) return SocialResponse.OBSERVE
        if (choice.listenerDeclines) return SocialResponse.DECLINE_INVITATION
        val recipient = lines.firstOrNull { it.speakerId == choice.targetId } ?: return SocialResponse.OBSERVE
        if (recipient.response == SocialResponse.DECLINE_INVITATION ||
            SocialEventAppraisal.classify(recipient.text) == SocialStimulus.REFUSAL) return SocialResponse.DECLINE_INVITATION
        return recipient.response ?: SocialResponse.OBSERVE
    }
}
