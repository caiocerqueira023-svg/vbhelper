package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatEngine
import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract §2 rule 6: level 1 = composed species voice, level 2 = intent `fallbackId`,
 * level 3 = authored deflection, level 4 = authored generic confusion, lowest available rung wins.
 */
class FreeChatFallbackLadderTest {
    private val demo = FreeChatEngine(demoContent())
    private val withoutLevelOne = FreeChatEngine(contentWithoutLevelOne())
    private val deflectionOnly = FreeChatEngine(deflectionOnlyContent())
    private val confusionOnly = FreeChatEngine(confusionOnlyContent())

    @Test
    fun rungOneComposesTheSpeciesVoiceFromCatalogAtoms() {
        val turnContext = context(speciesKey = "agumon")
        val result = demo.reply("zzzz qqqq", turnContext)
        assertEquals(1, result.fallbackLevel)
        assertTrue(result.replyId.startsWith("fallback.1.agumon."))
        assertTrue(result.text.contains("Agumon"))
        assertTrue(result.text.contains("Child level"))
        assertTrue(result.text.contains("Vaccine type"))
        assertEquals(0, result.moodDelta)
        assertEquals(FreeChatSafetyClass.SAFE, result.safetyClass)
        assertNotNull(result.tone)
        assertEquals(result, demo.reply("zzzz qqqq", turnContext))
    }

    @Test
    fun rungOneFallsBackToContextAtomsWhenTheVoiceHasGaps() {
        val gapContent = content(
            fallbacks = authoredFallbacks(),
            voices = listOf(voice(speciesKey = "agumon", speciesName = "Agumon")),
        )
        val engine = FreeChatEngine(gapContent)
        val result = engine.reply(
            "zzzz qqqq",
            context(speciesKey = "agumon", speciesName = "Agumon", level = "Child", speciesType = "Vaccine"),
        )
        assertEquals(1, result.fallbackLevel)
        assertTrue(result.text.contains("Agumon"))
        assertTrue(result.text.contains("Child level"))
        assertTrue(result.text.contains("Vaccine type"))
    }

    @Test
    fun authoredLevelOneLinesBackUpTheComposedVoiceLine() {
        val engine = FreeChatEngine(
            content(fallbacks = authoredFallbacks().filterKeys { it == 1 }, safety = demoSafety()),
        )
        val result = engine.reply("zzzz qqqq", context())
        assertEquals(1, result.fallbackLevel)
        assertEquals("fallback.level1.r1", result.replyId)
        assertEquals("First light, first line.", result.text)
    }

    @Test
    fun rungTwoUsesTheMatchedIntentFallbackId() {
        val result = withoutLevelOne.reply("secret word", context())
        assertEquals(2, result.fallbackLevel)
        assertEquals("fallback.2.r1", result.replyId)
        assertEquals("fc.needs.fallback", result.intentId)
        assertEquals("secret", result.topic)
        assertTrue(result.scoreQ16 > 0)
    }

    @Test
    fun rungTwoAcceptsADirectAuthoredFallbackReplyId() {
        val directIntent = intent(
            id = "fc.direct.fallback",
            topic = "direct",
            matchPhrases = listOf("direct word"),
            replies = emptyList(),
            fallbackId = "fallback.3.r2",
        )
        val engine = FreeChatEngine(
            content(intents = listOf(directIntent), fallbacks = authoredFallbacks()),
        )
        val result = engine.reply("direct word", context())
        assertEquals(2, result.fallbackLevel)
        assertEquals("fallback.3.r2", result.replyId)
        assertEquals("fc.direct.fallback", result.intentId)
    }

    @Test
    fun rungTwoIsSkippedWhenTheReferenceDoesNotResolve() {
        val brokenIntent = intent(
            id = "fc.broken.fallback",
            topic = "broken",
            matchPhrases = listOf("broken word"),
            replies = emptyList(),
            fallbackId = "fallback.9",
        )
        val engine = FreeChatEngine(
            content(
                intents = listOf(brokenIntent),
                fallbacks = authoredFallbacks().filterKeys { it >= 3 },
            ),
        )
        val result = engine.reply("broken word", context())
        assertEquals(3, result.fallbackLevel)
        assertEquals("fc.broken.fallback", result.intentId)
    }

    @Test
    fun rungThreeUsesAuthoredDeflectionLines() {
        val result = deflectionOnly.reply("zzzz qqqq", context())
        assertEquals(3, result.fallbackLevel)
        assertTrue(result.replyId in setOf("fallback.3.r1", "fallback.3.r2"))
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun rungFourUsesAuthoredGenericConfusion() {
        val result = confusionOnly.reply("zzzz qqqq", context())
        assertEquals(4, result.fallbackLevel)
        assertEquals("fallback.4.r1", result.replyId)
    }

    @Test
    fun safetyS2DeflectsWithTheAuthoredRungThreeLineWithoutEchoing() {
        val result = demo.reply("send nudes", context())
        assertEquals(FreeChatSafetyClass.S2, result.safetyClass)
        assertEquals(3, result.fallbackLevel)
        assertTrue(result.replyId in setOf("fallback.3.r1", "fallback.3.r2"))
        assertFalse(result.text.contains("nude"))
        assertTrue(result.chips.isEmpty())
        assertEquals(0, result.moodDelta)
    }

    @Test
    fun safetyS3WinsOverS2OnTheSameTurn() {
        val result = demo.reply("i want to suicide and then kill you", context())
        assertEquals(FreeChatSafetyClass.S3, result.safetyClass)
        assertFalse(result.text.contains("suicide"))
    }

    @Test
    fun safetyS1ReturnsTheBoundaryLineWithoutEchoing() {
        val result = demo.reply("you are an idiot", context())
        assertEquals(FreeChatSafetyClass.S1, result.safetyClass)
        assertEquals(3, result.fallbackLevel)
        assertEquals("safety.boundary.1", result.replyId)
        assertFalse(result.text.contains("idiot"))
        assertEquals(0, result.moodDelta)
    }

    @Test
    fun safetyDeflectionFallsBackToTheConstantWhenNoRungIsAuthored() {
        val engine = FreeChatEngine(content(safety = demoSafety()))
        val result = engine.reply("send nudes", context())
        assertEquals(FreeChatSafetyClass.S2, result.safetyClass)
        assertTrue(result.text.isNotBlank())
        assertFalse(result.text.contains("nude"))
        assertEquals(3, result.fallbackLevel)
    }

    @Test
    fun safetyPiiIsDeflectedWithoutEchoingTheDigits() {
        val result = demo.reply("my cpf is 12345678901", context())
        assertEquals(FreeChatSafetyClass.PII, result.safetyClass)
        assertFalse(result.text.contains("12345678901"))
        assertFalse(result.text.contains("cpf"))
    }

    @Test
    fun ladderNeverDeadEndsWithoutAuthoredFallbacks() {
        val bare = FreeChatEngine(content(intents = demoIntents(), safety = demoSafety()))
        val result = bare.reply("zzzz qqqq", context())
        assertTrue(result.text.isNotBlank())
        assertEquals(4, result.fallbackLevel)
        assertEquals(FreeChatSafetyClass.SAFE, result.safetyClass)
    }
}
