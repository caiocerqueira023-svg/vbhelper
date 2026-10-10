package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class BattleRepositioningTest {
    private val close = TechniqueDefinition("close", "Close", TechniqueKind.MELEE, 20,
        minRange = 0.8f, maxRange = 1.2f, rangeProfile = TechniqueRangeProfile.CLOSE,
        startupMillis = 68, activeMillis = 34, recoveryMillis = 68, criticalChance = 0f)
    private val ranged = close.copy(techniqueId = "ranged", minRange = 3f, maxRange = 10.5f,
        rangeProfile = TechniqueRangeProfile.MEDIUM_LONG)
    private fun fighter(id: String, side: BattleSide, skills: List<String> = listOf("close")) =
        CombatantDefinition(id, displayName = id, side = side, maxHealth = 100_000, maxEnergy = 1_000,
            attack = 100, defense = 40, movementSpeed = 3f, techniqueIds = skills,
            energyRegenerationPerSecond = 0f, decisionDelayMinMillis = 0, decisionDelayMaxMillis = 0,
            aiProfile = BattleAiProfile(targetPolicy = BattleTargetPolicy.CLOSEST))

    private fun sim(actor: CombatantDefinition, enemies: List<CombatantDefinition>, catalog: List<TechniqueDefinition>) =
        BattleSimulator(BattleConfiguration(randomSeed = 41), BattleTeam("allies", BattleSide.ALLIED, listOf(actor)),
            BattleTeam("enemies", BattleSide.OPPOSING, enemies), catalog)
    private fun run(sim: BattleSimulator, millis: Long) = repeat((millis / 34).toInt()) { sim.advance(34) }

    @Test fun mixedRangeObservationUsesLateralSpaceWithoutPrematurelyChoosingTheWrongRange() {
        val actor = fighter("a", BattleSide.ALLIED, listOf("close", "ranged"))
            .copy(decisionDelayMinMillis = 3_000, decisionDelayMaxMillis = 3_000)
        val enemy = fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val sim = sim(actor, listOf(enemy), listOf(close, ranged))
        val opening = sim.snapshot()
        run(sim, 1_000)
        val later = sim.snapshot()
        val from = opening.alliedMembers.single().position
        val to = later.alliedMembers.single().position
        assertTrue("Mixed kits should find lateral space instead of standing magnetically fixed", from.distanceTo(to) > 0.25f)
        assertTrue(abs(to.distanceTo(enemyPosition(later)) - from.distanceTo(enemyPosition(opening))) < 0.1f)
        assertEquals(1_000, later.alliedMembers.single().energy)
        assertNull(later.alliedMembers.single().activeTechniqueId)
    }

    private fun enemyPosition(snapshot: BattleSnapshot) = snapshot.opposingMembers.single().position

    @Test fun recoveringReadinessAllowsFootworkWithoutAnotherAttackOrResourceDebit() {
        val heavy = close.copy(techniqueId = "heavy", power = 220, maxRange = 4.4f,
            rangeProfile = TechniqueRangeProfile.CLOSE_MEDIUM, readinessCost = 255f, energyCost = 20, cooldownMillis = 0)
        val actor = fighter("a", BattleSide.ALLIED, listOf("heavy")).copy(readinessRegenerationPerSecond = 1f)
        val enemy = fighter("b", BattleSide.OPPOSING, listOf("heavy")).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val sim = sim(actor, listOf(enemy), listOf(heavy))
        repeat(400) {
            if (sim.snapshot().alliedMembers.single().debug.readinessWaitingMillis == 0L) sim.advance(34)
        }
        val before = sim.snapshot()
        assertTrue(before.alliedMembers.single().debug.readiness < 0f)
        run(sim, 680)
        val after = sim.snapshot()
        assertTrue("Waiting for readiness should allow controlled lateral footwork: before=${before.alliedMembers.single()} after=${after.alliedMembers.single()}",
            before.alliedMembers.single().position.distanceTo(after.alliedMembers.single().position) > 0.2f)
        assertEquals(before.opposingMembers.single().health, after.opposingMembers.single().health)
        assertEquals(before.alliedMembers.single().energy, after.alliedMembers.single().energy)
        assertTrue(after.alliedMembers.single().position.distanceTo(enemyPosition(after)) in 2.4f..3.9f)
    }

    @Test fun unstartedFallbackIsReconsideredWhenTheEquippedMoveBecomesAvailable() {
        val heavy = close.copy(techniqueId = "heavy", power = 220, maxRange = 4.4f,
            rangeProfile = TechniqueRangeProfile.CLOSE_MEDIUM, readinessCost = 255f, energyCost = 20,
            cooldownMillis = 700)
        val actor = fighter("a", BattleSide.ALLIED, listOf("heavy")).copy(readinessRegenerationPerSecond = 1f)
        val enemy = fighter("b", BattleSide.OPPOSING, listOf("heavy")).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val sim = sim(actor, listOf(enemy), listOf(heavy))
        repeat(500) { if (sim.snapshot().alliedMembers.single().activeTechniqueId != "basic_attack") sim.advance(34) }
        assertEquals("basic_attack", sim.snapshot().alliedMembers.single().activeTechniqueId)
        val energy = sim.snapshot().alliedMembers.single().energy
        run(sim, 1_000)
        assertEquals("heavy", sim.snapshot().alliedMembers.single().activeTechniqueId)
        assertEquals(energy, sim.snapshot().alliedMembers.single().energy)
        assertTrue(sim.snapshot().alliedMembers.single().debug.readiness < 0f)
    }

    @Test fun zeroMovementVersionRetainsTheExplicitHistoricalBehavior() {
        val actor = fighter("a", BattleSide.ALLIED, listOf("close", "ranged"))
            .copy(decisionDelayMinMillis = 3_000, decisionDelayMaxMillis = 3_000)
        val enemy = fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val old = BattleConfiguration(randomSeed = 41, movementRulesVersion = 0)
        fun historical(config: BattleConfiguration) = BattleSimulator(config,
            BattleTeam("allies", BattleSide.ALLIED, listOf(actor)), BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(close, ranged))
        val a = historical(old)
        val b = historical(old.copy(movementRulesVersion = BattleMovementRules.LEGACY_VERSION))
        val opening = a.snapshot().alliedMembers.single().position
        run(a, 1_000); run(b, 1_000)
        assertEquals(a.snapshot(), b.snapshot())
        assertEquals(opening, a.snapshot().alliedMembers.single().position)
    }

    @Test fun autonomousPreparationReconsidersAnApproachingBetterTargetBeforeWindup() {
        val actor = fighter("a", BattleSide.ALLIED).copy(movementSpeed = 0f)
        val far = fighter("b1", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val incoming = fighter("b2", BattleSide.OPPOSING).copy(movementSpeed = 5f)
        val sim = sim(actor, listOf(far, incoming), listOf(close))
        sim.advance(204)
        assertEquals("b1", sim.snapshot().alliedMembers.single().targetId)
        run(sim, 850)
        assertEquals("A pre-start chase should respond to the much closer opponent", "b2",
            sim.snapshot().alliedMembers.single().targetId)
    }

    @Test fun trainerSpecifiedTargetAndReservationSurviveTheSameNearbyOpportunity() {
        val actor = fighter("a", BattleSide.ALLIED).copy(movementSpeed = 0f)
        val far = fighter("b1", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val incoming = fighter("b2", BattleSide.OPPOSING).copy(movementSpeed = 5f)
        val paid = close.copy(energyCost = 50)
        val sim = sim(actor, listOf(far, incoming), listOf(paid))
        val order = sim.issueOrder("a", TrainerAction.UseTechnique("close", "b1"))
        run(sim, 1_050)
        val member = sim.snapshot().alliedMembers.single()
        assertEquals("b1", member.targetId)
        assertEquals(order.orderId, member.currentOrderId)
        assertEquals(50, member.reservedEnergy)
        assertEquals(1_000, member.energy)
    }

    @Test fun trainerFocusRemainsPinnedDuringAutonomousPreparation() {
        val actor = fighter("a", BattleSide.ALLIED).copy(movementSpeed = 0f)
        val far = fighter("b1", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val incoming = fighter("b2", BattleSide.OPPOSING).copy(movementSpeed = 5f)
        val sim = sim(actor, listOf(far, incoming), listOf(close))
        sim.issueOrder("a", TrainerAction.FocusTarget("b1"))
        run(sim, 1_050)
        assertEquals("b1", sim.snapshot().alliedMembers.single().targetId)
    }

    @Test fun anAttackAlreadyInWindupKeepsItsCommittedTarget() {
        val windup = close.copy(techniqueId = "windup", minRange = 0f, maxRange = 20f,
            rangeProfile = TechniqueRangeProfile.CUSTOM, startupMillis = 2_000)
        val actor = fighter("a", BattleSide.ALLIED, listOf("windup")).copy(movementSpeed = 0f)
        val far = fighter("b1", BattleSide.OPPOSING).copy(movementSpeed = 0f,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val incoming = fighter("b2", BattleSide.OPPOSING).copy(movementSpeed = 5f)
        val sim = sim(actor, listOf(far, incoming), listOf(windup, close))
        sim.advance(204)
        assertEquals(CombatantState.ATTACK_STARTUP, sim.snapshot().alliedMembers.single().state)
        run(sim, 850)
        assertEquals("b1", sim.snapshot().alliedMembers.single().targetId)
        assertEquals(CombatantState.ATTACK_STARTUP, sim.snapshot().alliedMembers.single().state)
    }

    @Test fun boundaryRetreatFindsAUsableTangentInsteadOfPushingAgainstTheWall() {
        val enemy = BattlePosition(-6f, 0f)
        var position = BattlePosition(-7.55f, 0f)
        val initial = position
        repeat(80) {
            val dx = position.x - enemy.x
            val dz = position.z - enemy.z
            val length = hypot(dx, dz)
            val goal = BattlePosition(enemy.x + dx / length * 6.5f, enemy.z + dz / length * 6.5f)
            position = steerBattlePosition(position, goal, 0.1f, 0.45f, 8f,
                listOf(BattleMovementObstacle(enemy, 0.45f)), 1f)
            assertTrue(hypot(position.x, position.z) <= 7.551f)
        }
        assertTrue("Retreat must leave the blocked ray", abs(position.z) > 1f)
        assertTrue(position.distanceTo(enemy) > initial.distanceTo(enemy) + 1f)
    }

    @Test fun movementGoesAroundAnOccupiedLaneWithoutCrossingTheOtherFighter() {
        val obstacle = BattleMovementObstacle(BattlePosition(-1f, 0f), 0.45f)
        var position = BattlePosition(-2f, 0f)
        var lateral = 0f
        repeat(30) {
            position = steerBattlePosition(position, BattlePosition(3f, 0f), 0.1f, 0.45f, 8f,
                listOf(obstacle), 1f)
            lateral = maxOf(lateral, abs(position.z))
            assertTrue(position.distanceTo(obstacle.position) >= 0.899f)
        }
        assertTrue(lateral > 0.4f)
        assertTrue(position.x > -1.2f)
    }
}
