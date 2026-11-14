package com.tamerslab.digiline.gateway

import com.tamerslab.domain.chat.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

/** One authored answer/choice button rendered under a [DigilineScene]. */
data class DigilineOptionView(
    val id: String,
    val text: String,
)

/**
 * Render-ready DigiLine scene (contract §4). The gateway translates engine/repository state into
 * this Android-free shape; UI layers only render it.
 *
 * @property kind scene type, drives the card layout
 * @property lines narration/dialogue rows, in order
 * @property options authored answer buttons; empty for dismissable narration-only scenes
 * @property canDismiss whether "Not now"/close is allowed (negotiation leave path is authored)
 * @property syncLabel native personality sync state label (D4/R3), when relevant
 */
data class DigilineScene(
    val kind: Kind,
    val lines: List<String> = emptyList(),
    /**
     * Authored reaction to the player's previous choice. Rendered between the framing sentence and
     * the prompt so a negotiation reads as a back-and-forth instead of unrelated questions.
     */
    val reactionLine: String? = null,
    val options: List<DigilineOptionView> = emptyList(),
    val canDismiss: Boolean = true,
    val syncLabel: String? = null,
    /** Bits demanded by the current negotiation node, shown as a UI label when present. */
    val demandBits: Long? = null,
    /**
     * Localization key for the scene's framing sentence, e.g. `wild_new`, `wild_resume`,
     * `persuasion_new`, `owned_question`. Keeps content/internals out of the UI while explaining
     * every entry/resume in one short line instead of a hard cut.
     */
    val contextKey: String? = null,
    /** Terminal outcome key for reaction scenes: JOIN, REFUSE, LEAVE, FIGHT, PAY_AND_LEAVE, DEPART, QUEST_OFFER, NEXT_STAGE, QUIZ_CORRECT, QUIZ_WRONG. */
    val outcomeKey: String? = null,
) {
    enum class Kind { QUESTION, QUIZ, NEGOTIATION, PERSUASION, REACTION, IDLE }
}

/** Result of a gateway action; the UI renders scenes, closes, or surfaces an error message. */
sealed interface DigilineResult {
    data class Scene(val scene: DigilineScene) : DigilineResult
    data class Closed(val message: String? = null) : DigilineResult
    data class Error(val message: String) : DigilineResult
}

/**
 * Deterministic DigiLine façade used by chat/wild screens (contract §4).
 *
 * The default implementation (Lead-owned `ScriptedDigilineGateway`) routes owned individuals to
 * the owned-question/quiz engines and wild contacts to the negotiation/persuasion engines; the
 * legacy LLM path is a separate debug-only implementation and never flows through this default.
 */
interface DigilineGateway {
    /** Ordered transcript for [individualId] (chosen options are `user` rows, authored lines are `assistant`). */
    fun observeHistory(individualId: String): Flow<List<ChatMessageEntity>>

    /** Current scene for [individualId]; `null` until a scene is loaded/resumed. */
    fun observeScene(individualId: String): Flow<DigilineScene?>

    /** Starts or resumes the next scene for an owned ([wild] = false) or wild ([wild] = true) individual. */
    suspend fun ensureScene(individualId: String, wild: Boolean): DigilineResult

    /** Applies the choice with [optionId] to the active scene. */
    suspend fun choose(individualId: String, optionId: String): DigilineResult

    /** Closes the active scene without choosing (cooldowns still apply; never deletes a contact). */
    suspend fun dismiss(individualId: String): DigilineResult
}
