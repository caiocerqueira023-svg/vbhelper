package com.tamerslab.digiline.content

import com.tamerslab.digiline.model.ContentManifest
import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationNodeDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.NegotiationPackDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SMT-style negotiation contract: every answer that continues the conversation must carry an
 * authored demon reaction, and the follow-up must continue the same authored topic. These checks
 * are what stop the old "generic recruitment skeleton" from shipping again.
 */
class DigilineNegotiationDialogueValidatorTest {

    private val validator = DigilineContentValidator()

    private fun pack(flow: NegotiationFlowDto): DigilineContentPack = DigilineContentPack(
        manifest = ContentManifest(
            schemaVersion = DigilineContentLoader.SUPPORTED_SCHEMA_VERSION,
            contentVersion = 3,
            locale = "en",
            files = mapOf(
                ContentManifest.FILE_SPECIES to "en/species_questions.json",
                ContentManifest.FILE_GENERIC to "en/generic_questions.json",
                ContentManifest.FILE_QUIZ to "en/quiz.json",
                ContentManifest.FILE_NEGOTIATION to "en/negotiation.json",
                ContentManifest.FILE_PERSUASION to "en/persuasion.json",
                ContentManifest.FILE_EVENTS to "en/event_lines.json",
                ContentManifest.FILE_LORE to "en/lore_species.json",
            ),
        ),
        negotiation = NegotiationPackDto(
            personalities = (1..16).associate { type ->
                type.toString() to (1..4).associate { tier -> tier.toString() to flow }
            }
        ),
    )

    private fun dialogueErrors(pack: DigilineContentPack): List<String> =
        validator.report(pack).errors.filter {
            it.contains("authored reaction") ||
                it.contains("does not continue the follow-up topic") ||
                it.contains("authored dialogue topic") ||
                it.contains("single answer")
        }

    private fun node(
        id: String,
        text: String,
        tag: String,
        topic: String,
        outcome: String? = null,
        options: List<NegotiationOptionDto> = emptyList(),
    ) = NegotiationNodeDto(
        id = id,
        text = text,
        tag = tag,
        topic = topic,
        outcome = outcome,
        options = options,
    )

    private fun validFlow(): NegotiationFlowDto = NegotiationFlowDto(
        start = "greeting",
        nodes = mapOf(
            "greeting" to node(
                id = "greeting", text = "You stopped. Why?", tag = "greeting", topic = "intent",
                options = listOf(
                    NegotiationOptionDto(
                        id = "a", text = "I need a place to belong.", next = "prove",
                        reaction = "A stray looking for a pack. Hm.", topic = "intent",
                    ),
                    NegotiationOptionDto(
                        id = "b", text = "I came to fight.", next = "join",
                        reaction = "Straight to it. I can respect that.", topic = "intent",
                    ),
                ),
            ),
            "prove" to node(
                id = "prove", text = "Then show me what you can do.", tag = "probe", topic = "intent",
                options = listOf(
                    NegotiationOptionDto(
                        id = "a", text = "I'll fight for it.", next = "join",
                        reaction = "That's the answer I wanted.", topic = "intent",
                    ),
                    NegotiationOptionDto(
                        id = "b", text = "I'd rather talk first.", next = "join",
                        reaction = "Words first. Fine.", topic = "intent",
                    ),
                ),
            ),
            "join" to node(
                id = "join", text = "Then we have a deal. I'm in.", tag = "join",
                topic = "intent", outcome = "JOIN",
            ),
        ),
    )

    @Test
    fun `authored reaction and topic continuity pass the dialogue checks`() {
        assertTrue(dialogueErrors(pack(validFlow())).isEmpty())
    }

    @Test
    fun `option continuing the conversation without a reaction is rejected`() {
        val flow = validFlow()
        val broken = flow.copy(
            nodes = flow.nodes + ("greeting" to flow.nodes.getValue("greeting").copy(
                options = flow.nodes.getValue("greeting").options.map { it.copy(reaction = null) }
            ))
        )
        val errors = dialogueErrors(pack(broken))
        assertTrue(errors.any { it.contains("no authored reaction") })
    }

    @Test
    fun `follow-up on a different topic is rejected`() {
        val flow = validFlow()
        val broken = flow.copy(
            nodes = flow.nodes + ("prove" to flow.nodes.getValue("prove").copy(topic = "payment"))
        )
        val errors = dialogueErrors(pack(broken))
        assertTrue(errors.any { it.contains("does not continue the follow-up topic") })
    }

    @Test
    fun `single-answer negotiation questions are rejected`() {
        assertTrue(
            dialogueErrors(pack(validFlow())).isEmpty(),
        )
        val flow = validFlow()
        val oneAnswer = flow.copy(
            nodes = flow.nodes + ("greeting" to flow.nodes.getValue("greeting").copy(
                options = flow.nodes.getValue("greeting").options.take(1)
            ))
        )
        val errors = dialogueErrors(pack(oneAnswer))
        assertTrue(errors.any { it.contains("single answer") })
    }

    @Test
    fun `missing topic is reported for negotiation nodes and options`() {
        val flow = validFlow()
        val broken = flow.copy(
            nodes = flow.nodes + ("greeting" to flow.nodes.getValue("greeting").copy(
                topic = null,
                options = flow.nodes.getValue("greeting").options.map { it.copy(topic = null) }
            ))
        )
        val errors = dialogueErrors(pack(broken))
        assertFalse(errors.isEmpty())
    }
}
