package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.FreeChatEngine
import com.tamerslab.digiline.freechat.model.FreeChatAudience
import com.tamerslab.digiline.freechat.model.FreeChatDatabasePairDto
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Owner requirement: generated Digimon content is the priority answer source; the licensed
 * database layer (`database_fallback.json`) only answers turns the generated corpus cannot.
 */
class FreeChatDatabaseFallbackTest {

    private val databasePairs: List<FreeChatDatabasePairDto> = listOf(
        databasePair("how was your day", "It was quiet, mostly. Thanks for asking."),
        databasePair("what is your favorite food", "I like warm bread and anything with butter."),
        databasePair("the weather outside is strange today", "Clouds again. The sky never makes up its mind."),
    )

    private fun contentWith(
        extraIntents: List<FreeChatIntentDto> = emptyList(),
    ): com.tamerslab.digiline.freechat.model.FreeChatContent = content(
        intents = demoIntents() + extraIntents,
        chips = demoChips(),
        fallbacks = authoredFallbacks(),
        safety = demoSafety(),
        voices = demoVoices(),
        databaseFallbacks = databasePairs,
        contentVersion = 3,
    )

    private fun generatedSmallTalk(): FreeChatIntentDto = intent(
        id = "fc.generated.smalltalk",
        topic = "smalltalk",
        matchPhrases = listOf("how was your day"),
        replies = listOf(reply("generated.smalltalk.r1", "Generated smalltalk line.")),
    )

    @Test
    fun generatedContentWinsOverTheDatabaseForTheSameInput() {
        val engine = FreeChatEngine(contentWith(extraIntents = listOf(generatedSmallTalk())))
        val result = engine.reply("how was your day", context(turnCounter = 4))
        assertEquals(0, result.fallbackLevel)
        assertEquals("fc.generated.smalltalk", result.intentId)
        assertEquals("Generated smalltalk line.", result.text)
        assertFalse(result.fromDatabase)
    }

    @Test
    fun databaseAnswersWhenTheGeneratedCorpusHasNoAnswer() {
        val engine = FreeChatEngine(contentWith())
        val result = engine.reply("how was your day", context(turnCounter = 4))
        assertEquals(4, result.fallbackLevel)
        assertTrue(result.fromDatabase)
        assertEquals("database", result.topic)
        assertEquals("It was quiet, mostly. Thanks for asking.", result.text)
    }

    @Test
    fun databaseFallbackIsDeterministicForTheSameTurn() {
        val engine = FreeChatEngine(contentWith())
        val turn = context(turnCounter = 9)
        val first = engine.reply("the weather outside is strange today", turn)
        assertEquals(first, engine.reply("the weather outside is strange today", turn))
        assertTrue(first.fromDatabase)
    }

    @Test
    fun databaseLayerIsNotUsedForWildContacts() {
        val engine = FreeChatEngine(contentWith())
        assertFalse(engine.reply("how was your day", context(audience = FreeChatAudience.WILD)).fromDatabase)
    }

    @Test
    fun contextFilteredGeneratedIntentFallsThroughToTheDatabase() {
        // The generated intent exists but its hard personality condition rejects this partner, so
        // generated content has no usable answer for this turn and the database rung answers.
        val typedIntent = intent(
            id = "fc.generated.typed",
            topic = "smalltalk",
            matchPhrases = listOf("how was your day"),
            conditions = condition(types = listOf(5)),
            replies = listOf(reply("generated.typed.r1", "Generated typed line.")),
        )
        val engine = FreeChatEngine(contentWith(extraIntents = listOf(typedIntent)))
        val result = engine.reply("how was your day", context(personalityType = 7, turnCounter = 2))
        assertTrue(result.fromDatabase)
        assertEquals(4, result.fallbackLevel)
    }

    @Test
    fun unmatchedInputGetsARepairLineInsteadOfARandomDatasetQuote() {
        val engine = FreeChatEngine(contentWith())
        val result = engine.reply("zzzz qqqq", context(turnCounter = 3))
        assertFalse("random dataset quote used", result.fromDatabase)
        assertEquals("repair", result.topic)
        assertEquals(3, result.fallbackLevel)
        assertTrue(result.text.isNotBlank())
        assertEquals(result, engine.reply("zzzz qqqq", context(turnCounter = 3)))
    }

    @Test
    fun authoredFallbackIsUsedWhenThePackHasNoDatabaseLayer() {
        val engine = FreeChatEngine(
            content(
                intents = demoIntents(),
                chips = demoChips(),
                fallbacks = authoredFallbacks(),
                safety = demoSafety(),
                voices = demoVoices(),
                contentVersion = 3,
            ),
        )
        val result = engine.reply("zzzz qqqq", context())
        assertFalse(result.fromDatabase)
        assertEquals(1, result.fallbackLevel)
    }
}
