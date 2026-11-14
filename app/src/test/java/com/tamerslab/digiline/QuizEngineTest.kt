package com.tamerslab.digiline

import com.tamerslab.digiline.engine.QuizEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizEngineTest {
    @Test
    fun correctAnswerRaisesBondByThree() {
        val quiz = quizCard("quiz.1", correctIndex = 1)
        val outcome = QuizEngine.resolve(quiz, chosenIndex = 1)
        assertTrue(outcome.correct)
        assertEquals(3, outcome.moodDelta)
        assertEquals(1, outcome.correctIndex)
        assertEquals(quiz.id, outcome.quizId)
        assertEquals(quiz.explanation, outcome.explanation)
    }

    @Test
    fun wrongAnswerDropsBondByFive() {
        val quiz = quizCard("quiz.1", correctIndex = 1)
        val outcome = QuizEngine.resolve(quiz, chosenIndex = 0)
        assertFalse(outcome.correct)
        assertEquals(-5, outcome.moodDelta)
    }

    @Test
    fun outOfRangeAnswerCountsAsWrongAndNeverThrows() {
        val quiz = quizCard("quiz.1")
        val tooHigh = QuizEngine.resolve(quiz, chosenIndex = 99)
        assertFalse(tooHigh.correct)
        assertEquals(-5, tooHigh.moodDelta)

        val negative = QuizEngine.resolve(quiz, chosenIndex = -1)
        assertFalse(negative.correct)
        assertEquals(-5, negative.moodDelta)
    }

    @Test
    fun emptyOrInvalidQuizContentIsSafe() {
        val emptyOptions = quizCard("quiz.empty", options = emptyList(), correctIndex = 0)
        val outcome = QuizEngine.resolve(emptyOptions, chosenIndex = 0)
        assertFalse(outcome.correct)
        assertEquals(-5, outcome.moodDelta)
        assertEquals(-1, outcome.correctIndex)

        assertNull(QuizEngine.resolveOrNull(null, 0))
    }

    @Test
    fun moodFloorIsAppliedByTheHelper() {
        val wrong = QuizEngine.resolve(quizCard("quiz.1"), chosenIndex = 2)
        assertEquals(0, QuizEngine.moodAfter(0, wrong))
        assertEquals(0, QuizEngine.moodAfter(3, wrong))
        val correct = QuizEngine.resolve(quizCard("quiz.1"), chosenIndex = 0)
        assertEquals(53, QuizEngine.moodAfter(50, correct))
    }
}
