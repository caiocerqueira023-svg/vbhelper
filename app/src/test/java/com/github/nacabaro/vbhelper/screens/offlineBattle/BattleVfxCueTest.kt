package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.BattleEvent
import com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleVfxCueTest {
    private fun snapshot(events: List<BattleEvent>, eventCount: Long = events.size.toLong()) = BattleSnapshot(
        elapsedMillis = 1_000L,
        isPaused = false,
        pauseReason = null,
        commandPoints = 0,
        maxCommandPoints = 100,
        alliedMembers = emptyList(),
        opposingMembers = emptyList(),
        pendingSupportCombatantIds = emptySet(),
        result = null,
        recentEvents = events,
        eventCount = eventCount
    )

    @Test fun techniqueMissUsesItsIntendedTargetAsTheFailureEffectAnchor() {
        val cue = latestBattleMissCue(snapshot(listOf(
            BattleEvent.TechniqueStarted("ally", "beam", "enemy"),
            BattleEvent.StateChanged("ally", com.github.nacabaro.vbhelper.battle.offline.core.CombatantState.ATTACK_ACTIVE),
            BattleEvent.TechniqueMissed("ally", "beam", "out of range")
        ), eventCount = 43L))

        assertEquals(BattleMissCue(eventId = 43L, anchorCombatantId = "enemy"), cue)
    }

    @Test fun projectileMissFindsTheTargetFromTheLaunchAndStartupChain() {
        val cue = latestBattleMissCue(snapshot(listOf(
            BattleEvent.TechniqueStarted("ally", "bolt", "enemy"),
            BattleEvent.ProjectileLaunched(7L, "ally", "bolt"),
            BattleEvent.ProjectileMissed(7L, "bolt")
        )))

        assertEquals(BattleMissCue(eventId = 3L, anchorCombatantId = "enemy"), cue)
    }

    @Test fun successfulAttackDoesNotProduceAFailureCue() {
        val cue = latestBattleMissCue(snapshot(listOf(
            BattleEvent.TechniqueStarted("ally", "old", "enemy"),
            BattleEvent.TechniqueMissed("ally", "old", "out of range"),
            BattleEvent.TechniqueStarted("ally", "strike", "enemy"),
            BattleEvent.TechniqueHit("ally", "enemy", "strike", 40)
        )))

        assertNull(cue)
    }

    @Test fun failedSpecialProducesADistinctArenaFailureCue() {
        val cue = latestBattleMissCue(snapshot(listOf(
            BattleEvent.SpecialStarted("ally", "innate", "enemy"),
            BattleEvent.TechniqueStarted("ally", "innate", "enemy"),
            BattleEvent.TechniqueMissed("ally", "innate", "target moved"),
            BattleEvent.SpecialResolved("ally", "innate", "enemy", success = false)
        )))

        assertEquals(BattleMissCue(eventId = 4L, anchorCombatantId = "enemy", isSpecial = true), cue)
    }
}
