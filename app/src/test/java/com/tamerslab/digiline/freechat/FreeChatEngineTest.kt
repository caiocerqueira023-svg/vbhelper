package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatEngine
import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import com.tamerslab.digiline.freechat.model.FreeChatAudience
import com.tamerslab.digiline.freechat.model.FreeChatChip
import com.tamerslab.digiline.freechat.model.FreeChatSafetyClass
import com.tamerslab.digiline.model.DigilineSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeChatEngineTest {
    private val normalizer = FreeChatNormalizer()
    private val engine = FreeChatEngine(demoContent())

    @Test
    fun greetingResolvesAuthoredIntentWithChips() {
        val result = engine.reply("Hi", context())
        assertEquals("fc.greeting.1", result.intentId)
        assertEquals("greeting", result.topic)
        assertEquals(0, result.fallbackLevel)
        assertEquals(FreeChatSafetyClass.SAFE, result.safetyClass)
        assertEquals(3, result.contentVersion)
        assertFalse(result.text.isBlank())
        assertEquals(listOf(FreeChatChip("chip.what_are_you", "What are you?")), result.chips)
    }

    @Test
    fun sameTurnIsDeterministicAcrossCallsAndInstances() {
        val turnContext = context(turnCounter = 7)
        val first = engine.reply("hello", turnContext)
        assertEquals(first, engine.reply("hello", turnContext))
        assertEquals(first, FreeChatEngine(demoContent()).reply("hello", turnContext))
        assertEquals(first, engine.reply("HELLO!!!", turnContext))
    }

    @Test
    fun variantPickUsesTheContractSeedFormula() {
        val turnContext = context(turnCounter = 3)
        val seed = DigilineSeed.combine(
            DigilineSeed.stableHash(normalizer.normalize("hi")),
            DigilineSeed.stableHash(turnContext.individualId),
            turnContext.turnCounter,
            turnContext.contentVersion.toLong(),
        )
        val warmReplies = listOf("greeting.r1", "greeting.r3").sorted()
        val expected = warmReplies[floorMod(seed, warmReplies.size.toLong())]
        assertEquals(expected, engine.reply("hi", turnContext).replyId)
    }

    @Test
    fun turnCounterChangesTheVariant() {
        val ids = (0L..7L).map { turn -> engine.reply("hi", context(turnCounter = turn)).replyId }.toSet()
        assertTrue("expected more than one authored variant", ids.size > 1)
    }

    @Test
    fun audienceIsAHardContextFilter() {
        assertEquals("fc.audience.owned", engine.reply("partner talk", context(audience = FreeChatAudience.OWNED)).intentId)
        assertEquals("fc.audience.wild", engine.reply("partner talk", context(audience = FreeChatAudience.WILD)).intentId)
    }

    @Test
    fun stageTierIsAHardContextFilter() {
        assertEquals("fc.tier.two", engine.reply("training plan", context(stageTier = 2)).intentId)
        assertEquals("fc.tier.three", engine.reply("training plan", context(stageTier = 3)).intentId)
        assertEquals(1, engine.reply("training plan", context(stageTier = 1)).fallbackLevel)
    }

    @Test
    fun personalityTypeIsAHardContextFilter() {
        assertEquals("fc.type.five", engine.reply("battle plan", context(personalityType = 5)).intentId)
        assertEquals("fc.type.nine", engine.reply("battle plan", context(personalityType = 9)).intentId)
        assertEquals(1, engine.reply("battle plan", context(personalityType = 7)).fallbackLevel)
    }

    @Test
    fun moodBandIsAHardContextFilter() {
        assertEquals("fc.mood.low", engine.reply("how do you feel", context(mood = 20)).intentId)
        assertEquals("fc.mood.positive", engine.reply("how do you feel", context(mood = 95)).intentId)
        assertEquals(1, engine.reply("how do you feel", context(mood = 50)).fallbackLevel)
    }

    @Test
    fun coreAndRecentEventConditionsAreHonoured() {
        assertEquals("fc.core.valor", engine.reply("valor talk", context(core = "VALOR")).intentId)
        assertEquals(1, engine.reply("valor talk", context(core = "WISDOM")).fallbackLevel)
        assertEquals("fc.event.rain", engine.reply("rain event", context(recentEventKeys = setOf("battle_win"))).intentId)
        assertEquals(1, engine.reply("rain event", context()).fallbackLevel)
    }

    @Test
    fun replyStageFilterNarrowsTheLineSet() {
        assertEquals("reply.filter.stage2", engine.reply("quiz me", context(stageTier = 2)).replyId)
        assertEquals("reply.filter.stage1", engine.reply("quiz me", context(stageTier = 1)).replyId)
        val relaxed = engine.reply("quiz me", context(stageTier = 3))
        assertEquals(0, relaxed.fallbackLevel)
        assertTrue(relaxed.replyId in setOf("reply.filter.stage1", "reply.filter.stage2"))
    }

    @Test
    fun recentReplyIdsAreExcludedWhenPossible() {
        assertEquals("recent.r2", engine.reply("again please", context(recentReplyIds = setOf("recent.r1"))).replyId)
        val allRecent = engine.reply("again please", context(recentReplyIds = setOf("recent.r1", "recent.r2")))
        assertEquals(0, allRecent.fallbackLevel)
        assertTrue(allRecent.replyId in setOf("recent.r1", "recent.r2"))
    }

    @Test
    fun tonePreferenceFollowsPersonalityType() {
        assertEquals("tone.split.warm", engine.reply("tell me a story", context(personalityType = 1)).replyId)
        assertEquals("tone.split.dry", engine.reply("tell me a story", context(personalityType = 10)).replyId)
    }

    @Test
    fun moodDeltaIsClampedToTheUnitRange() {
        val results = (0L..15L).map { turn -> engine.reply("hi", context(turnCounter = turn)) }
        assertTrue(results.all { it.moodDelta in -1..1 })
        val boosted = results.firstOrNull { it.replyId == "greeting.r3" }
        assertNotNull("expected the +5 authored line to be picked across 16 turns", boosted)
        assertEquals(1, boosted!!.moodDelta)
    }

    @Test
    fun matchedReplyChipsFallBackToGlobalPackChips() {
        val identity = engine.reply("what are you", context())
        assertEquals("fc.identity.1", identity.intentId)
        assertEquals(listOf("chip.greet", "chip.plain", "chip.extra"), identity.chips.map { it.id })
    }

    @Test
    fun suggestionsAreConditionFilteredDeterministicAndCapped() {
        val first = engine.suggestions(context())
        assertEquals(first, engine.suggestions(context()))
        assertEquals(listOf("chip.greet", "chip.plain", "chip.extra"), first.map { it.id })
        assertEquals(1, engine.suggestions(context(), limit = 1).size)
        assertTrue(engine.suggestions(context(), limit = 0).isEmpty())
        assertEquals(4, engine.suggestions(context(audience = FreeChatAudience.WILD), limit = 10).size)
    }

    @Test
    fun unmatchedInputFallsBackAndNeverThrows() {
        val result = engine.reply("zzzz qqqq", context())
        assertNotEquals(0, result.fallbackLevel)
        assertTrue(result.text.isNotBlank())
        assertEquals(FreeChatSafetyClass.SAFE, result.safetyClass)
        listOf("", "   ", "!!!", "😂😂😂", "a".repeat(5000)).forEach { input ->
            assertTrue(engine.reply(input, context()).text.isNotBlank())
        }
        assertTrue(engine.classify("zzzz").let { it == FreeChatSafetyClass.SAFE })
    }

    @Test
    fun emptyPackReturnsTheInternalConstantLine() {
        val empty = FreeChatEngine(emptyContent())
        val result = empty.reply("hello", context())
        assertEquals(4, result.fallbackLevel)
        assertTrue(result.text.isNotBlank())
        assertEquals("", result.intentId)
        assertEquals("fallback", result.topic)
        assertTrue(result.chips.isEmpty())
        assertEquals(1, result.contentVersion)
        assertEquals(FreeChatSafetyClass.SAFE, result.safetyClass)
        assertEquals(result, empty.reply("hello", context()))
        assertTrue(empty.suggestions(context()).isEmpty())
        assertTrue(empty.reply("", context()).text.isNotBlank())
    }

    @Test
    fun classifyUsesTheSameSafetyLexiconAsTheReplyPath() {
        assertEquals(FreeChatSafetyClass.S2, engine.classify("send nudes"))
        assertEquals(FreeChatSafetyClass.SAFE, engine.classify("hello"))
        assertEquals(FreeChatSafetyClass.SAFE, engine.classify(""))
    }

    @Test
    fun speciesMentionBoostsTheSpeciesIntent() {
        val result = engine.reply("agumon", context())
        assertEquals("fc.agumon.1", result.intentId)
        assertEquals(0, result.fallbackLevel)
        assertTrue(result.scoreQ16 > 0)
    }
}
