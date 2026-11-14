package com.tamerslab.digiline

import com.tamerslab.digiline.engine.QuestionScheduler
import com.tamerslab.digiline.model.QuestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionSchedulerTest {
    private val speciesCards = listOf(
        questionCard("sp.b.1", speciesKey = "b", options = listOf(answerOption("x"), answerOption("y"))),
        questionCard("sp.a.1", speciesKey = "a"),
        questionCard("sp.a.2", speciesKey = "a"),
    )

    @Test
    fun selectionIsStableForTheSameSeed() {
        val first = QuestionScheduler.schedule(speciesCards, QuestionKind.SPECIES, seed = 1234L, variant = 2)
        val second = QuestionScheduler.schedule(speciesCards, QuestionKind.SPECIES, seed = 1234L, variant = 2)
        assertNotNull(first)
        assertEquals(first, second)
        assertEquals(first!!.card.id, second!!.card.id)
        assertEquals(first.optionOrder, second.optionOrder)
    }

    @Test
    fun optionOrderContainsEveryOptionOnce() {
        val card = questionCard(
            "sp.one",
            options = listOf(answerOption("a"), answerOption("b"), answerOption("c"), answerOption("d")),
        )
        val selection = QuestionScheduler.arrange(card, seed = 42L)
        assertEquals(4, selection.optionOrder.size)
        assertEquals(card.options.map { it.id }.toSet(), selection.optionOrder.toSet())
        assertEquals(4, selection.orderedOptions.size)
    }

    @Test
    fun recentlyAskedCardsAreAvoided() {
        val selection = QuestionScheduler.schedule(
            candidates = speciesCards,
            kind = QuestionKind.SPECIES,
            seed = 7L,
            recentIds = setOf("sp.a.1", "sp.a.2"),
        )
        assertEquals("sp.b.1", selection?.card?.id)
    }

    @Test
    fun anExhaustedPoolFallsBackInsteadOfGoingSilent() {
        val selection = QuestionScheduler.schedule(
            candidates = speciesCards,
            kind = QuestionKind.SPECIES,
            seed = 7L,
            recentIds = speciesCards.map { it.id }.toSet(),
        )
        assertNotNull(selection)
        assertTrue(selection!!.card.id in speciesCards.map { it.id })
    }

    @Test
    fun emptyCandidatesReturnNull() {
        assertNull(QuestionScheduler.schedule(emptyList(), QuestionKind.SPECIES, seed = 1L))
        assertNull(QuestionScheduler.select(emptyList(), QuestionKind.GENERIC, seed = 1L))
    }

    @Test
    fun speciesAndGenericPoolsAreFilteredByKind() {
        val candidates = listOf(
            questionCard("sp.a", speciesKey = "a"),
            questionCard("gen.6", askerTypes = listOf(6)),
        )
        repeat(20) { index ->
            val seed = index.toLong()
            assertEquals("sp.a", QuestionScheduler.select(candidates, QuestionKind.SPECIES, seed)?.id)
            assertEquals("gen.6", QuestionScheduler.select(candidates, QuestionKind.GENERIC, seed)?.id)
        }
    }

    @Test
    fun mixedPoolFallsBackToAllCandidatesWhenNothingMatchesTheKind() {
        val genericOnly = listOf(questionCard("gen.6", askerTypes = listOf(6)))
        val selection = QuestionScheduler.schedule(genericOnly, QuestionKind.SPECIES, seed = 3L)
        assertEquals("gen.6", selection?.card?.id)
    }

    @Test
    fun arrangeHandlesSingleOptionCards() {
        val card = questionCard("sp.one", options = listOf(answerOption("a")))
        val selection = QuestionScheduler.arrange(card, seed = 9L)
        assertEquals(listOf("a"), selection.optionOrder)
        assertEquals(1, selection.orderedOptions.size)
    }

    @Test
    fun unknownOptionOrderIdsAreIgnoredAndMissingOptionsAppended() {
        val card = questionCard(
            "sp.one",
            options = listOf(answerOption("a"), answerOption("b"), answerOption("c")),
        )
        val selection = com.tamerslab.digiline.model.QuestionSelection(
            card = card,
            optionOrder = listOf("c", "missing", "a"),
            seed = 1L,
        )
        assertEquals(listOf("c", "a", "b"), selection.orderedOptions.map { it.id })
        assertNotNull(selection.option("b"))
    }
}
