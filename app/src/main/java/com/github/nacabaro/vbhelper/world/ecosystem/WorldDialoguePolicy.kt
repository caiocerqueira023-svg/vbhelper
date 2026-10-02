package com.github.nacabaro.vbhelper.world.ecosystem

internal data class PendingDialogueChallenge(
    val id: String,
    val initiatorId: String,
    val targetIds: Set<String>,
    val sparring: Boolean,
    val expiresAtTick: Long
)

internal data class AcceptedDialogueProposal(
    val proposal: DialogueProposal,
    val challengeIds: Set<String> = emptySet()
)

/** Context validation shared by transcript commits. Prose never supplies consent. */
internal object WorldDialoguePolicy {
    fun canStartWithoutReply(type: DialogueIntentType, sparring: Boolean): Boolean =
        type == DialogueIntentType.CHALLENGE_BATTLE && !sparring

    fun validate(
        proposal: DialogueProposal,
        speakers: Set<String>,
        evidence: Set<String>,
        pending: List<PendingDialogueChallenge>,
        tick: Long,
        trainerPresent: Boolean
    ): AcceptedDialogueProposal? {
        val targets = proposal.targetIds.toSet()
        val allowed = if (trainerPresent) speakers + "trainer" else speakers
        if (proposal.type == DialogueIntentType.NONE || proposal.speakerId !in speakers ||
            targets.isEmpty() || targets.size != proposal.targetIds.size || proposal.speakerId in targets ||
            !allowed.containsAll(targets) || proposal.evidenceIds.isEmpty() ||
            !evidence.containsAll(proposal.evidenceIds) || proposal.reason.isBlank() || proposal.reason.length > 160) return null

        val related = pending.filter { challenge ->
            tick < challenge.expiresAtTick && (
                challenge.initiatorId in targets && proposal.speakerId in challenge.targetIds ||
                challenge.initiatorId == proposal.speakerId && challenge.targetIds == targets)
        }
        if (proposal.type == DialogueIntentType.ACCEPT_CHALLENGE) {
            // An NPC-only transfer requires explicit, reciprocal consent from the two wilds.
            // Challenges involving the trainer always go through the player's Accept action.
            val challenge = related.filter {
                it.initiatorId != "trainer" && "trainer" !in it.targetIds &&
                    it.targetIds == setOf(proposal.speakerId) && targets == setOf(it.initiatorId)
            }.sortedWith(compareByDescending<PendingDialogueChallenge> { it.expiresAtTick }.thenBy { it.id }).firstOrNull()
                ?: return null
            return AcceptedDialogueProposal(proposal.copy(sparring = challenge.sparring), setOf(challenge.id))
        }
        return AcceptedDialogueProposal(proposal, related.map { it.id }.toSet())
    }

    fun socialDelta(accepted: AcceptedDialogueProposal?): Int = when {
        accepted?.proposal?.type == DialogueIntentType.CHALLENGE_BATTLE && !accepted.proposal.sparring -> -6
        accepted?.proposal?.type == DialogueIntentType.DEESCALATE -> 3
        accepted?.proposal?.type == DialogueIntentType.DECLINE_CHALLENGE -> 0
        else -> 2
    }
}
