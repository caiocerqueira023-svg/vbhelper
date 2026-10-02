package com.github.nacabaro.vbhelper.world.ecosystem

enum class PrivateDuelDisposition { ACCEPTED_FRIENDLY, INVITATION, HOSTILE_ATTACK }

object WorldPrivateDuelPolicy {
    fun classify(proposal: DialogueProposal, individualId: String, userMessageId: Long?, sourceMessages: Set<Long>): PrivateDuelDisposition? {
        if(proposal.speakerId!=individualId || proposal.targetIds!=listOf("trainer") || proposal.reason.isBlank() || proposal.reason.length>160) return null
        return when(proposal.type) {
            DialogueIntentType.ACCEPT_CHALLENGE -> if(userMessageId!=null && userMessageId in sourceMessages) PrivateDuelDisposition.ACCEPTED_FRIENDLY else null
            DialogueIntentType.CHALLENGE_BATTLE -> if(proposal.sparring) PrivateDuelDisposition.INVITATION else PrivateDuelDisposition.HOSTILE_ATTACK
            else -> null
        }
    }
}
