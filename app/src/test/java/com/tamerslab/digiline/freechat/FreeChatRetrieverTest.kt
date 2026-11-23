package com.tamerslab.digiline.freechat

import com.tamerslab.digiline.freechat.engine.EntityLexicon
import com.tamerslab.digiline.freechat.engine.FreeChatNormalizer
import com.tamerslab.digiline.freechat.engine.FreeChatRetriever
import com.tamerslab.digiline.freechat.engine.FreeChatEntityMentions
import com.tamerslab.digiline.freechat.engine.ScoredIntent
import com.tamerslab.digiline.freechat.model.FreeChatIntentDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeChatRetrieverTest {
    private val normalizer = FreeChatNormalizer()
    private val retriever = FreeChatRetriever(FreeChatPackDto(intents = demoIntents()))

    private fun candidates(
        text: String,
        entities: FreeChatEntityMentions = FreeChatEntityMentions.EMPTY,
        limit: Int = 8,
    ): List<ScoredIntent> = retriever.candidates(normalizer.normalize(text), entities, limit)

    private fun ids(scored: List<ScoredIntent>): List<String> = scored.map { it.intent.id }

    @Test
    fun greetingQueryRanksGreetingIntentFirst() {
        assertEquals("fc.greeting.1", candidates("hello").first().intent.id)
        assertEquals("fc.greeting.1", candidates("bom dia").first().intent.id)
        assertEquals("fc.identity.1", candidates("what are you").first().intent.id)
    }

    @Test
    fun retrievalIsDeterministicAcrossCallsAndInstances() {
        val first = candidates("hello digital world")
        val second = candidates("hello digital world")
        assertEquals(ids(first), ids(second))
        assertEquals(first.map { it.scoreQ16 }, second.map { it.scoreQ16 })

        val freshInstance = FreeChatRetriever(FreeChatPackDto(intents = demoIntents()))
        val third = freshInstance.candidates(normalizer.normalize("hello digital world"), FreeChatEntityMentions.EMPTY)
        assertEquals(ids(first), ids(third))
    }

    @Test
    fun packOrderDoesNotChangeRanking() {
        val reversed = FreeChatRetriever(FreeChatPackDto(intents = demoIntents().reversed()))
        val expected = candidates("hello").map { it.intent.id }
        val actual = reversed.candidates(normalizer.normalize("hello"), FreeChatEntityMentions.EMPTY)
            .map { it.intent.id }
        assertEquals(expected, actual)
    }

    @Test
    fun exactNormalizedPhraseIsAcceptedAcrossSurfaceForms() {
        val lower = candidates("bom dia")
        val shouty = candidates("BOM DIA!!!")
        assertEquals(ids(lower), ids(shouty))
        assertEquals("fc.greeting.1", lower.first().intent.id)
    }

    @Test
    fun equalScoresTieBreakOnIntentIdAscending() {
        val tied = listOf(
            intent(id = "fc.zzz", matchPhrases = listOf("tie check"), replies = listOf(reply("zzz.r1"))),
            intent(id = "fc.aaa", matchPhrases = listOf("tie check"), replies = listOf(reply("aaa.r1"))),
            intent(id = "fc.mmm", matchPhrases = listOf("tie check"), replies = listOf(reply("mmm.r1"))),
        )
        val tiedRetriever = FreeChatRetriever(FreeChatPackDto(intents = tied))
        val result = tiedRetriever.candidates(normalizer.normalize("tie check"), FreeChatEntityMentions.EMPTY)
        assertEquals(listOf("fc.aaa", "fc.mmm", "fc.zzz"), result.map { it.intent.id })
        assertEquals(result[0].scoreQ16, result[1].scoreQ16)
        assertEquals(result[1].scoreQ16, result[2].scoreQ16)
    }

    @Test
    fun limitCapsTheResultList() {
        assertEquals(1, candidates("hello agumon", limit = 1).size)
        assertEquals(2, candidates("hello agumon", limit = 2).size)
        assertEquals(0, candidates("hello", limit = 0).size)
    }

    @Test
    fun noEvidenceYieldsNoCandidates() {
        assertTrue(candidates("zzzz qqqq").isEmpty())
        assertTrue(candidates("").isEmpty())
    }

    @Test
    fun entityMentionsBoostTheMatchingIntent() {
        val entities = EntityLexicon(demoVoices().map { it.speciesName }).extract(normalizer.normalize("agumon"))
        val plain = candidates("agumon").first { it.intent.id == "fc.agumon.1" }
        val boosted = candidates("agumon", entities).first { it.intent.id == "fc.agumon.1" }
        assertTrue(boosted.entityHits > 0)
        assertTrue(boosted.scoreQ16 > plain.scoreQ16)
        assertEquals("fc.agumon.1", candidates("agumon", entities).first().intent.id)
    }

    @Test
    fun entityOnlyEvidenceStillProducesACandidate() {
        val lorePack = FreeChatPackDto(
            intents = listOf(
                intent(
                    id = "fc.lore.1",
                    matchPhrases = listOf("tell me"),
                    replies = listOf(reply("lore.r1", loreRefs = listOf("agumon"))),
                ),
            ),
        )
        val loreRetriever = FreeChatRetriever(lorePack)
        val entities = EntityLexicon(listOf("Agumon")).extract(normalizer.normalize("agumon"))
        val withEntities = loreRetriever.candidates(normalizer.normalize("agumon"), entities)
        assertFalse(withEntities.isEmpty())
        assertEquals("fc.lore.1", withEntities.first().intent.id)
        assertTrue(withEntities.first().entityHits > 0)
        assertTrue(loreRetriever.candidates(normalizer.normalize("agumon"), FreeChatEntityMentions.EMPTY).isEmpty())
    }

    @Test
    fun scoresAreNonNegativeAndSortedDescending() {
        val scored = candidates("hello there digital world agumon")
        assertFalse(scored.isEmpty())
        for (candidate in scored) {
            assertTrue(candidate.scoreQ16 >= 0)
            assertTrue(candidate.entityHits >= 0)
        }
        for (index in 1 until scored.size) {
            assertTrue(scored[index - 1].scoreQ16 >= scored[index].scoreQ16)
        }
    }

    @Test
    fun emptyPackYieldsNoCandidates() {
        val emptyRetriever = FreeChatRetriever(FreeChatPackDto())
        assertTrue(emptyRetriever.candidates(normalizer.normalize("hello"), FreeChatEntityMentions.EMPTY).isEmpty())
    }

    @Test
    fun intentsWithoutMatchPhrasesNeverMatchTextButCanMatchEntities() {
        val bare: FreeChatIntentDto = intent(
            id = "fc.bare.1",
            replies = listOf(reply("bare.r1", loreRefs = listOf("agumon"))),
        )
        val bareRetriever = FreeChatRetriever(FreeChatPackDto(intents = listOf(bare)))
        assertTrue(bareRetriever.candidates(normalizer.normalize("hello"), FreeChatEntityMentions.EMPTY).isEmpty())
        val entities = EntityLexicon(listOf("Agumon")).extract(normalizer.normalize("agumon"))
        assertFalse(bareRetriever.candidates(normalizer.normalize("hello"), entities).isEmpty())
    }
}
