package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.session.BattleSessionController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattleSessionControllerTest {
    private fun simulator(paused: Boolean = false): BattleSimulator {
        fun fighter(id: String, side: BattleSide) = CombatantDefinition(id, displayName = id, side = side,
            maxHealth = 10000, maxEnergy = 0, attack = 100, defense = 100,
            movementSpeed = 0f, techniqueIds = emptyList())
        return BattleSimulator(BattleConfiguration(defaultPaused = paused),
            BattleTeam("a", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING))), emptyList())
    }

    @Test fun releasingBackgroundDoesNotReleaseTheInitialMenuPause() = runTest {
        val controller = BattleSessionController(simulator(true), backgroundScope) { testScheduler.currentTime * 1_000_000 }
        controller.start()
        controller.setPaused("background", true)
        controller.setPaused("background", false)
        advanceTimeBy(1000)
        assertTrue(controller.snapshot.value.isPaused)
        assertEquals(0L, controller.snapshot.value.elapsedMillis)
        controller.setPaused("menu", false)
        advanceTimeBy(1000)
        assertFalse(controller.snapshot.value.isPaused)
        assertTrue(controller.snapshot.value.elapsedMillis > 0)
        controller.close()
    }

    @Test fun backgroundTimeIsNotSimulatedOnResume() = runTest {
        val controller = BattleSessionController(simulator(), backgroundScope) { testScheduler.currentTime * 1_000_000 }
        controller.start()
        advanceTimeBy(500)
        controller.setPaused("background", true)
        val before = controller.snapshot.value.elapsedMillis
        advanceTimeBy(60_000)
        assertEquals(before, controller.snapshot.value.elapsedMillis)
        controller.setPaused("background", false)
        advanceTimeBy(100)
        assertTrue(controller.snapshot.value.elapsedMillis - before in 34L..102L)
        controller.close()
    }

    @Test fun startIsIdempotentAndCloseStopsTheTicker() = runTest {
        val controller = BattleSessionController(simulator(), backgroundScope) { testScheduler.currentTime * 1_000_000 }
        controller.start()
        controller.start()
        advanceTimeBy(1024)
        runCurrent()
        assertEquals(1020L, controller.snapshot.value.elapsedMillis)
        controller.close()
        val closed = controller.snapshot.value
        advanceTimeBy(5000)
        assertEquals(closed, controller.snapshot.value)
        assertThrows(IllegalStateException::class.java) { controller.start() }
        assertThrows(IllegalStateException::class.java) { controller.issueOrder("a", TrainerAction.Defend()) }
    }

    @Test fun fractionalFrameTimeIsCarriedRatherThanDiscarded() = runTest {
        var clock = 0L
        val controller = BattleSessionController(simulator(), backgroundScope) { clock }
        controller.start()
        runCurrent()
        repeat(40) {
            clock += 16_900_000L
            advanceTimeBy(16)
            runCurrent()
        }
        assertEquals(646L, controller.snapshot.value.elapsedMillis) // floor(676 / 34) * 34
        controller.close()
    }

    private fun blastSimulator(): BattleSimulator {
        fun fighter(id: String, side: BattleSide) = CombatantDefinition(id, displayName = id, side = side,
            maxHealth = 100_000, maxEnergy = 100, attack = 100, defense = 100, movementSpeed = 0f,
            techniqueIds = emptyList(), specialTechniqueId = "special",
            blastMode = "FORM", blastTargetSpecies = "Result",
            decisionDelayMinMillis = 150_000, decisionDelayMaxMillis = 150_000)
        val sim = BattleSimulator(BattleConfiguration(),
            BattleTeam("a", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING))),
            listOf(TechniqueDefinition("special", "Special", TechniqueKind.SPECIAL, 100,
                maxRange = 30f, startupMillis = 340)))
        repeat(2_400) { sim.advance(34) }
        assertEquals(100, sim.snapshot().alliedMembers.single().specialCharge)
        sim.issueOrder("a", TrainerAction.UseTechnique("special", "b"))
        sim.advance(34)
        assertTrue("a" in sim.snapshot().pendingBlastTiming)
        return sim
    }

    @Test fun blastMenuCannotMaskManualOrBackgroundPauseInEitherInsertionOrder() = runTest {
        listOf("manual", "background").forEach { external ->
            listOf(true, false).forEach { externalFirst ->
                val controller = BattleSessionController(blastSimulator(), backgroundScope) { testScheduler.currentTime * 1_000_000 }
                controller.setPaused(if (externalFirst) external else "blast-menu", true)
                controller.setPaused(if (externalFirst) "blast-menu" else external, true)
                val tap = controller.issueOrder("a", TrainerAction.ConfirmBlastTiming())
                assertEquals(OrderFailure.BLAST_PAUSED, tap.reasonCode)
                assertTrue("a" in controller.snapshot.value.pendingBlastTiming)
                controller.setPaused(external, false)
                assertEquals(OrderStatus.COMPLETED, controller.issueOrder("a", TrainerAction.ConfirmBlastTiming()).status)
                assertNotNull(controller.snapshot.value.finisher)
                controller.close()
            }
        }
    }

    @Test fun singleTickerAdvancesCinematicAndComposedPausesFreezeIt() = runTest {
        val controller = BattleSessionController(blastSimulator(), backgroundScope) { testScheduler.currentTime * 1_000_000 }
        controller.start()
        controller.setPaused("blast-menu", true)
        controller.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        val combatTime = controller.snapshot.value.elapsedMillis
        advanceTimeBy(500)
        assertEquals(0L, controller.snapshot.value.finisher!!.elapsedMillis)
        controller.setPaused("blast-menu", false)
        advanceTimeBy(1_000)
        val cinematicTime = controller.snapshot.value.finisher!!.elapsedMillis
        assertTrue(cinematicTime in 980L..1_020L)
        assertEquals(combatTime, controller.snapshot.value.elapsedMillis)
        controller.setPaused("manual", true)
        controller.setPaused("background", true)
        advanceTimeBy(5_000)
        controller.setPaused("background", false)
        advanceTimeBy(5_000)
        assertEquals(cinematicTime, controller.snapshot.value.finisher!!.elapsedMillis)
        controller.setPaused("manual", false)
        advanceTimeBy(100)
        assertTrue(controller.snapshot.value.finisher!!.elapsedMillis - cinematicTime in 80L..112L)
        controller.close()
        assertNull(controller.snapshot.value.finisher)
        assertNull(controller.snapshot.value.alliedMembers.single().blastFormSpecies)
        assertEquals(1, controller.snapshot.value.recentEvents.filterIsInstance<BattleEvent.BlastFormEnded>().size)
        val closed = controller.snapshot.value
        controller.close()
        advanceTimeBy(5_000)
        assertEquals(closed, controller.snapshot.value)
    }
}
