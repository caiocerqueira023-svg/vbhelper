package com.tamerslab.digiline.freechat.content

import com.tamerslab.digiline.freechat.engine.FreeChatEngine
import com.tamerslab.digiline.freechat.model.FreeChatAudience
import com.tamerslab.digiline.freechat.model.FreeChatContext
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * End-to-end conversation-quality regression over the shipped pack (owner bug report 2026-11-18:
 * basic phrases returned disconnected "frases de efeito" and the database layer never answered).
 *
 * Guards the priority/fallback contract:
 * - generated content answers basic EN and PT-BR conversation moves with the matching intent;
 * - the generic composed species-voice one-liner must not preempt the database layer;
 * - the database layer answers input missing from the authored corpus;
 * - shipped database pairs stay short, conversational and free of chatbot/screenplay junk.
 */
class FreeChatConversationQualityTest {

    private val content by lazy { loadShippedContent() }
    private val engine by lazy { FreeChatEngine(content) }

    private fun partnerContext(): FreeChatContext = FreeChatContext(
        individualId = "quality",
        speciesKey = "agumon",
        speciesName = "Agumon",
        level = "Child",
        speciesType = "Vaccine",
        stageTier = 2,
        personalityType = 6,
        core = "VALOR",
        mood = 55,
        audience = FreeChatAudience.OWNED,
    )

    @Test
    fun englishAndPortugueseGreetingsHitTheAuthoredGreetingIntents() {
        val greetings = listOf("hi", "hello", "hey there", "oi", "olá", "bom dia", "tudo bem?")
        greetings.forEach { input ->
            val result = engine.reply(input, partnerContext())
            assertEquals("expected an authored greeting for '$input'", 0, result.fallbackLevel)
            assertTrue(
                "expected a greeting intent for '$input', got ${result.intentId}",
                result.intentId.startsWith("fc.greeting"),
            )
        }
    }

    @Test
    fun thanksAndSmallTalkHaveAuthoredAnswers() {
        listOf("thanks", "thank you", "obrigado", "valeu").forEach { input ->
            val result = engine.reply(input, partnerContext())
            assertEquals("expected the authored thanks intent for '$input'", "fc.thanks.1", result.intentId)
            assertEquals(0, result.fallbackLevel)
        }
        val questionBack = engine.reply("e você?", partnerContext())
        assertEquals("fc.how_about_you.1", questionBack.intentId)
    }

    @Test
    fun unmatchedInputNeverFallsBackToTheSpeciesOneLiner() {
        val inputs = listOf(
            "i love pizza", "tell me about yourself", "the weather is nice today",
            "asdkjhasd", "eu gosto de pizza", "meu vizinho tem um gato",
        )
        inputs.forEach { input ->
            val result = engine.reply(input, partnerContext())
            assertTrue("unexpected species-voice rung for '$input'", result.fallbackLevel != 1)
            assertTrue("blank answer for '$input'", result.text.isNotBlank())
        }
    }

    @Test
    fun unmatchedInputGetsAnInWorldRepairLineInsteadOfRandomMovieDialogue() {
        val inputs = listOf(
            "asdkjhasd", "what?", "her who?", "shift? what shift", "what do you mean",
            "nao entendi", "como assim?",
        )
        inputs.forEach { input ->
            val result = engine.reply(input, partnerContext())
            assertFalse("random dataset quote for '$input': ${result.text}", result.fromDatabase)
            assertEquals("expected a repair move for '$input' (got ${result.topic})", "repair", result.topic)
            assertTrue("blank repair line for '$input'", result.text.isNotBlank())
            assertTrue("species one-liner leaked for '$input'", result.fallbackLevel != 1)
        }
    }

    @Test
    fun shippedDatabasePairsAreConversationalAndJunkFree() {
        assertTrue("database layer missing from the shipped pack", content.databaseFallbacks.size >= 500)
        val banned = listOf(
            "as an ai", "language model", "chatbot", "wikipedia", "president",
            "rabbi", "artificial intelligence", "openai", "chatgpt",
        )
        val botPattern = Regex("\\b(a bot|chat\\s?bot|assistant|programmed)\\b")
        content.databaseFallbacks.forEach { pair ->
            assertTrue("database response too long: ${pair.response}", pair.response.length <= 200)
            assertFalse("database response contains screenplay junk: ${pair.response}", pair.response.contains("--"))
            banned.forEach { term ->
                val pattern = Regex("(?<![a-z])${Regex.escape(term)}(?![a-z])")
                assertFalse(
                    "database response contains '$term': ${pair.response}",
                    pattern.containsMatchIn(pair.response.lowercase()),
                )
            }
            assertFalse("database response reads like a chatbot: ${pair.response}", botPattern.containsMatchIn(pair.response.lowercase()))
            assertTrue("database pair missing source/license", pair.source.isNotBlank() && pair.license.isNotBlank())
        }
    }

    @Test
    fun basicConversationDoesNotRepeatOneAnswer() {
        val inputs = listOf("hi", "how are you", "thanks", "i am sad", "i love pizza", "good night", "oi")
        val answers = inputs.map { engine.reply(it, partnerContext()).text }.toSet()
        assertTrue("too many identical answers (${answers.size}/${inputs.size})", answers.size >= inputs.size - 1)
    }

    private fun loadShippedContent() = runBlocking {
        val base = listOf(File("src/main/assets"), File("app/src/main/assets"))
            .firstOrNull { File(it, "freechat/v1/en/manifest.json").isFile }
        assumeTrue("Free Talk assets are not present in this checkout", base != null)
        FreeChatContentLoader { path -> File(requireNotNull(base), path).readText(Charsets.UTF_8) }
            .load(FreeChatContentSource.DEFAULT_ASSET_ROOT)
    }
}
