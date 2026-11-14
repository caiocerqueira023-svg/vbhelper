package com.tamerslab.digiline

import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.NegotiationPackDto
import com.tamerslab.digiline.model.PersuasionPackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DigilineContentPackTest {
    private val speciesQuestions = listOf(
        questionCard("sp.aegiomon.1", speciesKey = "aegiomon", speciesName = "Aegiomon"),
        questionCard("sp.aegiomon.2", speciesKey = "aegiomon", speciesName = "Aegiomon"),
        questionCard("sp.agumon.1", speciesKey = "agumon", speciesName = "Agumon"),
    )

    private val genericQuestions = listOf(
        questionCard("gen.valor.1", askerTypes = listOf(5, 6, 7, 8), core = "VALOR"),
        questionCard("gen.sly.1", askerTypes = listOf(10), core = "WISDOM"),
        questionCard("gen.neutral.1", askerTypes = emptyList()),
    )

    @Test
    fun speciesLookupIsNormalizedAndVariantStable() {
        val pack = testPack(questions = speciesQuestions)
        val first = pack.speciesQuestion("Aegiomon", variant = 0)
        assertNotNull(first)
        assertEquals(first, pack.speciesQuestion("aegiomon", variant = 0))
        assertEquals(first, pack.speciesQuestion("AEGIOMON", variant = 0))
        val second = pack.speciesQuestion("aegiomon", variant = 1)
        assertNotNull(second)
        assertTrue(first!!.id != second!!.id)
        assertNull(pack.speciesQuestion("unknownmon", variant = 0))
        assertNull(pack.speciesQuestion("", variant = 0))
        assertNotNull(pack.speciesQuestion("agumon", 0))
        assertEquals(2, pack.speciesQuestions("aegiomon").size)
    }

    @Test
    fun genericQuestionsRespectAskerTypes() {
        val pack = testPack(genericQuestions = genericQuestions)
        repeat(10) { index ->
            val seed = index.toLong() * 97L
            val valor = pack.genericQuestion(6, seed)
            assertNotNull(valor)
            assertTrue(valor!!.askerTypes.contains(6))
            val sly = pack.genericQuestion(10, seed)
            assertEquals("gen.sly.1", sly?.id)
        }
    }

    @Test
    fun genericFallbackUsesCoreThenNeutralCards() {
        val coreOnly = testPack(
            genericQuestions = listOf(questionCard("gen.valor.1", askerTypes = listOf(5), core = "VALOR")),
        )
        // Type 7 (VALOR core) is not listed as an asker but can use the core fallback.
        assertEquals("gen.valor.1", coreOnly.genericQuestion(7, 1L)?.id)

        val neutralOnly = testPack(genericQuestions = listOf(questionCard("gen.neutral.1")))
        assertEquals("gen.neutral.1", neutralOnly.genericQuestion(16, 1L)?.id)

        val empty = testPack()
        assertNull(empty.genericQuestion(6, 1L))
    }

    @Test
    fun otherPersonalityCardsAreNeverAskedWithoutAFallback() {
        val pack = testPack(genericQuestions = listOf(questionCard("gen.6", askerTypes = listOf(6))))
        assertNotNull(pack.genericQuestion(6, 1L))
        assertNull(pack.genericQuestion(7, 1L))
    }

    @Test
    fun quizPickStaysWithinTierPlusOrMinusOne() {
        val quizzes = listOf(
            quizCard("quiz.t1", stageTier = 1),
            quizCard("quiz.t2", stageTier = 2),
            quizCard("quiz.t3", stageTier = 3),
            quizCard("quiz.t4", stageTier = 4),
        )
        val pack = testPack(quizzes = quizzes)
        repeat(40) { index ->
            val quiz = pack.quiz(stageTier = 2, seed = index.toLong())
            assertNotNull(quiz)
            assertTrue(quiz!!.id in setOf("quiz.t1", "quiz.t2", "quiz.t3"))
        }
        assertNull(testPack().quiz(stageTier = 2, seed = 1L))
    }

    @Test
    fun quizExclusionFallsBackInsteadOfGoingSilent() {
        val pack = testPack(quizzes = listOf(quizCard("quiz.only", stageTier = 2)))
        val quiz = pack.quiz(stageTier = 2, seed = 5L, excludedIds = setOf("quiz.only"))
        assertEquals("quiz.only", quiz?.id)
        assertEquals("quiz.only", pack.quizById("quiz.only")?.id)
        assertNull(pack.quizById("missing"))
    }

    @Test
    fun flowLookupsUsePersonalityAndTierKeys() {
        val negotiationFlow = flow("start", node("start", tag = "greeting"))
        val persuasionFlow = flow("start", node("start", tag = "greeting"))
        val pack = testPack(
            negotiation = NegotiationPackDto(personalities = mapOf("6" to mapOf("2" to negotiationFlow))),
            persuasion = PersuasionPackDto(personalities = mapOf("6" to mapOf("0" to persuasionFlow))),
        )
        assertEquals(negotiationFlow, pack.negotiationFlow(6, 2))
        assertNull(pack.negotiationFlow(6, 1))
        assertNull(pack.negotiationFlow(17, 2))
        assertNull(pack.negotiationFlow(6, 0))
        assertEquals(persuasionFlow, pack.persuasionFlow(6, 0))
        assertNull(pack.persuasionFlow(6, 4))
        assertNull(pack.persuasionFlow(0, 0))
    }

    @Test
    fun eventLinesPreferPersonalitySpecificAndFallBackToShared() {
        val lines = listOf(
            eventLine("ev.battle_win.shared", "battle_win", text = "shared"),
            eventLine("ev.battle_win.6", "battle_win", types = listOf(6), text = "typed"),
            eventLine("ev.injury.shared", "injury", text = "shared injury"),
        )
        val pack = testPack(eventLines = lines)
        assertEquals(listOf("ev.battle_win.6"), pack.eventLines("battle_win", 6).map { it.id })
        assertEquals(listOf("ev.battle_win.shared"), pack.eventLines("battle_win", 9).map { it.id })
        assertEquals(listOf("ev.injury.shared"), pack.eventLines("injury", 9).map { it.id })
        assertTrue(pack.eventLines("unknown", 6).isEmpty())
        assertTrue(pack.eventLines("", 6).isEmpty())
        assertEquals(2, pack.eventLines("battle_win").size)
    }

    @Test
    fun loreLookupIgnoresCaseAndPunctuation() {
        val pack = testPack(lore = listOf(loreFact("aegiomon", "Aegiomon")))
        assertEquals("Aegiomon", pack.loreFact("Aegiomon")?.speciesName)
        assertEquals("Aegiomon", pack.loreFact("AEGIOMON")?.speciesName)
        assertNull(pack.loreFact("nope"))
    }

    @Test
    fun emptyPackIsSafeForEveryLookup() {
        val pack = DigilineContentPack.empty()
        assertFalse(pack.hasContent)
        assertEquals(0, pack.contentVersion)
        assertNull(pack.speciesQuestion("aegiomon", 0))
        assertNull(pack.genericQuestion(1, 1L))
        assertNull(pack.quiz(2, 1L))
        assertNull(pack.negotiationFlow(1, 1))
        assertNull(pack.persuasionFlow(1, 0))
        assertTrue(pack.eventLines("battle_win", 1).isEmpty())
        assertNull(pack.loreFact("aegiomon"))
        assertTrue(pack.factsFor("aegiomon").isEmpty())
        assertEquals(0, pack.speciesQuestions("aegiomon").size)
    }
}
