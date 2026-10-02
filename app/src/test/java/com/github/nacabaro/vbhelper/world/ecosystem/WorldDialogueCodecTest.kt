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
    @Test fun contextualSparringUsesAnAttributedCurrentLineAndAnAllowedTarget() {
        val result = WorldDialogueCodec.parse("""{"lines":[{"speakerId":"a","text":"Will you spar with me?"}],"intent":{"type":"CHALLENGE_BATTLE","speakerId":"a","targetIds":["trainer"],"evidenceIds":["this:a"],"reason":"friendly practice","sparring":true}}""", setOf("a"), emptySet(), targetsAllowed = setOf("a","trainer"))
        assertEquals(DialogueIntentType.CHALLENGE_BATTLE, result.intent!!.type)
        assertTrue(result.intent!!.sparring)
    }
}
