package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import org.junit.Assert.*
import org.junit.Test

class BattleSimulatorTest {
    private fun fighter(id: String, side: BattleSide, skills: List<String> = emptyList()) =
        CombatantDefinition(id, displayName = id, side = side, maxHealth = 1000, maxEnergy = 100,
            attack = 100, defense = 0, movementSpeed = 3f, preferredDistance = 2f, techniqueIds = skills)

    private fun skill(id: String = "hit", power: Int = 10, range: Float = 20f) =
        TechniqueDefinition(id, id, TechniqueKind.MELEE, power, maxRange = range,
            startupMillis = 68, activeMillis = 68, recoveryMillis = 68, cooldownMillis = 500)

    private fun battle(
        allies: List<CombatantDefinition> = listOf(fighter("a", BattleSide.ALLIED)),
        enemies: List<CombatantDefinition> = listOf(fighter("b", BattleSide.OPPOSING)),
        skills: List<TechniqueDefinition> = emptyList(),
        config: BattleConfiguration = BattleConfiguration()
    ) = BattleSimulator(config, BattleTeam("allies", BattleSide.ALLIED, allies),
        BattleTeam("enemies", BattleSide.OPPOSING, enemies), skills)

    private fun runFor(sim: BattleSimulator, millis: Long) {
        repeat((millis / 34).toInt()) { sim.advance(34) }
    }

    private fun until(sim: BattleSimulator, millis: Long = 30_000, condition: (BattleSnapshot) -> Boolean) {
        repeat((millis / 34).toInt()) {
            if (condition(sim.snapshot())) return
            sim.advance(34)
        }
        assertTrue("Condition not reached: ${sim.snapshot()}", condition(sim.snapshot()))
    }

    private fun updates(sim: BattleSimulator, id: Long) = sim.snapshot().recentEvents
        .filterIsInstance<BattleEvent.OrderChanged>().map { it.update }.filter { it.orderId == id }

