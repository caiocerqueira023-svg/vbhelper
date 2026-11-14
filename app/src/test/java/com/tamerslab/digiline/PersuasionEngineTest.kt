package com.tamerslab.digiline

import com.tamerslab.digiline.engine.PersuasionEngine
import com.tamerslab.digiline.model.NegotiationFlowDto
import com.tamerslab.digiline.model.PersuasionOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersuasionEngineTest {
    private fun stageFlow(outcome: String): NegotiationFlowDto = flow(
        "start",
        node("start", tag = "greeting", options = listOf(option("talk", next = "resolve"))),
        node("resolve", tag = "resolve", outcome = outcome),
    )

    @Test
    fun stagesZeroToThreeProgressAndStageThreeOffersTheQuest() {
        val flows = mapOf(
            0 to stageFlow("NEXT_STAGE"),
            1 to stageFlow("NEXT_STAGE"),
            2 to stageFlow("NEXT_STAGE"),
            3 to stageFlow("QUEST_OFFER"),
        )
        var stage = 0
        while (stage < PersuasionEngine.MAX_STAGE) {
            val state = PersuasionEngine.start(flows.getValue(stage), stage = stage, personalityType = 6)
            assertNotNull(state)
            val step = PersuasionEngine.choose(state!!, "talk")
            assertTrue(step.accepted)
            assertEquals(PersuasionOutcome.NEXT_STAGE, step.outcome)
            assertEquals(stage + 1, step.nextStage)
            assertEquals(stage + 1, step.state.stage)
            assertTrue(step.state.finished)
            stage = step.nextStage!!
        }
        val finalState = PersuasionEngine.start(flows.getValue(3), stage = 3, personalityType = 6)
        val finalStep = PersuasionEngine.choose(finalState!!, "talk")
        assertEquals(PersuasionOutcome.QUEST_OFFER, finalStep.outcome)
        assertTrue(finalStep.questOffer)
        assertTrue(finalStep.terminal)
        assertTrue(PersuasionEngine.isArcComplete(finalStep.state))
    }

    @Test
    fun nextStageClampsAtTheAuthoredRange() {
        assertEquals(1, PersuasionEngine.nextStage(0))
        assertEquals(2, PersuasionEngine.nextStage(1))
        assertEquals(3, PersuasionEngine.nextStage(2))
        assertEquals(3, PersuasionEngine.nextStage(3))
        assertEquals(3, PersuasionEngine.nextStage(99))
        assertEquals(1, PersuasionEngine.nextStage(-4))
    }

    @Test
    fun emptyOrMalformedContentReturnsNull() {
        assertNull(PersuasionEngine.start(null, stage = 0))
        assertNull(PersuasionEngine.start(NegotiationFlowDto(start = "missing"), stage = 0))
        assertNull(PersuasionEngine.start(NegotiationFlowDto(), stage = 0))
        assertNotNull(PersuasionEngine.start(stageFlow("NEXT_STAGE"), stage = 0))
    }

    @Test
    fun flagsAreAppliedAndGateOptions() {
        val gatedFlow = flow(
            "start",
            node(
                "start",
                options = listOf(
                    option("reason", next = "probe", effects = effects(persuasionFlag = "opened_up")),
                    option("confess", next = "end", requires = "opened_up"),
                ),
            ),
            node("probe", options = listOf(option("confess", next = "end", requires = "opened_up"))),
            node("end", outcome = "QUEST_OFFER"),
        )
        val state = PersuasionEngine.start(gatedFlow, stage = 2)!!
        val blocked = PersuasionEngine.choose(state, "confess")
        assertFalse(blocked.accepted)
        assertTrue(blocked.message?.contains("requirement not met") == true)

        val opened = PersuasionEngine.choose(state, "reason")
        assertTrue(opened.accepted)
        assertTrue(opened.state.flags.contains("opened_up"))
        assertEquals("probe", opened.state.nodeId)

        val confess = PersuasionEngine.choose(opened.state, "confess")
        assertTrue(confess.accepted)
        assertEquals(PersuasionOutcome.QUEST_OFFER, confess.outcome)
    }

    @Test
    fun unknownOptionsAndFinishedConversationsAreRejected() {
        val state = PersuasionEngine.start(stageFlow("REFUSE"), stage = 0)!!
        val unknown = PersuasionEngine.choose(state, "nope")
        assertFalse(unknown.accepted)
        assertEquals(state, unknown.state)

        val finishedState = PersuasionEngine.start(flow("start", node("start", outcome = "REFUSE")), stage = 0)!!
        assertTrue(finishedState.finished)
        val rejected = PersuasionEngine.choose(finishedState, "talk")
        assertFalse(rejected.accepted)
        assertEquals(finishedState, rejected.state)
    }

    @Test
    fun optionsWithoutANextNodeEndSafely() {
        val quitFlow = flow(
            "start",
            node(
                "start",
                options = listOf(
                    option("quit"),
                    option("leave", effects = effects(depart = true)),
                ),
            ),
        )
        val state = PersuasionEngine.start(quitFlow, stage = 1)!!
        val quit = PersuasionEngine.choose(state, "quit")
        assertTrue(quit.accepted)
        assertEquals(PersuasionOutcome.REFUSE, quit.outcome)
        assertTrue(quit.state.finished)

        val depart = PersuasionEngine.choose(state, "leave")
        assertTrue(depart.accepted)
        assertEquals(PersuasionOutcome.DEPART, depart.outcome)
    }

    @Test
    fun unsupportedAuthoredOutcomesFallBackToRefuse() {
        val brokenFlow = flow(
            "start",
            node("start", options = listOf(option("go", next = "end"))),
            node("end", outcome = "JOIN"),
        )
        val state = PersuasionEngine.start(brokenFlow, stage = 0)!!
        val step = PersuasionEngine.choose(state, "go")
        assertEquals(PersuasionOutcome.REFUSE, step.outcome)
        assertNotNull(step.message)
        assertTrue(step.state.finished)
    }

    @Test
    fun missingNextNodeIsRejectedWithoutChangingTheState() {
        val brokenFlow = flow(
            "start",
            node("start", options = listOf(option("go", next = "ghost"))),
        )
        val state = PersuasionEngine.start(brokenFlow, stage = 0)!!
        val step = PersuasionEngine.choose(state, "go")
        assertFalse(step.accepted)
        assertEquals(state, step.state)
    }

    @Test
    fun availableOptionsRespectRequiredFlags() {
        val gatedFlow = flow(
            "start",
            node(
                "start",
                options = listOf(
                    option("a", next = "end"),
                    option("b", next = "end", requires = "flag"),
                ),
            ),
            node("end", outcome = "REFUSE"),
        )
        val state = PersuasionEngine.start(gatedFlow, stage = 0, flags = setOf("flag"))!!
        assertEquals(listOf("a", "b"), PersuasionEngine.availableOptions(state).map { it.id })
        val locked = PersuasionEngine.start(gatedFlow, stage = 0)!!
        assertEquals(listOf("a"), PersuasionEngine.availableOptions(locked).map { it.id })
    }

    @Test
    fun progressLabelsStayQualitative() {
        assertEquals("still wary", PersuasionEngine.progressLabel(0))
        assertEquals("warming up", PersuasionEngine.progressLabel(1))
        assertEquals("opening up", PersuasionEngine.progressLabel(2))
        assertEquals("ready to travel", PersuasionEngine.progressLabel(3))
        assertEquals("wants to travel with you", PersuasionEngine.progressLabel(3, questOfferPending = true))
    }

    @Test
    fun replayingTheSameConversationIsDeterministic() {
        val state = PersuasionEngine.start(stageFlow("NEXT_STAGE"), stage = 0, personalityType = 6)!!
        val first = PersuasionEngine.choose(state, "talk")
        val second = PersuasionEngine.choose(state, "talk")
        assertEquals(first.state, second.state)
        assertEquals(first.outcome, second.outcome)
    }
}
