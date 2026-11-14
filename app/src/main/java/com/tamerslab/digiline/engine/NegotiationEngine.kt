package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DemandDto
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationMove
import com.tamerslab.digiline.model.NegotiationNodeDto
import com.tamerslab.digiline.model.NegotiationOptionDto
import com.tamerslab.digiline.model.NegotiationOutcome
import com.tamerslab.digiline.model.NegotiationPhase
import com.tamerslab.digiline.model.NegotiationState
import com.tamerslab.digiline.model.NegotiationStep

/**
 * Deterministic wild-negotiation engine (plan §7, contract §3.1).
 *
 * The engine is a pure state machine: [start] creates a session state from an authored flow,
 * [choose] validates one option, applies its effects **atomically in the returned state** (no I/O,
 * no DB), records the move and maps the authored node outcome to [NegotiationOutcome].
 *
 * Safety rules:
 * - unknown option ids, unmet `requires` flags, unknown `next` nodes and unpayable options are
 *   rejected without mutating the state or consuming anything;
 * - payments are checked against wallet (when supplied) and the per-session loss caps from
 *   [DemandResolver]; an item stack's last unit is never consumed;
 * - `DEPART` is only reachable through an authored `DEPART` node (or an explicit `depart` effect)
 *   and is downgraded to `REFUSE` on a contact's first conversation or when it was already used
 *   (decision R6: max once per contact, never first conversation);
 * - a `null`/malformed flow never throws: [startOrNull] returns `null`, [start] returns a finished
 *   state so callers simply close the scene.
 */
object NegotiationEngine {
    /**
     * Starts a negotiation session from a parsed flow.
     *
     * A malformed flow (missing start node) returns a finished [NegotiationOutcome.LEAVE] state
     * instead of throwing; prefer [startOrNull] when the caller wants to distinguish that case.
     */
    fun start(
        flow: NegotiationFlowDto,
        seed: Long,
        tier: Int,
        personalityType: Int = 0,
        firstConversation: Boolean = false,
        departUsed: Boolean = false,
    ): NegotiationState {
        val startNode = flow.nodes[flow.start]
        if (startNode == null) {
            return NegotiationState(
                flow = flow,
                nodeId = flow.start,
                tier = tier,
                seed = seed,
                personalityType = personalityType,
                firstConversation = firstConversation,
                departUsed = departUsed,
                finished = true,
                outcome = NegotiationOutcome.LEAVE,
            )
        }
        val requested = NegotiationOutcome.fromWire(startNode.outcome)
        val outcome = applyDepartGuard(requested, firstConversation, departUsed)
        return NegotiationState(
            flow = flow,
            nodeId = flow.start,
            tier = tier,
            seed = seed,
            personalityType = personalityType,
            firstConversation = firstConversation,
            departUsed = departUsed,
            finished = outcome != null,
            outcome = outcome,
        )
    }

    /** Null-safe [start] for absent or malformed content. */
    fun startOrNull(
        flow: NegotiationFlowDto?,
        seed: Long,
        tier: Int,
        personalityType: Int = 0,
        firstConversation: Boolean = false,
        departUsed: Boolean = false,
    ): NegotiationState? {
        if (flow == null) return null
        if (flow.start.isBlank() || flow.nodes[flow.start] == null) return null
        return start(flow, seed, tier, personalityType, firstConversation, departUsed)
    }

    /**
     * Applies one option choice.
     *
     * @param playerBits current player bits, or `null` when the caller cannot observe them yet
     *   (session caps and authored validity are still enforced).
     * @param playerItems current item stacks, or `null` when unobservable. Passing a stack map
     *   enforces the "never consume the last item" rule.
     */
    fun choose(
        state: NegotiationState,
        optionId: String,
        playerBits: Long? = null,
        playerItems: Map<String, Int>? = null,
    ): NegotiationStep {
        if (state.finished) {
            return NegotiationStep(state = state, accepted = false, message = "negotiation is already finished")
        }
        val previousNode = state.node
            ?: return NegotiationStep(
                state = state.copy(finished = true, outcome = NegotiationOutcome.LEAVE),
                accepted = false,
                message = "current negotiation node is missing",
            )
        val option = previousNode.options.firstOrNull { it.id == optionId }
            ?: return NegotiationStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                message = "unknown option: $optionId",
            )