    private fun damageFromDataAgainst(defenderAttribute: BattleAttribute): Int {
        val strike = skill("attribute_strike", power = 100).copy(criticalChance = 0f)
        val attacker = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(attribute = BattleAttribute.DATA)
        val defender = fighter("b", BattleSide.OPPOSING)
            .copy(attribute = defenderAttribute, movementSpeed = 0f)
        val sim = battle(
            allies = listOf(attacker),
            enemies = listOf(defender),
            skills = listOf(strike),
            config = BattleConfiguration(defaultPaused = true, randomSeed = 41L)
        )
        sim.issueOrder("a", TrainerAction.UseTechnique(strike.techniqueId, "b"))
        sim.setPaused(false)
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
                .any { hit -> hit.combatantId == "a" && hit.techniqueId == strike.techniqueId }
        }
        return sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .last { it.combatantId == "a" && it.techniqueId == strike.techniqueId }.amount
    }

    @Test fun attributeAdvantageFollowsTheDigimonCycleAndFreeRemainsNeutral() {
        assertTrue(BattleAttribute.DATA.hasAdvantageOver(BattleAttribute.VACCINE))
        assertTrue(BattleAttribute.VACCINE.hasAdvantageOver(BattleAttribute.VIRUS))
        assertTrue(BattleAttribute.VIRUS.hasAdvantageOver(BattleAttribute.DATA))
        assertFalse(BattleAttribute.DATA.hasAdvantageOver(BattleAttribute.VIRUS))
        assertEquals(1f, BattleAttribute.FREE.damageMultiplierAgainst(BattleAttribute.DATA))
        assertEquals(1f, BattleAttribute.DATA.damageMultiplierAgainst(BattleAttribute.FREE))
        assertEquals(1f, BattleAttribute.NONE.damageMultiplierAgainst(BattleAttribute.VACCINE))
    }

    @Test fun attributeAdvantageProducesMoreDamageThanNeutralAndDisadvantage() {
        val advantage = damageFromDataAgainst(BattleAttribute.VACCINE)
        val neutral = damageFromDataAgainst(BattleAttribute.FREE)
        val disadvantage = damageFromDataAgainst(BattleAttribute.VIRUS)

        assertTrue("Expected advantage > neutral, got $advantage <= $neutral", advantage > neutral)
        assertTrue("Expected neutral > disadvantage, got $neutral <= $disadvantage", neutral > disadvantage)
    }

    @Test fun depletedCombatantsCanFinishWithoutInput() {
        val sim = battle(allies = listOf(fighter("a", BattleSide.ALLIED).copy(maxEnergy = 0)),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(maxEnergy = 0)))
        until(sim, 90_000) { it.result != null }
    }

    @Test fun trainingFormatsAllFinishAutonomously() {
        for ((allyCount, enemyCount) in listOf(1 to 1, 1 to 2, 2 to 2)) {
            fun inputs(count: Int) = (1..count).map {
                TrainingParticipantInput("$it", displayName = "Digimon $it", stage = 3, maxHealth = 3000, attack = 700)
            }
            val sim = TrainingBattleFactory.create(inputs(allyCount), inputs(enemyCount))
            until(sim, 120_000) { it.result != null }
        }
    }

    @Test fun snapshotExposesBoundedAiDecisionAndRejectedOrderDiagnostics() {
        val sim = battle()
        until(sim) { it.alliedMembers.single().debug.techniqueScores.isNotEmpty() }
        val fighter = sim.snapshot().alliedMembers.single()
        assertEquals("b", fighter.targetId)
        assertTrue(fighter.debug.targetDistance!!.isFinite())
        assertTrue(fighter.debug.techniqueScores.containsKey("basic_attack"))
        assertTrue(fighter.debug.decision.isNotBlank())

        val rejected = sim.issueOrder("a", TrainerAction.UseTechnique("missing"))
        assertEquals(OrderStatus.FAILED, rejected.status)
        assertEquals("Técnica inexistente.", sim.snapshot().alliedMembers.single().debug.lastOrderFailure)
        assertTrue(sim.snapshot().recentEvents.size <= 96)
    }

    @Test fun defendCompletesAndDoesNotPermitAttackDuringItsDuration() {
        val sim = battle(skills = listOf(skill()))
        val order = sim.issueOrder("a", TrainerAction.Defend(680))
        until(sim) { updates(sim, order.orderId).any { it.status == OrderStatus.EXECUTING } }
        val start = sim.snapshot().elapsedMillis
        runFor(sim, 646)
        assertEquals(CombatantState.DEFENDING, sim.snapshot().alliedMembers.single().state)
        runFor(sim, 238)
        assertTrue(sim.snapshot().elapsedMillis >= start + 680)
        assertEquals(1, updates(sim, order.orderId).count { it.status == OrderStatus.COMPLETED })
    }

    @Test fun defensiveStrategyAnticipatesAnEnemyWindupWithoutTrainerOrders() {
        val heavy = skill("windup", power = 180).copy(startupMillis = 900, recoveryMillis = 1200)
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED).copy(strategy = BattleStrategy.DEFENSIVE)),
            enemies = listOf(fighter("b", BattleSide.OPPOSING, listOf(heavy.techniqueId))),
            skills = listOf(heavy)
        )
        until(sim, 3000) { it.alliedMembers.single().state == CombatantState.DEFENDING }
        assertTrue(sim.snapshot().alliedMembers.single().debug.decision.contains("Antecipou"))
        assertTrue(sim.snapshot().recentEvents.none { it is BattleEvent.OrderChanged })
    }

    @Test fun keepingDistanceActuallyMovesAwayAndCompletes() {
        val sim = battle(enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)))
        val closer = sim.issueOrder("a", TrainerAction.MoveCloser)
        until(sim) { updates(sim, closer.orderId).any { it.status == OrderStatus.COMPLETED } }
        val keep = sim.issueOrder("a", TrainerAction.KeepDistance, interruptCurrentAction = true)
        until(sim) { updates(sim, keep.orderId).any { it.status == OrderStatus.COMPLETED } }
        val state = sim.snapshot()
        assertTrue(state.alliedMembers.single().position.distanceTo(state.opposingMembers.single().position) >= 3.8f)
    }

    @Test fun pursuitAndKnockbackKeepEveryCombatantInsideThePlayableArena() {
        val radius = 5f
        val sim = battle(
            allies = listOf(fighter("a1", BattleSide.ALLIED, listOf("heavy")), fighter("a2", BattleSide.ALLIED, listOf("heavy"))),
            enemies = listOf(fighter("b1", BattleSide.OPPOSING, listOf("heavy")), fighter("b2", BattleSide.OPPOSING, listOf("heavy"))),
            skills = listOf(skill("heavy", power = 50).copy(knockbackDistance = 2.2f)),
            config = BattleConfiguration(arenaRadius = radius, randomSeed = 29L)
        )
        repeat(500) {
            sim.advance(34)
            (sim.snapshot().alliedMembers + sim.snapshot().opposingMembers).forEach { member ->
                val edgeDistance = kotlin.math.hypot(member.position.x, member.position.z) + 0.45f
                assertTrue("${member.combatantId} crossed the arena boundary: $edgeDistance", edgeDistance <= radius + 0.001f)
            }
        }
    }

    @Test fun supportIsImmediateAndOnlyRewardsOncePerWindow() {
        val hit = skill().copy(activeMillis = 500, recoveryMillis = 2000)
        val sim = battle(allies = listOf(fighter("a", BattleSide.ALLIED, listOf(hit.techniqueId))), skills = listOf(hit))
        until(sim) { "a" in it.pendingSupportCombatantIds }
        assertEquals(OrderStatus.COMPLETED, sim.issueOrder("a", TrainerAction.Support).status)
        assertEquals(8, sim.snapshot().commandPoints)
        assertEquals(OrderStatus.FAILED, sim.issueOrder("a", TrainerAction.Support).status)
        assertEquals(8, sim.snapshot().commandPoints)
    }

    @Test fun activePhaseCannotBeCancelledByTrainer() {
        val hit = skill().copy(activeMillis = 1000, recoveryMillis = 1000)
        val sim = battle(allies = listOf(fighter("a", BattleSide.ALLIED, listOf(hit.techniqueId))), skills = listOf(hit))
        until(sim) { it.alliedMembers.single().state == CombatantState.ATTACK_ACTIVE }
        val order = sim.issueOrder("a", TrainerAction.Defend(), interruptCurrentAction = true)
        runFor(sim, 408)
        assertEquals(CombatantState.ATTACK_ACTIVE, sim.snapshot().alliedMembers.single().state)
        assertFalse(updates(sim, order.orderId).any { it.status == OrderStatus.EXECUTING })
    }

    @Test fun pauseFreezesEveryCombatClock() {
        val sim = battle()
        runFor(sim, 1020)
        sim.setPaused(true, "menu")
        val before = sim.snapshot()
        runFor(sim, 10200)
        assertEquals(before, sim.snapshot())
    }

    @Test fun terminalSessionRejectsOrdersAndEndsOnlyOnce() {
        val sim = battle()
        val result = sim.abandon()
        assertEquals(result, sim.abandon())
        assertEquals(OrderStatus.FAILED, sim.issueOrder("a", TrainerAction.Defend()).status)
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BattleEnded>().size)
    }

    @Test fun simultaneousLethalHitsAreADraw() {
        val lethal = skill(power = 10_000)
        val simultaneous = { id: String, side: BattleSide ->
            fighter(id, side, listOf("hit")).copy(decisionDelayMinMillis = 0L, decisionDelayMaxMillis = 0L)
        }
        val sim = battle(listOf(simultaneous("a", BattleSide.ALLIED)),
            listOf(simultaneous("b", BattleSide.OPPOSING)), listOf(lethal))
        until(sim) { it.result != null }
        assertEquals(BattleOutcome.DRAW, sim.snapshot().result!!.outcome)
    }

    @Test fun healingRespectsRangeAndDoesNotRetargetSelf() {
        val heal = skill("heal", 0, 0.5f).copy(kind = TechniqueKind.HEAL, healPower = 100)
        val sim = battle(allies = listOf(fighter("a", BattleSide.ALLIED, listOf("heal")).copy(movementSpeed = 0f),
            fighter("a2", BattleSide.ALLIED).copy(movementSpeed = 0f)), skills = listOf(heal))
        sim.issueOrder("a", TrainerAction.UseTechnique("heal", "a2"))
        runFor(sim, 2000)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().none { it.techniqueId == "heal" })
    }

    @Test fun rangedOrderRetreatsBeforeStartingAndPaysExactlyOnce() {
        val ranged = skill("ranged", range = 10.5f).copy(
            minRange = 3f,
            energyCost = 40,
            rangeProfile = TechniqueRangeProfile.MEDIUM_LONG
        )
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("ranged")).copy(energyRegenerationPerSecond = 0f)),
            listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)), listOf(ranged))
        val close = sim.issueOrder("a", TrainerAction.MoveCloser)
        until(sim) { updates(sim, close.orderId).any { it.status == OrderStatus.COMPLETED } }
        val order = sim.issueOrder("a", TrainerAction.UseTechnique("ranged", "b"))
        var retreated = false
        until(sim) {
            retreated = retreated || it.alliedMembers.single().state == CombatantState.MOVE_AWAY
            it.alliedMembers.single().state == CombatantState.ATTACK_STARTUP
        }
        val start = sim.snapshot()
        val distance = start.alliedMembers.single().position.distanceTo(start.opposingMembers.single().position)
        assertTrue("Medium-long user did not create distance", retreated)
        assertTrue("Medium-long attack started too close: $distance", distance >= 6.25f)
        assertTrue("Medium-long attack started beyond its useful lane: $distance", distance <= 9.5f)
        assertEquals(60, start.alliedMembers.single().energy)
        until(sim) { updates(sim, order.orderId).any { it.status == OrderStatus.COMPLETED } }
        assertEquals(60, sim.snapshot().alliedMembers.single().energy)
    }

    @Test fun rangedOnlyLoadoutUsesOpeningReadToClaimRangedSpace() {
        val ranged = skill("ranged", range = 10.5f).copy(
            minRange = 3f,
            rangeProfile = TechniqueRangeProfile.MEDIUM_LONG
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(ranged.techniqueId)).copy(
                decisionDelayMinMillis = 3_000L,
                decisionDelayMaxMillis = 3_000L
            )),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(
                movementSpeed = 0f,
                decisionDelayMinMillis = 10_000L,
                decisionDelayMaxMillis = 10_000L
            )),
            skills = listOf(ranged)
        )
        val opening = sim.snapshot().alliedMembers.single().position
            .distanceTo(sim.snapshot().opposingMembers.single().position)

        runFor(sim, 1_200L)

        val positioned = sim.snapshot().alliedMembers.single().position
            .distanceTo(sim.snapshot().opposingMembers.single().position)
        assertTrue("Ranged-only opening moved inward: $opening -> $positioned", positioned > opening + 0.5f)
    }

    @Test fun incompatibleMixedLoadoutDoesNotMoveOppositeTheTechniqueItChooses() {
        val close = skill("close", range = 2.2f).copy(
            minRange = 0.8f,
            rangeProfile = TechniqueRangeProfile.CLOSE
        )
        val ranged = skill("ranged", range = 10.5f).copy(
            minRange = 3f,
            rangeProfile = TechniqueRangeProfile.MEDIUM_LONG
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(close.techniqueId, ranged.techniqueId)).copy(
                strategy = BattleStrategy.RANGED,
                decisionDelayMinMillis = 1_800L,
                decisionDelayMaxMillis = 1_800L
            )),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(
                movementSpeed = 0f,
                decisionDelayMinMillis = 30_000L,
                decisionDelayMaxMillis = 30_000L
            )),
            skills = listOf(close, ranged)
        )
        val opening = sim.snapshot().alliedMembers.single().position
            .distanceTo(sim.snapshot().opposingMembers.single().position)
        var closestDistance = opening

        until(sim, millis = 15_000L) { snapshot ->
            closestDistance = minOf(
                closestDistance,
                snapshot.alliedMembers.single().position.distanceTo(snapshot.opposingMembers.single().position)
            )
            snapshot.recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
                .any { it.combatantId == "a" }
        }

        val start = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
            .first { it.combatantId == "a" }
        assertEquals(ranged.techniqueId, start.techniqueId)
        assertTrue(
            "Mixed loadout moved toward the close lane before choosing ranged: $opening -> $closestDistance",
            closestDistance >= opening - 0.1f
        )
    }

    @Test fun aroundUserAttackMovesInsideItsOwnImpactRadiusBeforeStarting() {
        val pulse = skill("pulse", range = 4.4f).copy(
            kind = TechniqueKind.AREA,
            minRange = 0.8f,
            rangeProfile = TechniqueRangeProfile.CLOSE_MEDIUM,
            impactShape = TechniqueImpactShape.AROUND_USER,
            areaRadius = 2f
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(pulse.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(pulse)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(pulse.techniqueId, "b"))
        until(sim) { it.alliedMembers.single().state == CombatantState.ATTACK_STARTUP }

        val snapshot = sim.snapshot()
        val distance = snapshot.alliedMembers.single().position.distanceTo(snapshot.opposingMembers.single().position)
        assertTrue("Around-user attack started outside its radius: $distance", distance <= pulse.areaRadius)
    }

    @Test fun closeMediumTechniqueClaimsAMidfieldLaneBeforeStarting() {
        val midfield = skill("midfield", range = 4.4f).copy(
            minRange = 0.8f,
            rangeProfile = TechniqueRangeProfile.CLOSE_MEDIUM
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(midfield.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(midfield)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(midfield.techniqueId, "b"))
        until(sim) { it.alliedMembers.single().state == CombatantState.ATTACK_STARTUP }

        val snapshot = sim.snapshot()
        val distance = snapshot.alliedMembers.single().position.distanceTo(snapshot.opposingMembers.single().position)
        assertTrue("Close-medium attack did not enter midfield: $distance", distance in 2.4f..3.9f)
    }

    @Test fun closeTechniqueClosesTheGapBeforeStarting() {
        val close = skill("close", range = 2.2f).copy(
            minRange = 0.8f,
            rangeProfile = TechniqueRangeProfile.CLOSE
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(close.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(close)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(close.techniqueId, "b"))
        until(sim) { it.alliedMembers.single().state == CombatantState.ATTACK_STARTUP }

        val snapshot = sim.snapshot()
        val distance = snapshot.alliedMembers.single().position.distanceTo(snapshot.opposingMembers.single().position)
        assertTrue("Close attack started outside close range: $distance", distance in 0.8f..2.2f)
    }

    @Test fun allFieldTechniqueStartsWithoutChangingPositionForRange() {
        val field = skill("field", range = 16f).copy(
            kind = TechniqueKind.AREA,
            rangeProfile = TechniqueRangeProfile.ALL_FIELD,
            impactShape = TechniqueImpactShape.ALL_OPPONENTS
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(field.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(field)
        )
        val opening = sim.snapshot().alliedMembers.single().position

        sim.issueOrder("a", TrainerAction.UseTechnique(field.techniqueId, "b"))
        until(sim) { it.alliedMembers.single().state == CombatantState.ATTACK_STARTUP }

        assertEquals(opening, sim.snapshot().alliedMembers.single().position)
    }

    @Test fun autonomousSelfAndAllFieldChoicesNeverRoamForRange() {
        val self = skill("self", power = 0, range = 0f).copy(
            kind = TechniqueKind.SUPPORT,
            minRange = 0f,
            rangeProfile = TechniqueRangeProfile.SELF,
            statusEffects = listOf(BattleStatusEffect("self_boost", 2_000L, attackMultiplier = 1.1f))
        )
        val field = skill("field", range = 16f).copy(
            kind = TechniqueKind.AREA,
            rangeProfile = TechniqueRangeProfile.ALL_FIELD,
            impactShape = TechniqueImpactShape.ALL_OPPONENTS
        )

        listOf(self, field).forEach { technique ->
            val sim = battle(
                allies = listOf(fighter("a", BattleSide.ALLIED, listOf(technique.techniqueId)).copy(
                    decisionDelayMinMillis = 1_800L,
                    decisionDelayMaxMillis = 1_800L
                )),
                enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(
                    movementSpeed = 0f,
                    decisionDelayMinMillis = 30_000L,
                    decisionDelayMaxMillis = 30_000L
                )),
                skills = listOf(technique)
            )
            val opening = sim.snapshot().alliedMembers.single().position

            until(sim, millis = 10_000L) { snapshot ->
                snapshot.recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
                    .any { it.combatantId == "a" }
            }

            assertEquals("${technique.rangeProfile} moved despite not needing range", opening,
                sim.snapshot().alliedMembers.single().position)
        }
    }

    @Test fun everyGenericTechniqueCanReachAndResolveItsDeclaredGeometry() {
        GenericTechniqueCatalog.battleDefinitions.filter { it.kind != TechniqueKind.SPECIAL && !it.reactionOnly }.forEach { technique ->
            val attacker = fighter("a", BattleSide.ALLIED, listOf(technique.techniqueId)).copy(
                maxEnergy = 1_000,
                decisionDelayMinMillis = 30_000L,
                decisionDelayMaxMillis = 30_000L
            )
            val sim = battle(
                allies = listOf(attacker),
                enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(
                    movementSpeed = 0f,
                    decisionDelayMinMillis = 30_000L,
                    decisionDelayMaxMillis = 30_000L
                )),
                skills = GenericTechniqueCatalog.battleDefinitions,
                config = BattleConfiguration(commandPoints = 100, defaultPaused = true)
            )
            val targetId = if (technique.rangeProfile == TechniqueRangeProfile.SELF) null else "b"

            sim.issueOrder("a", TrainerAction.UseTechnique(technique.techniqueId, targetId))
            sim.setPaused(false)
            until(sim, millis = 15_000L) { snapshot ->
                snapshot.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
                    .any { it.techniqueId == technique.techniqueId }
            }

            val hit = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
                .first { it.techniqueId == technique.techniqueId }
            if (technique.rangeProfile == TechniqueRangeProfile.SELF) assertEquals("a", hit.targetId)
            else assertEquals("b", hit.targetId)
        }
    }

    @Test fun combatantCooldownMultiplierChangesTheActualTechniqueCooldown() {
        val hit = skill("paced", power = 10).copy(cooldownMillis = 2_000L)
        fun remainingCooldown(multiplier: Float): Long {
            val attacker = fighter("a", BattleSide.ALLIED, listOf(hit.techniqueId)).copy(
                techniqueCooldownMultiplier = multiplier,
                decisionDelayMinMillis = 10_000L,
                decisionDelayMaxMillis = 10_000L
            )
            val simulator = battle(
                allies = listOf(attacker),
                enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
                skills = listOf(hit),
                config = BattleConfiguration(defaultPaused = true, randomSeed = 71L)
            )
            simulator.issueOrder("a", TrainerAction.UseTechnique(hit.techniqueId, "b"))
            simulator.setPaused(false)
            until(simulator) { it.alliedMembers.single().cooldownsMillis.containsKey(hit.techniqueId) }
            return simulator.snapshot().alliedMembers.single().cooldownsMillis.getValue(hit.techniqueId)
        }

        val fast = remainingCooldown(0.8f)
        val slow = remainingCooldown(1.2f)

        assertTrue("Expected a shorter cooldown for the faster profile: $fast vs $slow", fast < slow)
        assertTrue(kotlin.math.abs(fast - 1_600L) <= 34L)
        assertTrue(kotlin.math.abs(slow - 2_400L) <= 34L)
    }

    @Test fun physicalOrdersAreSerializedAndHaveOneTerminalStatusEach() {
        val hit = skill().copy(recoveryMillis = 680)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("hit"))), skills = listOf(hit))
        val first = sim.issueOrder("a", TrainerAction.UseTechnique("hit", "b"))
        val second = sim.issueOrder("a", TrainerAction.Defend(340))
        runFor(sim, 340)
        assertEquals(first.orderId, sim.snapshot().alliedMembers.single().currentOrderId)
        assertFalse(updates(sim, second.orderId).any { it.status == OrderStatus.EXECUTING })
        until(sim) { updates(sim, second.orderId).any { it.status == OrderStatus.COMPLETED } }
        for (id in listOf(first.orderId, second.orderId)) {
            assertEquals(1, updates(sim, id).count { it.status == OrderStatus.COMPLETED })
        }
    }

    @Test fun reservationPreventsTwoAlliesSpendingTheSameCommandPoints() {
        val costly = skill("costly").copy(commandPointCost = 20, energyCost = 40)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("costly")), fighter("a2", BattleSide.ALLIED, listOf("costly"))),
            skills = listOf(costly), config = BattleConfiguration(commandPoints = 25, defaultPaused = true))
        assertEquals(OrderStatus.QUEUED, sim.issueOrder("a", TrainerAction.UseTechnique("costly", "b")).status)
        assertEquals(OrderStatus.FAILED, sim.issueOrder("a2", TrainerAction.UseTechnique("costly", "b")).status)
        assertEquals(20, sim.snapshot().reservedCommandPoints)
        sim.setPaused(false)
        runFor(sim, 34)
        assertEquals(5, sim.snapshot().commandPoints)
        assertEquals(0, sim.snapshot().reservedCommandPoints)
        assertEquals(60, sim.snapshot().alliedMembers.first().energy)
    }

    @Test fun innateSpecialRequiresItsOwnFullGaugeInsteadOfCommandPointsOrEnergy() {
        val special = skill("innate", power = 120).copy(
            kind = TechniqueKind.SPECIAL,
            energyCost = 0,
            commandPointCost = 0
        )
        val attacker = fighter("a", BattleSide.ALLIED)
            .copy(maxEnergy = 0, specialTechniqueId = special.techniqueId)
        val sim = battle(
            allies = listOf(attacker),
            skills = listOf(special),
            config = BattleConfiguration(commandPoints = 0, defaultPaused = true)
        )

        val update = sim.issueOrder("a", TrainerAction.UseTechnique(special.techniqueId, "b"))

        assertEquals(OrderStatus.FAILED, update.status)
        assertEquals(0, sim.snapshot().alliedMembers.single().specialCharge)
        assertTrue(update.reason.orEmpty().contains("especial", ignoreCase = true))
    }

    @Test fun landingATechniqueChargesTheAttackerFasterThanTheFighterThatWasHit() {
        val strike = skill("charge_hit", power = 10).copy(criticalChance = 0f)
        val special = skill("innate_charge_target", power = 120).copy(kind = TechniqueKind.SPECIAL)
        val attacker = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId)).copy(
            specialTechniqueId = special.techniqueId,
            decisionDelayMinMillis = 10_000L,
            decisionDelayMaxMillis = 10_000L
        )
        val defender = fighter("b", BattleSide.OPPOSING).copy(
            specialTechniqueId = special.techniqueId,
            movementSpeed = 0f,
            decisionDelayMinMillis = 10_000L,
            decisionDelayMaxMillis = 10_000L
        )
        val sim = battle(
            allies = listOf(attacker),
            enemies = listOf(defender),
            skills = listOf(strike, special),
            config = BattleConfiguration(defaultPaused = true)
        )
        sim.issueOrder("a", TrainerAction.UseTechnique(strike.techniqueId, "b"))
        sim.setPaused(false)

        until(sim) { it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().any { hit -> hit.techniqueId == strike.techniqueId } }

        val snapshot = sim.snapshot()
        val attackerCharge = snapshot.alliedMembers.single().specialCharge
        val defenderCharge = snapshot.opposingMembers.single().specialCharge
        assertTrue("Attacking should charge the special gauge", attackerCharge > 0)
        assertTrue("Attacking should charge faster than taking a hit: $attackerCharge <= $defenderCharge",
            attackerCharge > defenderCharge)
    }

    @Test fun fullSpecialClaimsItsRangeBeforeConsumingChargeAndSignalsItsResolution() {
        val charger = skill("charger", power = 1, range = 20f).copy(
            rangeProfile = TechniqueRangeProfile.ALL_FIELD,
            cooldownMillis = 0L,
            criticalChance = 0f
        )
        val special = skill("innate", power = 120, range = 1.45f).copy(
            kind = TechniqueKind.SPECIAL,
            minRange = 0.8f,
            maxRange = 1.45f,
            rangeProfile = TechniqueRangeProfile.CLOSE,
            energyCost = 0,
            commandPointCost = 0,
            startupMillis = 340L,
            criticalChance = 0f
        )
        val attacker = fighter("a", BattleSide.ALLIED, listOf(charger.techniqueId)).copy(
            specialTechniqueId = special.techniqueId,
            movementSpeed = 1f,
            decisionDelayMinMillis = 10_000L,
            decisionDelayMaxMillis = 10_000L
        )
        val defender = fighter("b", BattleSide.OPPOSING).copy(
            maxHealth = 100_000,
            movementSpeed = 0f,
            decisionDelayMinMillis = 10_000L,
            decisionDelayMaxMillis = 10_000L
        )
        val sim = battle(
            allies = listOf(attacker),
            enemies = listOf(defender),
            skills = listOf(charger, special),
            config = BattleConfiguration(defaultPaused = true, maxDurationMillis = 60_000L)
        )
        repeat(8) {
            val order = sim.issueOrder("a", TrainerAction.UseTechnique(charger.techniqueId, "b"))
            assertEquals(OrderStatus.QUEUED, order.status)
            sim.setPaused(false)
            until(sim) { updates(sim, order.orderId).any { update -> update.status == OrderStatus.COMPLETED } }
            sim.setPaused(true)
            if (sim.snapshot().alliedMembers.single().specialCharge == 100) return@repeat
        }
        val charged = sim.snapshot().alliedMembers.single()
        assertEquals(charged.maxSpecialCharge, charged.specialCharge)

        val order = sim.issueOrder("a", TrainerAction.UseTechnique(special.techniqueId, "b"))
        assertEquals(OrderStatus.QUEUED, order.status)
        assertEquals(charged.maxSpecialCharge, sim.snapshot().alliedMembers.single().reservedSpecialCharge)
        sim.setPaused(false)
        sim.advance(34L)

        val positioning = sim.snapshot().alliedMembers.single()
        assertTrue(positioning.state == CombatantState.MOVE_TO_TARGET || positioning.state == CombatantState.POSITIONING)
        assertEquals(positioning.maxSpecialCharge, positioning.specialCharge)

        until(sim, 10_000L) { it.alliedMembers.single().state == CombatantState.USING_SPECIAL }
        assertEquals(0, sim.snapshot().alliedMembers.single().specialCharge)
        assertEquals(TechniqueKind.SPECIAL, sim.snapshot().alliedMembers.single().activeTechniqueKind)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.SpecialStarted>()
            .any { it.combatantId == "a" && it.techniqueId == special.techniqueId })

        until(sim) { it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>().any { result -> result.success } }
        val resolved = sim.snapshot()
        assertTrue(resolved.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
            .any { it.combatantId == "a" && it.techniqueId == special.techniqueId && it.success })
        assertTrue(resolved.impacts.any { it.isSpecial && it.techniqueId == special.techniqueId })
    }

    @Test fun expiryReleasesResourcesEvenWhilePartnerIsDefending() {
        val hit = skill().copy(energyCost = 80, commandPointCost = 20)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("hit"))), skills = listOf(hit),
            config = BattleConfiguration(commandPoints = 30))
        sim.issueOrder("a", TrainerAction.Defend(2000))
        val waiting = sim.issueOrder("a", TrainerAction.UseTechnique("hit", "b"), lifetimeMillis = 200)
        assertEquals(80, sim.snapshot().alliedMembers.single().reservedEnergy)
        runFor(sim, 238)
        assertEquals(OrderStatus.EXPIRED, updates(sim, waiting.orderId).last().status)
        assertEquals(0, sim.snapshot().reservedCommandPoints)
        assertEquals(0, sim.snapshot().alliedMembers.single().reservedEnergy)
        assertEquals(100, sim.snapshot().alliedMembers.single().energy)
    }

    @Test fun queuedSkillCannotBeOverwrittenWhileApproaching() {
        val melee = skill("short", range = 1.5f)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("short")).copy(movementSpeed = 0f)), skills = listOf(melee))
        val first = sim.issueOrder("a", TrainerAction.UseTechnique("short", "b"), lifetimeMillis = 500)
        val second = sim.issueOrder("a", TrainerAction.Defend(340))
        runFor(sim, 340)
        assertEquals(first.orderId, sim.snapshot().alliedMembers.single().currentOrderId)
        until(sim) { updates(sim, second.orderId).any { it.status == OrderStatus.COMPLETED } }
        assertEquals(OrderStatus.EXPIRED, updates(sim, first.orderId).last().status)
    }

    @Test fun supportIsRejectedWhilePausedAndWindowRemainsAvailable() {
        val hit = skill()
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("hit"))), skills = listOf(hit))
        until(sim) { "a" in it.pendingSupportCombatantIds }
        sim.setPaused(true)
        assertEquals(OrderStatus.FAILED, sim.issueOrder("a", TrainerAction.Support).status)
        runFor(sim, 3000)
        sim.setPaused(false)
        assertEquals(OrderStatus.COMPLETED, sim.issueOrder("a", TrainerAction.Support).status)
        assertEquals(8, sim.snapshot().commandPoints)
    }

    @Test fun enemyAutomationCannotSpendTrainersCommandPoints() {
        val costly = skill("costly", power = 300).copy(commandPointCost = 10)
        val sim = battle(enemies = listOf(fighter("b", BattleSide.OPPOSING, listOf("costly"))),
            skills = listOf(costly), config = BattleConfiguration(commandPoints = 50))
        runFor(sim, 5000)
        assertEquals(50, sim.snapshot().commandPoints)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>().none { it.techniqueId == "costly" })
    }

    @Test fun stunFreezesEvenAnUninterruptibleStartup() {
        val slow = skill("slow", power = 500).copy(startupMillis = 1000, interruptibleDuringStartup = false)
        val stun = skill("stun", power = 1).copy(cooldownMillis = 10_000,
            statusEffects = listOf(BattleStatusEffect("stun", 1000, preventsActions = true)))
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("slow"))),
            listOf(fighter("b", BattleSide.OPPOSING, listOf("stun"))), listOf(slow, stun))
        until(sim) { it.alliedMembers.single().state == CombatantState.STUNNED }
        runFor(sim, 680)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().none { it.combatantId == "a" })
        until(sim) { it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().any { hit -> hit.combatantId == "a" } }
    }

    @Test fun damageOverTimeDefeatsOnceAndCancelsOutstandingOrders() {
        val poison = skill("poison", power = 0).copy(statusEffects = listOf(BattleStatusEffect("poison", 1000, damagePerSecond = 3000f)))
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("poison"))), skills = listOf(poison))
        sim.issueOrder("a", TrainerAction.UseTechnique("poison", "b"))
        val defend = sim.issueOrder("a", TrainerAction.Defend(5000))
        until(sim) { it.result != null }
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.CombatantDefeated>().count { it.combatantId == "b" })
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.StateChanged>().count { it.combatantId == "b" && it.state == CombatantState.DEFEATED })
        assertEquals(OrderStatus.CANCELLED, updates(sim, defend.orderId).last().status)
    }

    @Test fun threatCanPullEnemyOffItsOriginalTarget() {
        val weak = skill("weak", 2).copy(cooldownMillis = 5000)
        val strong = skill("strong", 200).copy(startupMillis = 340, cooldownMillis = 5000)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED, listOf("weak")),
            fighter("a2", BattleSide.ALLIED, listOf("strong"))),
            listOf(fighter("b", BattleSide.OPPOSING, listOf("weak")).copy(maxHealth = 10000)), listOf(weak, strong))
        until(sim) { it.opposingMembers.single().targetId != null }
        assertEquals("a", sim.snapshot().opposingMembers.single().targetId)
        until(sim, 10_000) { it.opposingMembers.single().targetId == "a2" }
    }

    @Test fun invalidCostsAndTeamsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) { battle(skills = listOf(skill().copy(energyCost = -1))) }
        assertThrows(IllegalArgumentException::class.java) { battle(skills = listOf(skill().copy(maxRange = Float.NaN))) }
        assertThrows(IllegalArgumentException::class.java) { battle(allies = listOf(fighter("a", BattleSide.OPPOSING))) }
        assertThrows(IllegalArgumentException::class.java) { battle(config = BattleConfiguration(maxCommandPoints = -1)) }
    }

    @Test fun oneDefeatedPartnerDoesNotEndTheWholeTeamBattle() {
        val lethal = skill(power = 100).copy(cooldownMillis = 5000)
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED).copy(maxHealth = 1),
            fighter("a2", BattleSide.ALLIED).copy(maxHealth = 5000)),
            listOf(fighter("b", BattleSide.OPPOSING, listOf("hit"))), listOf(lethal))
        until(sim) { it.alliedMembers.first().health == 0 }
        assertNull(sim.snapshot().result)
        assertTrue(sim.snapshot().alliedMembers.last().health > 0)
    }

    @Test fun sameSeedAndOrdersProduceIdenticalResults() {
        val first = battle()
        val second = battle()
        first.issueOrder("a", TrainerAction.Defend(1000))
        second.issueOrder("a", TrainerAction.Defend(1000))
        repeat(2000) { first.advance(34); second.advance(34) }
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test fun fastProjectileUsesSweptCollisionAndResolvesItsHit() {
        val bolt = skill("bolt", power = 200).copy(
            kind = TechniqueKind.PROJECTILE,
            minRange = 0f,
            maxRange = 20f,
            startupMillis = 34,
            activeMillis = 34,
            recoveryMillis = 34,
            projectileSpeed = 120f,
            projectileRadius = 0.1f,
            projectileLifetimeMillis = 1_000,
            attackVisual = "large"
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf("bolt"))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING)),
            skills = listOf(bolt)
        )
        sim.issueOrder("a", TrainerAction.UseTechnique("bolt", "b"))
        until(sim, 5_000) { it.statistics.projectilesHit == 1 }
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.ProjectileLaunched>()
            .any { it.techniqueId == "bolt" })
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .any { it.combatantId == "a" && it.targetId == "b" })
        assertTrue(sim.snapshot().opposingMembers.single().health < 1_000)
        assertEquals(0, sim.snapshot().projectiles.size)
        assertEquals("b", sim.snapshot().impacts.single().targetId)
        val hit = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().last { it.techniqueId == "bolt" }
        assertEquals(hit.amount, sim.snapshot().impacts.single().damage)
    }

    @Test fun regularProjectileUsesNormalSpriteEvenWhenTechniqueMetadataMarksItLarge() {
        val bolt = skill("regular_bolt").copy(
            kind = TechniqueKind.PROJECTILE,
            startupMillis = 34,
            activeMillis = 340,
            projectileSpeed = 1f,
            projectileLifetimeMillis = 1_000,
            attackVisual = "large"
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(bolt.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(bolt),
            config = BattleConfiguration(defaultPaused = true)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(bolt.techniqueId, "b"))
        sim.setPaused(false)
        until(sim) { it.projectiles.isNotEmpty() }

        assertEquals("small", sim.snapshot().projectiles.single().visual)
    }

    @Test fun confirmedHitEffectRemainsVisibleLongEnoughToReadInTheArena() {
        val strike = skill("strike", power = 20)
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))),
            enemies = listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)),
            skills = listOf(strike),
            config = BattleConfiguration(defaultPaused = true)
        )
        sim.issueOrder("a", TrainerAction.UseTechnique(strike.techniqueId, "b"))
        sim.setPaused(false)
        until(sim) { it.impacts.isNotEmpty() }

        runFor(sim, 408L)
        assertTrue("Confirmed-hit VFX disappeared too quickly", sim.snapshot().impacts.isNotEmpty())

        runFor(sim, 340L)
        assertTrue("Confirmed-hit VFX did not expire", sim.snapshot().impacts.isEmpty())
    }

    @Test fun trainingItemReservationDoesNotSpendAnItemWhenItHasNoEffect() {
        val input = TrainingParticipantInput("a", displayName = "A", stage = 2, maxHealth = 1_000, attack = 1)
        val sim = TrainingBattleFactory.create(listOf(input), listOf(input.copy(instanceId = "b")))
        val update = sim.issueOrder("ally:a", TrainerAction.UseItem("training_recovery", "ally:a"))
        assertEquals(OrderStatus.QUEUED, update.status)
        assertEquals(1, sim.snapshot().trainingItems.first { it.itemId == "training_recovery" }.reserved)
        sim.advance(34)
        val item = sim.snapshot().trainingItems.first { it.itemId == "training_recovery" }
        assertEquals(2, item.remaining)
        assertEquals(0, item.reserved)
        assertEquals(OrderStatus.FAILED, updates(sim, update.orderId).last().status)
        assertEquals(0, sim.snapshot().statistics.itemsUsed)
    }

    @Test fun unreachableOpponentsEventuallyEndAsDraw() {
        val sim = battle(listOf(fighter("a", BattleSide.ALLIED).copy(movementSpeed = 0f)),
            listOf(fighter("b", BattleSide.OPPOSING).copy(movementSpeed = 0f)), config = BattleConfiguration(maxDurationMillis = 2000))
        until(sim, 2100) { it.result != null }
        assertEquals(BattleOutcome.DRAW, sim.snapshot().result!!.outcome)
    }

    @Test fun trainingFactoryRejectsDuplicateStoredPartnerAndUnsupportedFormat() {
        val input = TrainingParticipantInput("one", sourceCharacterId = 42, displayName = "One", stage = 2, maxHealth = 1000, attack = 100)
        val clone = input.copy(instanceId = "two")
        assertThrows(IllegalArgumentException::class.java) { TrainingBattleFactory.create(listOf(input, clone), listOf(input, clone)) }
        assertThrows(IllegalArgumentException::class.java) { TrainingBattleFactory.create(listOf(input, clone.copy(sourceCharacterId = 43)), listOf(input)) }
    }

    @Test fun allOpponentShapeHitsEveryLivingEnemyFromAnyValidFieldPosition() {
        val field = skill("field", power = 25, range = 16f).copy(
            kind = TechniqueKind.AREA,
            rangeProfile = TechniqueRangeProfile.ALL_FIELD,
            impactShape = TechniqueImpactShape.ALL_OPPONENTS
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(field.techniqueId))),
            enemies = listOf(fighter("b1", BattleSide.OPPOSING), fighter("b2", BattleSide.OPPOSING)),
            skills = listOf(field)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(field.techniqueId, "b1"))
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
                .count { hit -> hit.techniqueId == field.techniqueId } == 2
        }

        assertTrue(sim.snapshot().opposingMembers.all { it.health < it.maxHealth })
    }

    @Test fun multiHitTechniqueResolvesEveryConfiguredHit() {
        val combo = skill("combo", power = 5).copy(hitCount = 3)
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(combo.techniqueId))),
            skills = listOf(combo)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(combo.techniqueId, "b"))
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
                .count { hit -> hit.techniqueId == combo.techniqueId } == 3
        }

        val hits = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .filter { it.techniqueId == combo.techniqueId }
        assertEquals(3, hits.size)
        assertEquals(1_000 - hits.sumOf { it.amount }, sim.snapshot().opposingMembers.single().health)
    }

    @Test fun selfRangeTechniqueDoesNotNeedOrAffectAnOpponentTarget() {
        val boost = skill("boost", power = 0).copy(
            kind = TechniqueKind.SUPPORT,
            minRange = 0f,
            maxRange = 0f,
            rangeProfile = TechniqueRangeProfile.SELF,
            statusEffects = listOf(BattleStatusEffect("boosted", 2_000L, attackMultiplier = 1.2f))
        )
        val sim = battle(
            allies = listOf(fighter("a", BattleSide.ALLIED, listOf(boost.techniqueId))),
            skills = listOf(boost)
        )

        sim.issueOrder("a", TrainerAction.UseTechnique(boost.techniqueId))
        until(sim) { snapshot -> snapshot.alliedMembers.single().statuses.any { it.id == "boosted" } }

        val hit = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .single { it.techniqueId == boost.techniqueId }
        assertEquals("a", hit.targetId)
        assertEquals(1_000, sim.snapshot().opposingMembers.single().health)
    }
}
