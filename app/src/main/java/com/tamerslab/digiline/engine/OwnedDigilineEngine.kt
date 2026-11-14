package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.AnswerOptionDto
import com.tamerslab.digiline.model.DigilineContentPack
import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.OwnedDigilineAnswer
import com.tamerslab.digiline.model.OwnedDigilineInput
import com.tamerslab.digiline.model.OwnedDigilineQuizAnswer
import com.tamerslab.digiline.model.OwnedDigilineState
import com.tamerslab.digiline.model.QuestionCardDto
import com.tamerslab.digiline.model.QuestionKind
import com.tamerslab.digiline.model.QuizCardDto
import com.tamerslab.digiline.model.ReactionPick

/**
 * Pure state machine for an owned Digimon's DigiLine scenes (contract §3.1, plan §6.1/§6.2).
 *
 * Flow: `Idle -> Question | Quiz -> Answered | QuizAnswered -> Idle` with an optional
 * `Reaction` scene for authored events. The engine decides *what* comes next and what an answer
 * means; the repository persists the selection before display, applies mood/shift changes and
 * writes the transcript. No Android/Room/DI dependencies.
 */
class OwnedDigilineEngine(
    private val shiftEngine: PersonalityShiftEngine = PersonalityShiftEngine,
) {
    /**
     * Decides the next scene for an owned Digimon.
     *
     * Priority (plan §6.1): species question -> quiz (if due) -> generic question for the current
     * personality -> [OwnedDigilineState.Idle] (authored fallback reaction is composed by the
     * caller through `ReactionLineProvider`). Empty/missing content always yields `Idle`.
     */
    fun plan(pack: DigilineContentPack, input: OwnedDigilineInput, now: Long): OwnedDigilineState {
        if (!pack.hasContent) return OwnedDigilineState.Idle
        val seed = input.seed()

        if (input.allowSpeciesQuestion) {
            speciesQuestion(pack, input)?.let { card ->
                return questionState(card, seed, QuestionKind.SPECIES, now)
            }
        }

        if (input.allowQuiz && input.quizDue) {
            val quiz = pack.quiz(input.normalizedTier, DigilineSeed.selectionSeed(seed, "quiz"), input.recentQuizIds)
            if (quiz != null) return OwnedDigilineState.Quiz(quiz, now)
        }

        if (input.allowGenericQuestion) {
            val generic = pack.genericQuestion(
                typeNumber = input.normalizedType,
                seed = DigilineSeed.selectionSeed(seed, "generic", input.normalizedType),
                excludedIds = input.recentQuestionIds,
            )
            if (generic != null) return questionState(generic, seed, QuestionKind.GENERIC, now)
        }

        return OwnedDigilineState.Idle
    }

    /**
     * Applies one chosen answer option of a pending question.
     *
     * Always returns a shift snapshot (even for options without a shift vector) because the answer
     * counter and decay rules depend on every answered question. On an unknown option id the state
     * is returned unchanged with `accepted = false`.
     */
    fun answerQuestion(
        state: OwnedDigilineState.Question,
        optionId: String,
        tendencyPoints: Map<Int, Int> = emptyMap(),
        currentType: Int = 1,
        now: Long = 0L,
        lastShiftAt: Long? = null,
        answeredSinceShift: Int = 0,
        lastDecayAt: Long? = null,
    ): OwnedDigilineAnswer {
        val option = state.selection.card.options.firstOrNull { it.id == optionId }
        if (option == null) {
            return OwnedDigilineAnswer(
                accepted = false,
                option = null,
                reaction = "",
                moodDelta = 0,
                shift = null,
                nextState = state,
                message = "unknown answer option: $optionId",
            )
        }
        val shift = shiftEngine.apply(
            points = tendencyPoints,
            currentType = currentType,
            optionShift = option.shift,
            now = now,
            lastShiftAt = lastShiftAt,
            answeredSinceShift = answeredSinceShift,
            lastDecayAt = lastDecayAt,
        )
        val nextState = OwnedDigilineState.Answered(
            questionId = state.selection.card.id,
            optionId = option.id,
            reaction = option.reaction,
            moodDelta = option.moodDelta,
            shift = shift,
            finishedAt = now,
        )
        return OwnedDigilineAnswer(
            accepted = true,
            option = option,
            reaction = option.reaction,
            moodDelta = option.moodDelta,
            shift = shift,
            nextState = nextState,
        )
    }

    /** Applies one quiz answer. Mood floor is applied by the repository ([QuizEngine.moodAfter]). */
    fun answerQuiz(state: OwnedDigilineState.Quiz, chosenIndex: Int, now: Long = 0L): OwnedDigilineQuizAnswer {
        val outcome = QuizEngine.resolve(state.quiz, chosenIndex)
        val nextState = OwnedDigilineState.QuizAnswered(
            quizId = state.quiz.id,
            outcome = outcome,
            finishedAt = now,
        )
        return OwnedDigilineQuizAnswer(accepted = true, outcome = outcome, nextState = nextState)
    }

    /** Dismissing any scene returns to [OwnedDigilineState.Idle] (repository records the cooldown). */
    fun dismiss(state: OwnedDigilineState): OwnedDigilineState = OwnedDigilineState.Idle

    /** Wraps a picked authored event line into a Reaction scene (reaction cards in chat). */
    fun reactionState(event: String, pick: ReactionPick?): OwnedDigilineState {
        if (pick == null) return OwnedDigilineState.Idle
        return OwnedDigilineState.Reaction(event = event, text = pick.text, moodDelta = pick.moodDelta)
    }

    /** Explicit idle scene (used when no scheduler trigger is eligible). */
    fun idle(): OwnedDigilineState = OwnedDigilineState.Idle

    /**
     * Builds the display state for a card: option order is frozen here (persist it before showing,
     * anti-reroll).
     */
    fun questionState(card: QuestionCardDto, seed: Long, kind: QuestionKind, now: Long): OwnedDigilineState.Question {
        val selection = QuestionScheduler.arrange(
            card,
            DigilineSeed.selectionSeed(seed, "options", DigilineSeed.stableHash(card.id).toInt()),
        )
        return OwnedDigilineState.Question(selection = selection, askedAt = now, kind = kind)
    }

    private fun speciesQuestion(pack: DigilineContentPack, input: OwnedDigilineInput): QuestionCardDto? {
        val speciesKey = input.speciesKey?.takeIf { it.isNotBlank() } ?: return null
        val seen = mutableSetOf<String>()
        for (offset in 0 until SPECIES_VARIANT_ATTEMPTS) {
            val card = pack.speciesQuestion(speciesKey, input.speciesVariant + offset) ?: return null
            if (!seen.add(card.id)) return null
            if (card.id !in input.recentQuestionIds) return card
        }
        return null
    }

    /**
     * Convenience overload for the pending state without leaking model subtypes into callers.
     * Returns `null` for scenes that are not quizzes.
     */
    fun quizOf(state: OwnedDigilineState): QuizCardDto? = (state as? OwnedDigilineState.Quiz)?.quiz

    /** Current option objects in display order for a pending question (`null` for other states). */
    fun questionOptionsOf(state: OwnedDigilineState): List<AnswerOptionDto>? =
        (state as? OwnedDigilineState.Question)?.selection?.orderedOptions
}

/** Species variant lines are authored as a small bank per species; try a few before giving up. */
private const val SPECIES_VARIANT_ATTEMPTS = 4
