package com.tamerslab.digiline

import com.tamerslab.digiline.engine.NegotiationEngine
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.NegotiationOutcome
import com.tamerslab.digiline.model.NegotiationPhase
import com.tamerslab.digiline.model.NegotiationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NegotiationEngineTest {
    private fun probeFlow(): NegotiationFlowDto = flow(
        "start",
        node("start", tag = "greeting", options = listOf(option("talk", next = "probe"))),
        node("probe", tag = "probe", options = listOf(option("ask", next = "join"))),
        node("join", tag = "join", outcome = "JOIN"),
    )

    private fun demandFlow(demand: com.tamerslab.digiline.model.DemandDto? = null): NegotiationFlowDto = flow(
        "start",
        node(
            "start",
            tag = "demand",
            demand = demand,
            options = listOf(option("pay", next = "join", effects = effects(bits = -60))),
        ),
        node("join", tag = "join", outcome = "JOIN"),
    )

    @Test
    fun startCreatesAStateAtTheFlowStartNode() {
        val state = NegotiationEngine.start(probeFlow(), seed = 99L, tier = 2)
        assertEquals("start", state.nodeId)
        assertNotNull(state.node)
        assertFalse(state.finished)
        assertNull(state.outcome)
        assertEquals(99L, state.seed)
        assertEquals(NegotiationPhase.APPROACH, state.phase)
    }

    @Test
    fun choosingAnOptionMovesOnAndAppliesTheEffectsAtomically() {
        val state = NegotiationEngine.start(demandFlow(), seed = 5L, tier = 2)
        val step = NegotiationEngine.choose(state, "pay", playerBits = 100L)
        assertTrue(step.accepted)
        assertEquals(-60L, step.bitsDelta)
        assertEquals(60L, step.state.bitsPaid)
        assertEquals("join", step.state.nodeId)
        assertEquals(NegotiationOutcome.JOIN, step.outcome)
        assertTrue(step.state.finished)
        assertEquals(1, step.state.history.size)
        assertEquals("pay", step.state.history.first().optionId)
        assertEquals(NegotiationOutcome.JOIN, step.state.history.first().outcome)
    }

    @Test
    fun unknownOptionsAreRejectedWithoutMutatingTheState() {
        val state = NegotiationEngine.start(probeFlow(), seed = 1L, tier = 2)
        val step = NegotiationEngine.choose(state, "nope")
        assertFalse(step.accepted)
        assertEquals(state, step.state)
        assertNotNull(step.message)
    }

    @Test
    fun unaffordablePaymentsAreRejectedWithoutConsumingAnything() {
        val state = NegotiationEngine.start(demandFlow(), seed = 1L, tier = 2)
        val step = NegotiationEngine.choose(state, "pay", playerBits = 10L)
        assertFalse(step.accepted)
        assertEquals(0L, step.state.bitsPaid)
        assertTrue(step.message?.contains("INSUFFICIENT_BITS") == true)
        assertEquals(state, step.state)
    }

    @Test
    fun itemPaymentsNeverConsumeTheLastStackUnit() {
        val itemFlow = flow(
            "start",
            node(
                "start",
                options = listOf(
                    option(
                        "pay",
                        next = "join",
                        effects = effects(itemId = "potion", itemQuantity = 1),
                    ),
                ),
            ),
            node("join", outcome = "JOIN"),
        )
        val state = NegotiationEngine.start(itemFlow, seed = 2L, tier = 2)
        val lastItem = NegotiationEngine.choose(state, "pay", playerItems = mapOf("potion" to 1))
        assertFalse(lastItem.accepted)
        assertTrue(lastItem.message?.contains("LAST_ITEM_PROTECTED") == true)

        val twoItems = NegotiationEngine.choose(state, "pay", playerItems = mapOf("potion" to 2))
        assertTrue(twoItems.accepted)
        assertEquals(mapOf("potion" to 1), twoItems.state.itemsPaid)
        assertEquals(mapOf("potion" to -1), twoItems.itemDelta)
    }

    @Test
    fun perSessionLossCapsStopStackedPayments() {
        val capFlow = flow(
            "start",
            node("start", options = listOf(option("pay1", next = "second", effects = effects(bits = -200)))),
            node("second", options = listOf(option("pay2", next = "join", effects = effects(bits = -200)))),
            node("join", outcome = "JOIN"),
        )
        val state = NegotiationEngine.start(capFlow, seed = 3L, tier = 1)
        val firstPayment = NegotiationEngine.choose(state, "pay1", playerBits = 10_000L)
        assertTrue(firstPayment.accepted)
        assertEquals(200L, firstPayment.state.bitsPaid)

        val secondPayment = NegotiationEngine.choose(firstPayment.state, "pay2", playerBits = 10_000L)
        assertFalse(secondPayment.accepted)
        assertTrue(secondPayment.message?.contains("SESSION_CAP") == true)
        assertEquals(200L, secondPayment.state.bitsPaid)
    }

    @Test
    fun requiresFlagsGateOptions() {
        val requiredFlow = flow(
            "start",
            node(
                "start",
                options = listOf(
                    option("look", next = "probe", effects = effects(persuasionFlag = "insight")),
                    option("reveal", next = "end", requires = "insight"),
                    option("skip", next = "end"),
                ),
            ),
            node("probe", options = listOf(option("reveal", next = "end", requires = "insight"))),
            node("end", outcome = "REFUSE"),
        )
        val state = NegotiationEngine.start(requiredFlow, seed = 4L, tier = 2)
        val blocked = NegotiationEngine.choose(state, "reveal")
        assertFalse(blocked.accepted)
        assertTrue(blocked.message?.contains("requirement not met") == true)

        val insight = NegotiationEngine.choose(state, "look")
        assertTrue(insight.accepted)
        assertTrue(insight.state.flags.contains("insight"))
        assertEquals("probe", insight.state.nodeId)

        val reveal = NegotiationEngine.choose(insight.state, "reveal")
        assertTrue(reveal.accepted)
        assertEquals(NegotiationOutcome.REFUSE, reveal.outcome)
    }

    @Test
    fun departIsOnlyReachableThroughAuthoredNodesAndNeverOnTheFirstConversation() {
        val departFlow = flow(
            "start",
            node("start", options = listOf(option("tell", next = "depart"))),
            node("depart", tag = "depart", outcome = "DEPART"),
        )
        val firstConversation = NegotiationEngine.start(departFlow, seed = 1L, tier = 3, firstConversation = true)
        val blocked = NegotiationEngine.choose(firstConversation, "tell")
        assertTrue(blocked.accepted)
        assertTrue(blocked.departBlocked)
        assertEquals(NegotiationOutcome.REFUSE, blocked.outcome)
        assertFalse(blocked.state.departUsed)

        val laterConversation = NegotiationEngine.start(departFlow, seed = 1L, tier = 3, firstConversation = false)
        val departed = NegotiationEngine.choose(laterConversation, "tell")
        assertTrue(departed.accepted)
        assertEquals(NegotiationOutcome.DEPART, departed.outcome)
        assertTrue(departed.state.departUsed)
        assertTrue(departed.state.finished)
    }

    @Test
    fun departEffectCannotBeReused() {
        val departEffectFlow = flow(
            "start",
            node("start", options = listOf(option("go", next = "end", effects = effects(depart = true)))),
            node("end", outcome = "LEAVE"),
        )
        val state = NegotiationEngine.start(departEffectFlow, seed = 1L, tier = 2, departUsed = true)
        val step = NegotiationEngine.choose(state, "go")
        assertFalse(step.accepted)
        assertTrue(step.message?.contains("departure already used") == true)
    }

    @Test
    fun unknownNextNodeRejectsTheChoiceWithoutApplyingEffects() {
        val brokenFlow = flow(
            "start",
            node("start", options = listOf(option("go", next = "ghost", effects = effects(bits = -10)))),
        )
        val state = NegotiationEngine.start(brokenFlow, seed = 1L, tier = 2)
        val step = NegotiationEngine.choose(state, "go", playerBits = 100L)
        assertFalse(step.accepted)
        assertEquals(0L, step.state.bitsPaid)
        assertEquals(state, step.state)
    }

    @Test
    fun malformedOrEmptyContentIsSafe() {
        val malformed = NegotiationFlowDto(start = "missing", nodes = emptyMap())
        assertTrue(NegotiationEngine.start(malformed, seed = 1L, tier = 2).finished)
        assertEquals(NegotiationOutcome.LEAVE, NegotiationEngine.start(malformed, 1L, 2).outcome)
        assertNull(NegotiationEngine.startOrNull(null, seed = 1L, tier = 2))
        assertNull(NegotiationEngine.startOrNull(malformed, seed = 1L, tier = 2))
        assertNotNull(NegotiationEngine.startOrNull(probeFlow(), seed = 1L, tier = 2))
    }

    @Test
    fun aTerminalStartNodeFinishesImmediately() {
        val terminal = flow("start", node("start", outcome = "JOIN"))
        val state = NegotiationEngine.start(terminal, seed = 1L, tier = 2)
        assertTrue(state.finished)
        assertEquals(NegotiationOutcome.JOIN, state.outcome)
    }

    @Test
    fun choosingAfterTheEndIsRejected() {
        val state = NegotiationEngine.start(flow("start", node("start", outcome = "JOIN")), 1L, 2)
        val step = NegotiationEngine.choose(state, "anything")
        assertFalse(step.accepted)
        assertEquals(state, step.state)
    }

    @Test
    fun availableOptionsExposeTheNonPaymentBranchWhenThePlayerCannotPay() {
        val demand = com.tamerslab.digiline.model.DemandDto(
            kind = com.tamerslab.digiline.model.DemandDto.KIND_BITS,
            amount = 100,
        )
        val choiceFlow = flow(
            "start",
            node(
                "start",
                tag = "demand",
                demand = demand,
                options = listOf(
                    option("pay", next = "join", effects = effects(bits = -100)),
                    option("refuse", next = "refuse"),
                ),
            ),
            node("join", outcome = "JOIN"),
            node("refuse", outcome = "REFUSE"),
        )
        val state = NegotiationEngine.start(choiceFlow, seed = 1L, tier = 2)
        val poor = NegotiationEngine.availableOptions(state, playerBits = 50L)
        assertEquals(listOf("refuse"), poor.map { it.id })
        val rich = NegotiationEngine.availableOptions(state, playerBits = 1000L)
        assertEquals(listOf("pay", "refuse"), rich.map { it.id })
        assertEquals(demand, NegotiationEngine.demand(state))
    }

    @Test
    fun phasesFollowTheNodeTags() {
        fun stateAt(tag: String): NegotiationState = NegotiationEngine.start(
            flow("start", node("start", tag = tag)),
            1L,
            2,
        )
        assertEquals(NegotiationPhase.APPROACH, NegotiationEngine.phase(stateAt("greeting")))
        assertEquals(NegotiationPhase.PROBE, NegotiationEngine.phase(stateAt("probe")))
        assertEquals(NegotiationPhase.BARGAIN, NegotiationEngine.phase(stateAt("demand")))
        assertEquals(NegotiationPhase.TEST, NegotiationEngine.phase(stateAt("trick")))
        assertEquals(NegotiationPhase.RESOLVE, NegotiationEngine.phase(stateAt("resolve")))
    }

    @Test
    fun everyAuthoredOutcomeValueMapsToItsEnum() {
        val outcomes = mapOf(
            "JOIN" to NegotiationOutcome.JOIN,
            "REFUSE" to NegotiationOutcome.REFUSE,
            "LEAVE" to NegotiationOutcome.LEAVE,
            "FIGHT" to NegotiationOutcome.FIGHT,
            "PAY_AND_LEAVE" to NegotiationOutcome.PAY_AND_LEAVE,
            "DEPART" to NegotiationOutcome.DEPART,
        )
        for ((wire, expected) in outcomes) {
            val terminalFlow = flow("start", node("start", tag = "resolve", outcome = wire))
            val state = NegotiationEngine.start(terminalFlow, seed = 1L, tier = 2)
            assertTrue("$wire should finish the negotiation", state.finished)
            assertEquals("$wire should map to $expected", expected, state.outcome)
        }
    }

    @Test
    fun theSameChoicesAlwaysReplayTheSameSession() {
        fun run(): NegotiationState {
            val state = NegotiationEngine.start(probeFlow(), seed = 77L, tier = 2)
            val first = NegotiationEngine.choose(state, "talk")
            return NegotiationEngine.choose(first.state, "ask").state
        }
        assertEquals(run(), run())
        val replayed = run()
        assertTrue(replayed.finished)
        assertEquals(NegotiationOutcome.JOIN, replayed.outcome)
    }
}
