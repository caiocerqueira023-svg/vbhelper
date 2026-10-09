package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.chat.WorldDialogueCodec
import org.junit.Assert.*
import org.junit.Test

class WorldSocialExchangePolicyTest {
    private val choice = SocialEncounterDecision("a", "b", SocialMotive.SHARED_ACTIVITY, 0)

    @Test fun speakingDoesNotImplicitlyAcceptAnInvitation() {
        assertEquals(SocialResponse.OBSERVE, WorldSocialExchangePolicy.response(choice,
            listOf(DialogueLine("a", "Let's explore."), DialogueLine("b", "What do you have in mind?"))))
        assertEquals(SocialResponse.DECLINE_INVITATION, WorldSocialExchangePolicy.response(choice,
            listOf(DialogueLine("a", "Let's explore."), DialogueLine("b", "No, thanks."))))
    }

    @Test fun acceptanceMustBeAttributedToTheInvitedParticipantAndCannotOverrideARefusal() {
        val lines = listOf(DialogueLine("a", "I want to explore.", response = SocialResponse.ACCEPT_INVITATION),
            DialogueLine("b", "I'll think about it."))
        assertEquals(SocialResponse.OBSERVE, WorldSocialExchangePolicy.response(choice, lines))
        val accepted = listOf(DialogueLine("b", "Yes, let's explore.", response = SocialResponse.ACCEPT_INVITATION))
        assertEquals(SocialResponse.ACCEPT_INVITATION, WorldSocialExchangePolicy.response(choice, accepted))
        assertEquals(SocialResponse.DECLINE_INVITATION, WorldSocialExchangePolicy.response(choice.copy(listenerDeclines = true), accepted))
    }

    @Test fun publicCodecPreservesValidSpeechWhenResponseMetadataIsUnknown() {
        val raw = """{"lines":[{"speakerId":"b","text":"Let's talk first.","response":"launch_combat"}],"intent":null}"""
        val decoded = WorldDialogueCodec.parseReadableExchange(raw, setOf("a", "b"), emptySet())
        assertEquals("Let's talk first.", decoded.lines.single().text)
        assertNull(decoded.lines.single().response)
    }

    @Test fun explicitResponseIsDecodedWithoutTreatingItAsBattleConsent() {
        val raw = """{"lines":[{"speakerId":"b","text":"I'll join you.","response":"ACCEPT_INVITATION"}],"intent":null}"""
        val decoded = WorldDialogueCodec.parseReadableExchange(raw, setOf("a", "b"), emptySet())
        assertEquals(SocialResponse.ACCEPT_INVITATION, WorldSocialExchangePolicy.response(choice, decoded.lines))
        assertNull(decoded.intent)
    }
}
