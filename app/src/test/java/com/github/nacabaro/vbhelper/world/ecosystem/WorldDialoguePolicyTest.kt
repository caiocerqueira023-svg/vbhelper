package com.github.nacabaro.vbhelper.world.ecosystem

import org.junit.Assert.*
import org.junit.Test

class WorldDialoguePolicyTest {
    private val speakers = setOf("a", "b", "c")
    private val challenge = PendingDialogueChallenge("challenge", "a", setOf("b"), sparring = true, expiresAtTick = 20)

    private fun proposal(type: DialogueIntentType, speaker: String = "b", targets: List<String> = listOf("a"),
        evidence: List<String> = listOf("line")) = DialogueProposal(type, speaker, targets, evidence, "friendly practice")

    private fun validate(proposal: DialogueProposal, pending: List<PendingDialogueChallenge> = listOf(challenge), tick: Long = 10,
        trainerPresent: Boolean = false) = WorldDialoguePolicy.validate(proposal, speakers, setOf("line"), pending, tick, trainerPresent)

    @Test fun `rejected evidence cannot turn a normal exchange into hostility`() {
        val invalid = proposal(DialogueIntentType.CHALLENGE_BATTLE, evidence = listOf("invented"))
        val accepted = validate(invalid)
        assertNull(accepted)
        assertEquals(WorldDialoguePolicy.socialDelta(null), WorldDialoguePolicy.socialDelta(accepted))
        assertEquals(2, WorldDialoguePolicy.socialDelta(accepted))
    }

    @Test fun `hostile and sparring changes require a validated proposal`() {
        assertEquals(-6, WorldDialoguePolicy.socialDelta(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE))))
        assertEquals(2, WorldDialoguePolicy.socialDelta(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE).copy(sparring = true))))
    }

    @Test fun `acceptance must reciprocate an unexpired challenge and inherit its stakes`() {
        val accepted = validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE))!!
        assertEquals(setOf("challenge"), accepted.challengeIds)
        assertTrue(accepted.proposal.sparring)
        assertNull(validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE), tick = 20))
        assertNull(validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE), pending = emptyList()))
        assertNull(validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE, targets = listOf("c"))))
        assertNull(validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE, speaker = "c")))
    }

    @Test fun `an NPC cannot accept a trainers challenge or invent absent human participation`() {
        val humanChallenge = challenge.copy(initiatorId = "trainer")
        assertNull(validate(proposal(DialogueIntentType.ACCEPT_CHALLENGE, targets = listOf("trainer")), listOf(humanChallenge), trainerPresent = true))
        assertNull(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE, targets = listOf("trainer"))))
        assertNotNull(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE, targets = listOf("trainer")), trainerPresent = true))
    }

    @Test fun `refusal only closes challenges addressed between the attributed participants`() {
        val unrelated = challenge.copy(id = "other", initiatorId = "c", targetIds = setOf("a"))
        val declined = validate(proposal(DialogueIntentType.DECLINE_CHALLENGE), listOf(challenge, unrelated))!!
        assertEquals(setOf("challenge"), declined.challengeIds)
        assertEquals(0, WorldDialoguePolicy.socialDelta(declined))
    }

    @Test fun `empty self targeted or unattributed proposals have no mechanical effect`() {
        assertNull(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE, targets = emptyList())))
        assertNull(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE, targets = listOf("b"))))
        assertNull(validate(proposal(DialogueIntentType.CHALLENGE_BATTLE, evidence = emptyList())))
        assertNull(validate(proposal(DialogueIntentType.NONE)))
    }
}
