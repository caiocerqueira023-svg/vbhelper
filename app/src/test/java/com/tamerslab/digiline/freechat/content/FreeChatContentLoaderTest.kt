package com.tamerslab.digiline.freechat.content

import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatFallbackDto
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatManifestDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto
import com.tamerslab.digiline.freechat.model.FreeChatReplyDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM gate for the map-reader seam (contract §3): the production loader must be loadable without an
 * emulator, must name the failing asset file, and the pure validator must catch hard pack errors.
 */
class FreeChatContentLoaderTest {

    private val validFiles = mapOf(
        "freechat/v1/en/manifest.json" to
            """
            { "schemaVersion": 1, "contentVersion": 1, "locale": "en",
              "files": { "intents": "free_chat.json", "speciesVoice": "species_voice.json" } }
            """.trimIndent(),
        "freechat/v1/en/free_chat.json" to
            """
            { "schemaVersion": 1, "contentVersion": 1, "locale": "en",
              "intents": [{
                "id": "fc.greeting.1", "topic": "greeting", "tags": ["owned"],
                "matchPhrases": ["hi", "hello"], "keywords": ["hi"],
                "conditions": { "audience": "owned" },
                "replies": [{ "id": "fc.greeting.1.r1",
                              "text": "You came back. Good. I was getting bored of the static here.",
                              "tone": "warm", "authoringStatus": "draft" }],
                "chips": [{ "id": "chip.what_are_you", "text": "What are you?", "intentId": "fc.greeting.1" }],
                "fallbackId": "fallback.1"
              }],
              "chips": [{ "id": "chip.what_are_you", "text": "What are you?", "intentId": "fc.greeting.1" }],
              "fallbacks": [
                { "level": 1, "replies": [{ "id": "fallback.1.r1", "text": "Hm. Say that again?" }] },
                { "level": 2, "replies": [] }, { "level": 3, "replies": [] }, { "level": 4, "replies": [] }
              ],
              "safety": { "S1": ["idiot"], "S2": ["kill you"], "S3": ["suicide"], "PII": ["cpf"] } }
            """.trimIndent(),
        "freechat/v1/en/species_voice.json" to
            """
            { "schemaVersion": 1, "voices": [
              { "speciesKey": "aegiomon", "speciesName": "Aegiomon", "level": "Adult",
                "type": "God Man", "moves": ["Sprite Dive"], "tier": 2 } ] }
            """.trimIndent(),
    )

    @Test
    fun `map-backed reader loads manifest pack and species voices`() = runBlocking {
        val content = FreeChatContentLoader { path -> validFiles.getValue(path) }.load()

        assertEquals(1, content.contentVersion)
        assertEquals("en", content.locale)
        assertEquals(setOf("aegiomon"), content.speciesVoices.keys)
        assertEquals(listOf("Aegiomon"), content.speciesNames)
        assertEquals("greeting", content.pack.intents.single().topic)
        assertEquals("warm", content.pack.intents.single().replies.single().tone)
        assertTrue(
            FreeChatContentValidator().validate(content).joinToString("\n"),
            FreeChatContentValidator().validate(content).isEmpty(),
        )
    }

    @Test
    fun `loader names the unreadable asset file in the failure message`() = runBlocking {
        val loader = FreeChatContentLoader { path ->
            if (path.endsWith("species_voice.json")) error("boom")
            validFiles.getValue(path)
        }
        val failure = runCatching { loader.load() }.exceptionOrNull()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
        assertTrue(
            "message should name the asset: ${failure?.message}",
            failure?.message.orEmpty().contains("species_voice.json"),
        )
    }

    @Test
    fun `validator reports duplicate ids and missing fallback levels`() {
        val pack = FreeChatPackDto(
            intents = listOf(
                FreeChatIntentDto(
                    id = "fc.greeting.1",
                    topic = "greeting",
                    replies = listOf(FreeChatReplyDto(id = "reply.1", text = "One.")),
                ),
                FreeChatIntentDto(
                    id = "fc.greeting.1",
                    topic = "greeting",
                    replies = listOf(FreeChatReplyDto(id = "reply.1", text = "Two.")),
                ),
            ),
            fallbacks = listOf(
                FreeChatFallbackDto(
                    level = 1,
                    replies = listOf(FreeChatReplyDto(id = "fallback.1.r1", text = "Hm?")),
                ),
            ),
            safety = mapOf("S1" to listOf("idiot")),
        )
        val content = FreeChatContent(
            manifest = FreeChatManifestDto(),
            pack = pack,
            speciesVoices = emptyMap(),
            speciesNames = emptyList(),
            contentVersion = 1,
            locale = "en",
        )
        val errors = FreeChatContentValidator().validate(content).joinToString("\n")

        assertTrue(errors, errors.contains("intent id 'fc.greeting.1' is duplicated"))
        assertTrue(errors, errors.contains("reply id 'reply.1' is duplicated"))
        assertTrue(errors, errors.contains("fallbacks are missing level 4"))
    }
}