        val requiredFlag = option.requires?.trim()?.takeIf { it.isNotEmpty() }
        if (requiredFlag != null && !state.flags.contains(requiredFlag)) {
            return NegotiationStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "requirement not met: $requiredFlag",
            )
        }
        if (option.effects.depart && state.departUsed) {
            return NegotiationStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "departure already used in this contact",
            )
        }

        val requestedNext = option.next?.trim()?.takeIf { it.isNotEmpty() }
        val nextNode = requestedNext?.let { state.flow.nodes[it] }
        if (requestedNext != null && nextNode == null) {
            return NegotiationStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "unknown next node: $requestedNext",
            )
        }

        val payment = DemandResolver.checkPayment(
            effects = option.effects,
            playerBits = playerBits,
            playerItems = playerItems,
            tier = state.tier,
            bitsPaid = state.bitsPaid,
            itemsPaid = state.itemsPaidTotal,
        )
        if (!payment.affordable) {
            return NegotiationStep(
                state = state,
                accepted = false,
                previousNode = previousNode,
                option = option,
                message = "cannot pay: ${payment.reason.name}",
            )
        }

        val targetNode: NegotiationNodeDto = nextNode ?: previousNode
        val targetNodeId = requestedNext ?: state.nodeId
        val authoredOutcome = if (nextNode != null) nextNode.outcome else previousNode.outcome
        var requestedOutcome = NegotiationOutcome.fromWire(authoredOutcome)
        if (!authoredOutcome.isNullOrBlank() && requestedOutcome == null) {
            // Authored outcome is not one of the supported values: end safely instead of trapping.
            requestedOutcome = NegotiationOutcome.LEAVE
        }
        if (nextNode == null && requestedOutcome == null && option.effects.depart) {
            requestedOutcome = NegotiationOutcome.DEPART
        }
        if (nextNode == null && requestedOutcome == null) {
            // An option without a next node ends the conversation (walk away/refuse branch).
            requestedOutcome = NegotiationOutcome.LEAVE
        }

        var departBlocked = false
        val effectiveOutcome = applyDepartGuard(requestedOutcome, state.firstConversation, state.departUsed)
        if (effectiveOutcome != requestedOutcome) departBlocked = true

        val flag = option.effects.persuasionFlag?.trim()?.takeIf { it.isNotEmpty() }
        val newFlags = if (flag == null) state.flags else state.flags + flag
        val newItemsPaid = if (payment.itemId != null && payment.itemQuantity > 0) {
            val previous = state.itemsPaid[payment.itemId] ?: 0
            state.itemsPaid + (payment.itemId to previous + payment.itemQuantity)
        } else {
            state.itemsPaid
        }
        val move = NegotiationMove(
            nodeId = previousNode.id.ifBlank { state.nodeId },
            optionId = option.id,
            bitsDelta = option.effects.bits.toLong(),
            itemId = payment.itemId?.takeIf { payment.itemQuantity > 0 },
            itemQuantity = payment.itemQuantity,
            persuasionFlag = flag,
            outcome = effectiveOutcome,
        )
        val newState = state.copy(
            nodeId = targetNodeId,
            bitsPaid = state.bitsPaid + payment.bitsCost,
            itemsPaid = newItemsPaid,
            flags = newFlags,
            departUsed = state.departUsed || option.effects.depart || effectiveOutcome == NegotiationOutcome.DEPART,
            finished = effectiveOutcome != null,
            outcome = effectiveOutcome,
            history = state.history + move,
        )
        val itemDelta = if (payment.itemId != null && payment.itemQuantity > 0) {
            mapOf(payment.itemId to -payment.itemQuantity)
        } else {
            emptyMap()
        }
        return NegotiationStep(
            state = newState,
            accepted = true,
            previousNode = previousNode,
            node = targetNode,
            option = option,
            effects = option.effects,
            bitsDelta = option.effects.bits.toLong(),
            itemDelta = itemDelta,
            outcome = effectiveOutcome,
            departBlocked = departBlocked,
        )
    }

    /**
     * Options the player may pick right now: `requires` satisfied, departure rules hold and the
     * payment is affordable. If every payable option is unaffordable, the non-paying exit branch is
     * returned instead (plan §7.4: never soft-lock on an impossible demand).
     */
    fun availableOptions(
        state: NegotiationState,
        playerBits: Long? = null,
        playerItems: Map<String, Int>? = null,
    ): List<NegotiationOptionDto> {
        if (state.finished) return emptyList()
        val node = state.node ?: return emptyList()
        val eligible = node.options.filter { option ->
            val requiredFlag = option.requires?.trim()?.takeIf { it.isNotEmpty() }
            (requiredFlag == null || state.flags.contains(requiredFlag)) &&
                (!(option.effects.depart && state.departUsed))
        }
        val payable = eligible.filter { option ->
            DemandResolver.checkPayment(
                effects = option.effects,
                playerBits = playerBits,
                playerItems = playerItems,
                tier = state.tier,
                bitsPaid = state.bitsPaid,
                itemsPaid = state.itemsPaidTotal,
            ).affordable
        }
        if (payable.isNotEmpty()) return payable
        return eligible.filter { !it.effects.hasPayment }
    }

    /** Coarse phase of the current node (Approach/Probe/Bargain/Test/Resolve). */
    fun phase(state: NegotiationState): NegotiationPhase = state.phase

    /** Authored demand of the current node, if any. */
    fun demand(state: NegotiationState): DemandDto? = state.node?.demand

    /** Current node, if it still exists in the flow. */
    fun currentNode(state: NegotiationState): NegotiationNodeDto? = state.node

    /** Whether the current node is a tell (trick/insight/depart) — UI hint, no rule change. */
    fun isTellNode(state: NegotiationState): Boolean = state.node?.isTell == true

    private fun applyDepartGuard(
        outcome: NegotiationOutcome?,
        firstConversation: Boolean,
        departUsed: Boolean,
    ): NegotiationOutcome? {
        if (outcome != NegotiationOutcome.DEPART) return outcome
        return if (firstConversation || departUsed) NegotiationOutcome.REFUSE else outcome
    }
}
