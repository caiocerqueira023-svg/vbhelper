package com.tamerslab.digiline

import com.tamerslab.digiline.engine.OwnedDigilineEngine
import com.tamerslab.digiline.engine.ReactionLineProvider
import com.tamerslab.digiline.model.OwnedDigilineInput
import com.tamerslab.digiline.model.OwnedDigilineState
import com.tamerslab.digiline.model.QuestionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedDigilineEngineTest {
    private val engine = OwnedDigilineEngine()

    @Test
    fun emptyContentAlwaysPlansIdle() {
        val state = engine.plan(testPack(), OwnedDigilineInput(individualId = "ind-1"), now = 0L)
        assertEquals(OwnedDigilineState.Idle, state)
    }

    @Test
    fun speciesQuestionHasPriorityAndIsDeterministic() {
        val pack = testPack(
            questions = listOf(
                questionCard(
                    "sp.aegiomon.1",
                    speciesKey = "aegiomon",
                    options = listOf(answerOption("a"), answerOption("b"), answerOption("c")),
                ),
            ),
            genericQuestions = listOf(questionCard("gen.6", askerTypes = listOf(6))),
            quizzes = listOf(quizCard("quiz.1")),
        )
        val input = OwnedDigilineInput(
            individualId = "ind-1",
            personalityType = 6,
            stageTier = 2,
            speciesKey = "aegiomon",
            dayIndex = 1,
            sessionCounter = 2,
            contentVersion = 1,
        )
        val state = engine.plan(pack, input, now = 1000L)
        assertTrue(state is OwnedDigilineState.Question)
        val question = state as OwnedDigilineState.Question
        assertEquals("sp.aegiomon.1", question.selection.card.id)
        assertEquals(QuestionKind.SPECIES, question.kind)
        assertEquals(1000L, question.askedAt)
        assertEquals(state, engine.plan(pack, input, now = 1000L))
    }

    @Test
    fun quizIsPlannedWhenNoSpeciesQuestionIsAvailable() {
        val pack = testPack(quizzes = listOf(quizCard("quiz.1", stageTier = 2)))
        val input = OwnedDigilineInput(
            individualId = "ind-2",
            personalityType = 6,
            stageTier = 2,
            allowSpeciesQuestion = false,
        )
        val state = engine.plan(pack, input, now = 5L)
        assertTrue(state is OwnedDigilineState.Quiz)
        assertEquals("quiz.1", (state as OwnedDigilineState.Quiz).quiz.id)
    }

    @Test
    fun genericQuestionsRespectThePersonalityGate() {
        val pack = testPack(genericQuestions = listOf(questionCard("gen.6", askerTypes = listOf(6))))
        val matching = engine.plan(
            pack,
            OwnedDigilineInput("ind-1", personalityType = 6, allowSpeciesQuestion = false),
            now = 0L,
        )
        assertTrue(matching is OwnedDigilineState.Question)
        assertEquals(QuestionKind.GENERIC, (matching as OwnedDigilineState.Question).kind)

        val nonMatching = engine.plan(
            pack,
            OwnedDigilineInput("ind-1", personalityType = 7, allowSpeciesQuestion = false),
            now = 0L,
        )
        assertEquals(OwnedDigilineState.Idle, nonMatching)
    }

    @Test
    fun aRecentlyAskedSpeciesQuestionFallsBackToTheGenericPool() {
        val pack = testPack(
            questions = listOf(questionCard("sp.only", speciesKey = "aegiomon")),
            genericQuestions = listOf(questionCard("gen.6", askerTypes = listOf(6))),
        )
        val input = OwnedDigilineInput(
            individualId = "ind-1",
            personalityType = 6,
            speciesKey = "aegiomon",
            recentQuestionIds = setOf("sp.only"),
        )
        val state = engine.plan(pack, input, now = 0L)
        assertTrue(state is OwnedDigilineState.Question)
        assertEquals("gen.6", (state as OwnedDigilineState.Question).selection.card.id)
    }

    @Test
    fun answeringAQuestionProducesReactionMoodAndShiftSnapshot() {
        val pack = testPack(
            questions = listOf(
                questionCard(
                    "sp.one",
                    speciesKey = "aegiomon",
                    options = listOf(
                        answerOption("a", reaction = "Nice.", moodDelta = 1, shift = mapOf("7" to 14)),
                        answerOption("b"),
                    ),
                ),
            ),
        )
        val input = OwnedDigilineInput(individualId = "ind-1", personalityType = 6, speciesKey = "aegiomon")
        val state = engine.plan(pack, input, now = 10L)
        assertTrue(state is OwnedDigilineState.Question)
        val answer = engine.answerQuestion(
            state = state as OwnedDigilineState.Question,
            optionId = "a",
            tendencyPoints = emptyMap(),
            currentType = 6,
            now = 100L,
            lastShiftAt = null,
            answeredSinceShift = 1,
        )
        assertTrue(answer.accepted)
        assertEquals("Nice.", answer.reaction)
        assertEquals(1, answer.moodDelta)
        assertNotNull(answer.shift)
        assertTrue(answer.shift!!.shifted)
        assertEquals(7, answer.shift!!.newType)
        val answered = answer.nextState as OwnedDigilineState.Answered
        assertEquals("sp.one", answered.questionId)
        assertEquals("a", answered.optionId)
        assertEquals(100L, answered.finishedAt)
    }

    @Test
    fun unknownAnswerOptionsLeaveThePendingQuestionUntouched() {
        val pack = testPack(questions = listOf(questionCard("sp.one", speciesKey = "aegiomon")))
        val state = engine.plan(
            pack,
            OwnedDigilineInput("ind-1", personalityType = 6, speciesKey = "aegiomon"),
            now = 0L,
        ) as OwnedDigilineState.Question
        val answer = engine.answerQuestion(state, "nope")
        assertFalse(answer.accepted)
        assertEquals(state, answer.nextState)
        assertTrue(answer.message?.contains("unknown answer option") == true)
    }

    @Test
    fun quizAnswersUseTheContractDeltas() {
        val quiz = quizCard("quiz.1", correctIndex = 1)
        val state = OwnedDigilineState.Quiz(quiz = quiz, askedAt = 0L)
        val correct = engine.answerQuiz(state, chosenIndex = 1)
        assertEquals(3, correct.outcome?.moodDelta)
        assertTrue(correct.outcome?.correct == true)
        assertTrue(correct.nextState is OwnedDigilineState.QuizAnswered)
        assertEquals("quiz.1", (correct.nextState as OwnedDigilineState.QuizAnswered).quizId)

        val wrong = engine.answerQuiz(state, chosenIndex = 0)
        assertEquals(-5, wrong.outcome?.moodDelta)
        assertFalse(wrong.outcome?.correct == true)
    }

    @Test
    fun dismissReturnsToIdle() {
        val quizState = OwnedDigilineState.Quiz(quiz = quizCard("quiz.1"), askedAt = 0L)
        assertEquals(OwnedDigilineState.Idle, engine.dismiss(quizState))
        assertEquals(OwnedDigilineState.Idle, engine.idle())
    }

    @Test
    fun reactionScenesWrapAuthoredLines() {
        val pick = ReactionLineProvider(
            listOf(eventLine("ev.1", "battle_win", text = "We won.", moodDelta = 1)),
        ).pick("battle_win", 6, seed = 1L)
        val reaction = engine.reactionState("battle_win", pick)
        assertTrue(reaction is OwnedDigilineState.Reaction)
        val scene = reaction as OwnedDigilineState.Reaction
        assertEquals("battle_win", scene.event)
        assertEquals("We won.", scene.text)
        assertEquals(1, scene.moodDelta)
        assertEquals(OwnedDigilineState.Idle, engine.reactionState("battle_win", null))
    }

    @Test
    fun questionHelpersExposeOrderedOptionsAndQuizContent() {
        val pack = testPack(
            questions = listOf(
                questionCard(
                    "sp.one",
                    speciesKey = "aegiomon",
                    options = listOf(answerOption("a"), answerOption("b"), answerOption("c")),
                ),
            ),
            quizzes = listOf(quizCard("quiz.1")),
        )
        val questionState = engine.plan(
            pack,
            OwnedDigilineInput("ind-1", personalityType = 6, speciesKey = "aegiomon"),
            now = 0L,
        )
        val options = engine.questionOptionsOf(questionState)
        assertNotNull(options)
        assertEquals(setOf("a", "b", "c"), options!!.map { it.id }.toSet())
        assertNull(engine.quizOf(questionState))

        val quizState = engine.plan(
            testPack(quizzes = listOf(quizCard("quiz.1"))),
            OwnedDigilineInput("ind-1", personalityType = 6, allowSpeciesQuestion = false),
            now = 0L,
        )
        assertEquals("quiz.1", engine.quizOf(quizState)?.id)
        assertNull(engine.questionOptionsOf(quizState))
    }
}
