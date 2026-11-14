package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.QuizCardDto
import com.tamerslab.digiline.model.QuizOutcome

/**
 * Deterministic quiz resolution (plan §6.5, contract §3.1).
 *
 * Bond is the existing `mood` stat (decision D2): a correct answer adds +3, a wrong (or
 * out-of-range) answer subtracts 5. The engine only returns the delta; the repository applies it
 * and floors the stat at 0 (decision D8) — use [moodAfter] for that.
 */
object QuizEngine {
    const val CORRECT_MOOD_DELTA = QuizOutcome.CORRECT_MOOD_DELTA
    const val WRONG_MOOD_DELTA = QuizOutcome.WRONG_MOOD_DELTA

    /** Resolves one answer. Never throws, including for empty option lists or invalid indexes. */
    fun resolve(quiz: QuizCardDto, chosenIndex: Int): QuizOutcome {
        val validAnswer = quiz.hasValidAnswer
        val correct = validAnswer && chosenIndex == quiz.correctIndex
        return QuizOutcome(
            quizId = quiz.id,
            correct = correct,
            moodDelta = if (correct) CORRECT_MOOD_DELTA else WRONG_MOOD_DELTA,
            chosenIndex = chosenIndex,
            correctIndex = if (validAnswer) quiz.correctIndex else -1,
            explanation = quiz.explanation,
        )
    }

    /** Null-safe variant for content that may be absent. */
    fun resolveOrNull(quiz: QuizCardDto?, chosenIndex: Int): QuizOutcome? {
        if (quiz == null) return null
        val validAnswer = quiz.hasValidAnswer
        val correct = validAnswer && chosenIndex == quiz.correctIndex
        return QuizOutcome(
            quizId = quiz.id,
            correct = correct,
            moodDelta = if (correct) CORRECT_MOOD_DELTA else WRONG_MOOD_DELTA,
            chosenIndex = chosenIndex,
            correctIndex = if (validAnswer) quiz.correctIndex else -1,
            explanation = quiz.explanation,
        )
    }

    /** Applies the quiz delta to the bond stat with the 0 floor (repository-side persistence). */
    fun moodAfter(currentMood: Int, outcome: QuizOutcome): Int = (currentMood + outcome.moodDelta).coerceAtLeast(0)
}
