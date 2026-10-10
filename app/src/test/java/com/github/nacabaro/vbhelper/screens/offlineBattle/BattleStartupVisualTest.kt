package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class BattleStartupVisualTest {
    @Test fun startupEnergyGathersTowardReleaseRatherThanCyclingAtAnUnrelatedRate() {
        val beginning = battleStartupVisual(0f, false, true)
        val ready = battleStartupVisual(1f, false, true)
        assertTrue(ready.orbSize > beginning.orbSize)
        assertTrue(ready.orbOpacity > beginning.orbOpacity)
        assertTrue(ready.floorSize < beginning.floorSize)
        assertTrue(ready.floorOpacity < 0.3f)
        assertTrue(ready.orbSize < 0.5f)
        assertTrue(ready.particleCount in 1..8)
    }

    @Test fun reducedMotionKeepsAQuietLegibleChargeWithoutOrbitingParticles() {
        val cue = battleStartupVisual(0.5f, true, false)
        assertEquals(0, cue.particleCount)
        assertTrue(cue.orbOpacity > 0f)
        assertTrue(cue.orbSize < 0.5f)
    }

    @Test fun snapshotPublishesRealStartupProgressAndPauseFreezesIt() {
        val technique = TechniqueDefinition("windup", "Windup", TechniqueKind.MELEE, 10,
            maxRange = 20f, startupMillis = 340, activeMillis = 34, recoveryMillis = 68, cooldownMillis = 1000)
        fun fighter(id: String, side: BattleSide) = CombatantDefinition(id, displayName = id, side = side,
            maxHealth = 1000, maxEnergy = 100, attack = 100, defense = 40, movementSpeed = 0f,
            techniqueIds = listOf("windup"), decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val sim = BattleSimulator(BattleConfiguration(), BattleTeam("allies", BattleSide.ALLIED,
            listOf(fighter("a", BattleSide.ALLIED))), BattleTeam("enemies", BattleSide.OPPOSING,
            listOf(fighter("b", BattleSide.OPPOSING))), listOf(technique))
        sim.issueOrder("a", TrainerAction.UseTechnique("windup", "b"))
        sim.advance(34)
        assertEquals(0f, sim.snapshot().alliedMembers.single().activeTechniqueStartupProgress!!, 0f)
        sim.advance(102)
        val progress = sim.snapshot().alliedMembers.single().activeTechniqueStartupProgress!!
        assertEquals(0.3f, progress, 0.001f)
        sim.setPaused(true, "manual")
        sim.advance(250)
        assertEquals(progress, sim.snapshot().alliedMembers.single().activeTechniqueStartupProgress!!, 0f)
        sim.setPaused(false)
        sim.advance(250)
        assertNull(sim.snapshot().alliedMembers.single().activeTechniqueStartupProgress)
    }
}
