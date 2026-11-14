package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.PersuasionOutcome
import com.tamerslab.digiline.model.PersuasionState
import com.tamerslab.digiline.model.PersuasionStep

/**
 * Deterministic post-join wild persuasion engine (plan §7.7, contract §3.1).
 *
 * A wild contact that joined through negotiation is convinced to offer its recruitment quest over
 * an authored arc of conversations. Content is keyed by personality type and stage `0..3`; each
 * stage flow is a small authored node graph. Terminal outcomes are `NEXT_STAGE`, `QUEST_OFFER`,
 * `REFUSE` and `DEPART` — `QUEST_OFFER` is the only arc-completing outcome.
 *
 * The engine is pure: it applies authored `persuasionFlag` effects and the stage advance in the
 * returned state; the repository persists stage/flags/outcome (`WildPersuasionEntity`). It never
 * touches owned-Digimon mood and never throws for missing content ([start] returns `null`).
 */
object PersuasionEngine {
    const val MIN_STAGE = 0
    const val MAX_STAGE = 3

    /**
     * Starts (or resumes) one persuasion conversation at [stage].
     *
     * Returns `null` for `null`/malformed content (missing start node), so callers can close the
     * scene instead of crashing.
     */
    fun start(
        flow: NegotiationFlowDto?,
        stage: Int,
        seed: Long = 0,
        personalityType: Int = 0,
        flags: Set<String> = emptySet(),
        visitedNodeIds: Set<String> = emptySet(),
    ): PersuasionState? {
        if (flow == null) return null
        if (flow.start.isBlank() || flow.nodes[flow.start] == null) return null
        val normalizedStage = stage.coerceIn(MIN_STAGE, MAX_STAGE)
        val startNode = flow.nodes[flow.start] ?: return null
        val outcome = supportedOutcome(startNode.outcome)
        return PersuasionState(
            flow = flow,
            stage = normalizedStage,
            nodeId = flow.start,
            seed = seed,
            personalityType = personalityType,
            flags = flags,
            visitedNodeIds = visitedNodeIds.toList(),
            finished = outcome != null,
            outcome = outcome,
        )
    }

    /** Applies one option choice of the current stage conversation. */
    fun choose(state: PersuasionState, optionId: String): PersuasionStep {
        if (state.finished) {
            return PersuasionStep(state = state, accepted = false, message = "persuasion conversation is already finished")
        }
        val previousNode = state.node
            ?: return PersuasionStep(
                state = state.copy(finished = true, outcome = PersuasionOutcome.REFUSE),
                accepted = false,
                message = "current persuasion node is missing",
            )
        val option = previousNode.options.firstOrNull { it.id == optionId }
            ?: return PersuasionStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                message = "unknown option: $optionId",
            )
        val requiredFlag = option.requires?.trim()?.takeIf { it.isNotEmpty() }
        if (requiredFlag != null && !state.flags.contains(requiredFlag)) {
            return PersuasionStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "requirement not met: $requiredFlag",
            )
        }
        val requestedNext = option.next?.trim()?.takeIf { it.isNotEmpty() }
        val nextNode = requestedNext?.let { state.flow.nodes[it] }
        if (requestedNext != null && nextNode == null) {
            return PersuasionStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "unknown next node: $requestedNext",
            )
        }

        val targetNode = nextNode ?: previousNode
        val targetNodeId = requestedNext ?: state.nodeId
        val authoredOutcome = if (nextNode != null) nextNode.outcome else previousNode.outcome
        var message: String? = null
        var outcome = supportedOutcome(authoredOutcome)
        if (!authoredOutcome.isNullOrBlank() && outcome == null) {
            outcome = PersuasionOutcome.REFUSE
            message = "unsupported persuasion outcome: $authoredOutcome"
        }
        if (outcome == null && nextNode == null) {
            outcome = if (option.effects.depart) PersuasionOutcome.DEPART else PersuasionOutcome.REFUSE
        }

        val flag = option.effects.persuasionFlag?.trim()?.takeIf { it.isNotEmpty() }
        val newFlags = if (flag == null) state.flags else state.flags + flag
        val nextStageValue = if (outcome == PersuasionOutcome.NEXT_STAGE) nextStage(state.stage) else state.stage
        val visited = if (state.nodeId in state.visitedNodeIds) {
            state.visitedNodeIds
        } else {
            state.visitedNodeIds + state.nodeId
        }
        val newState = state.copy(
            flow = state.flow,
            stage = nextStageValue,
            nodeId = targetNodeId,
            seed = state.seed,
            personalityType = state.personalityType,
            flags = newFlags,
            visitedNodeIds = visited,
            finished = outcome != null,
            outcome = outcome,
        )
        return PersuasionStep(
            state = newState,
            accepted = true,
            previousNode = previousNode,
            node = targetNode,
            option = option,
            outcome = outcome,
            nextStage = if (outcome == PersuasionOutcome.NEXT_STAGE) nextStageValue else null,
            questOffer = outcome == PersuasionOutcome.QUEST_OFFER,
            message = message,
        )
    }

    /** Next authored stage after a `NEXT_STAGE` conversation (clamped to [MIN_STAGE]..[MAX_STAGE]). */
    fun nextStage(stage: Int): Int = (stage.coerceIn(MIN_STAGE, MAX_STAGE) + 1).coerceAtMost(MAX_STAGE)

    /** True when the conversation produced the recruitment-quest offer. */
    fun isArcComplete(state: PersuasionState): Boolean = state.outcome == PersuasionOutcome.QUEST_OFFER

    /** True when the outcome ends the conversation (all four outcomes are terminal). */
    fun isTerminal(outcome: PersuasionOutcome?): Boolean = outcome != null

    /**
     * Qualitative Wild-tab label (plan §7.7: never show a numeric trust value).
     * `questOfferPending` maps the persisted `PENDING` quest-offer state.
     */
    fun progressLabel(stage: Int, questOfferPending: Boolean = false): String {
        if (questOfferPending) return "wants to travel with you"
        return when (stage.coerceIn(MIN_STAGE, MAX_STAGE)) {
            0 -> "still wary"
            1 -> "warming up"
            2 -> "opening up"
            else -> "ready to travel"
        }
    }

    /** Negotiation options currently selectable (flags only; persuasion never handles payments). */
    fun availableOptions(state: PersuasionState): List<NegotiationOptionDto> {
        if (state.finished) return emptyList()
        val node = state.node ?: return emptyList()
        return node.options.filter { option ->
            val requiredFlag = option.requires?.trim()?.takeIf { it.isNotEmpty() }
            requiredFlag == null || state.flags.contains(requiredFlag)
        }
    }

    private fun supportedOutcome(raw: String?): PersuasionOutcome? =
        PersuasionOutcome.fromWire(raw)?.takeIf { it in SUPPORTED_OUTCOMES }

    private val SUPPORTED_OUTCOMES = setOf(
        PersuasionOutcome.NEXT_STAGE,
        PersuasionOutcome.QUEST_OFFER,
        PersuasionOutcome.REFUSE,
        PersuasionOutcome.DEPART,
    )
}
