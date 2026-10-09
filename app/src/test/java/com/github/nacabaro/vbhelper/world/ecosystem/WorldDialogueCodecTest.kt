package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.chat.WorldDialogueCodec
import org.junit.Assert.*
import org.junit.Test

class WorldDialogueCodecTest {
    @Test fun malformedIntentKeepsAttributedSpeechWithoutExposingOrExecutingMetadata() {
        val raw="""{"lines":[{"speakerId":"a","text":"A spar? How agreeable—shall we begin?"}],"intent":{"type":"ACCEPT_CHALLENGE","speakerId":"a","targetIds":["trainer"],"evidence":["this:a"]}}"""
        val exchange=WorldDialogueCodec.parseReadableExchange(raw,setOf("a"),emptySet(),targetsAllowed=setOf("a","trainer"))
        assertEquals("A spar? How agreeable—shall we begin?",exchange.lines.single().text)
        assertNull(exchange.intent)
        assertEquals(exchange.lines.single().text,WorldDialogueCodec.visibleText(raw,"a"))
    }
    @Test fun invalidEvidenceCannotExecuteEvenWhenTheReplyIsReadable() {
        val raw="""{"lines":[{"speakerId":"a","text":"Stay out of my territory."}],"intent":{"type":"CHALLENGE_BATTLE","speakerId":"a","targetIds":["b"],"evidenceIds":["invented"],"reason":"territory"}}"""
        assertNull(WorldDialogueCodec.parseReadableExchange(raw,setOf("a"),emptySet(),targetsAllowed=setOf("a","b")).intent)
        assertEquals("Stay out of my territory.",WorldDialogueCodec.visibleText(raw,"a"))
    }
    @Test fun wrongSpeakerOrBrokenEnvelopeNeverLeaksItsJsonIntoTheVisibleReply() {
        assertNull(WorldDialogueCodec.visibleText("""{"lines":[{"speakerId":"invented","text":"Attack!"}],"intent":null}""","a"))
        assertNull(WorldDialogueCodec.visibleText("""{"lines":[{"speakerId":"a","text":"unfinished""","a"))
        assertEquals("Hello there.",WorldDialogueCodec.visibleText("Hello there.","a"))
    }
    @Test fun jokeAndRefusalHaveNoExecutableIntent() {
        val result = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"That joke about fighting was silly."},{"speakerId":"b","text":"I do not want to fight."}],"intent":null}""", setOf("a","b"), emptySet())
        assertNull(result.intent)
    }
    @Test fun inventedSpeakersAndEvidenceAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldDialogueCodec.parse("""{"lines":[{"speakerId":"invented","text":"Fight!"}]}""", setOf("a"), emptySet())
        }
        assertThrows(IllegalArgumentException::class.java) {
            WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Will you spar?"}],"intent":{"type":"CHALLENGE_BATTLE","speakerId":"a","targetIds":["b"],"evidenceIds":["missing"],"reason":"sparring","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","b"))
        }
    }
    @Test fun abbreviatedAndLowercaseTypesStillResolveToBattleIntents() {
        val accept = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Lets do it!"}],"intent":{"type":"ACCEPT","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"accepted the duel","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer")).intent
        assertNotNull(accept)
        assertEquals(DialogueIntentType.ACCEPT_CHALLENGE, accept?.type)
        assertEquals(true, accept?.sparring)
        val lower = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"En garde."}],"intent":{"type":"challenge_battle","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"hostile attack","sparring":false}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer")).intent
        assertEquals(DialogueIntentType.CHALLENGE_BATTLE, lower?.type)
    }
    @Test fun singularEvidenceIdAndCapitalizedTrainerAreAccepted() {
        val result = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"We fight."}],"intent":{"type":"CHALLENGE_BATTLE","speakerId":"a","targetIds":["Trainer"],"evidenceId":"this:a","reason":"rivalry","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer")).intent
        assertEquals(DialogueIntentType.CHALLENGE_BATTLE, result?.type)
        assertEquals(listOf("trainer"), result?.targetIds)
    }
    @Test fun overlongReasonIsTruncatedInsteadOfDroppingAFightAgreement() {
        val longReason = "x".repeat(500)
        val result = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Lets do it!"}],"intent":{"type":"ACCEPT_CHALLENGE","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"$longReason","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer")).intent
        assertEquals(DialogueIntentType.ACCEPT_CHALLENGE, result?.type)
        assertTrue((result?.reason?.length ?: 999) <= 160)
    }
    @Test fun unknownIntentTypeStillFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Attack!"}],"intent":{"type":"GRANT_REWARD","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"loot","sparring":false}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer"))
        }
    }
    @Test fun contextualSparringUsesAnAttributedCurrentLineAndAnAllowedTarget() {
        val result = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Will you spar with me?"}],"intent":{"type":"CHALLENGE_BATTLE","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"friendly practice","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer"))
        assertEquals(DialogueIntentType.CHALLENGE_BATTLE, result.intent!!.type)
        assertTrue(result.intent!!.sparring)
    }
}
