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
}
