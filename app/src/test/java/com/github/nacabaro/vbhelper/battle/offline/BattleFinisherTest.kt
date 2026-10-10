package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class BattleFinisherTest {
    private val special = TechniqueDefinition("special", "Base Special", TechniqueKind.SPECIAL, 100,
        energyCost = 30, commandPointCost = 5, maxRange = 30f, startupMillis = 340,
        activeMillis = 68, recoveryMillis = 204, cooldownMillis = 680, criticalChance = 0f)
    private val slow = TechniqueDefinition("slow", "Suspended attack", TechniqueKind.MELEE, 10,
        maxRange = 30f, startupMillis = 2_000, activeMillis = 68, recoveryMillis = 204,
        cooldownMillis = 680, criticalChance = 0f)
    private val exhaust = TechniqueDefinition("exhaust", "Exhaust readiness", TechniqueKind.MELEE, 0,
        maxRange = 30f, startupMillis = 34, activeMillis = 34, recoveryMillis = 34,
        readinessCost = 255f, criticalChance = 0f)

    private fun fighter(id: String, side: BattleSide) = CombatantDefinition(id, displayName = id,
        side = side, maxHealth = 100_000, maxEnergy = 500, attack = 100, defense = 0,
        movementSpeed = 0f, techniqueIds = listOf("slow"), specialTechniqueId = "special",
        decisionDelayMinMillis = 150_000, decisionDelayMaxMillis = 150_000)

    private fun simulator(partner: Boolean = false, kill: Boolean = false, commandPoints: Int = 100,
        configurePartner: (CombatantDefinition) -> CombatantDefinition = { it },
        lead: (CombatantDefinition) -> CombatantDefinition = { it }): BattleSimulator = BattleSimulator(
        BattleConfiguration(commandPoints = commandPoints, randomSeed = 903),
        BattleTeam("allies", BattleSide.ALLIED, buildList {
            add(lead(fighter("a", BattleSide.ALLIED)))
            if (partner) add(configurePartner(fighter("p", BattleSide.ALLIED)))
        }),
        BattleTeam("enemies", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING)
            .copy(initialHealth = if (kill) 1 else null))), listOf(special, slow, exhaust))

    private fun advance(sim: BattleSimulator, millis: Long, frame: Long = 34) {
        var remaining = millis
        while (remaining > 0) {
            val delta = minOf(frame, remaining)
            sim.advance(delta)
            remaining -= delta
        }
    }

    private fun until(sim: BattleSimulator, condition: (BattleSnapshot) -> Boolean) {
        repeat(4_000) {
            if (condition(sim.snapshot())) return
            sim.advance(34)
        }
        assertTrue("Condition not reached", condition(sim.snapshot()))
    }

    private fun charge(sim: BattleSimulator) = until(sim) { it.alliedMembers.all { member -> member.specialCharge == 100 } }

    private fun open(sim: BattleSimulator, id: String = "a"): Long {
        val order = sim.issueOrder(id, TrainerAction.UseTechnique("special", "b"))
        assertEquals(OrderStatus.QUEUED, order.status)
        until(sim) { id in it.pendingBlastTiming }
        return order.orderId
    }

    private fun confirm(sim: BattleSimulator, fusion: String? = null) {
        assertEquals(OrderStatus.COMPLETED,
            sim.issueOrder("a", TrainerAction.ConfirmBlastTiming(fusion, "Fusion Special")).status)
        assertNotNull(sim.snapshot().finisher)
    }

    private fun formSimulator(kill: Boolean = false) = simulator(kill = kill) {
        it.copy(blastMode = "FORM", blastTargetSpecies = "WarGreymon X", blastFormSpecial = "Gaia Force")
    }

    @Test fun authoredTimelineHasExactBoundariesAndVisibility() {
        assertEquals(6_500L, BattleFinisherTimeline.durationMillis(BattleFinisherKind.FORM))
        assertEquals(7_000L, BattleFinisherTimeline.durationMillis(BattleFinisherKind.JOGRESS))
        val start = BattleFinisherTimeline.snapshot(sequenceId = 4, kind = BattleFinisherKind.FORM,
            leadId = "a", partnerId = "p", elapsedMillis = -1)
        assertEquals(listOf("a"), start.participantIds)
        assertEquals(0L, start.elapsedMillis)
        assertFalse(start.resultVisible)
        val durations = listOf(250L, 1100L, 800L, 1200L, 600L, 800L, 1100L, 650L)
        var boundary = 0L
        BattleFinisherPhase.entries.forEachIndexed { index, phase ->
            val beat = BattleFinisherTimeline.sample(start, boundary)
            assertEquals(phase, beat.phase)
            assertEquals(0f, beat.phaseProgress, 0f)
            assertEquals(phase in BattleFinisherPhase.REVEAL..BattleFinisherPhase.AFTERMATH, beat.resultVisible)
            val midpoint = BattleFinisherTimeline.sample(start, boundary + durations[index] / 2)
            assertEquals(0.5f, midpoint.phaseProgress, 0.002f)
            boundary += durations[index]
        }
        val end = BattleFinisherTimeline.sample(start, 99_000)
        assertEquals(BattleFinisherTimeline.durationMillis(BattleFinisherKind.FORM), end.elapsedMillis)
        assertEquals(BattleFinisherPhase.RESTORE, end.phase)
        assertEquals(1f, end.phaseProgress, 0f)
        assertEquals(listOf("a", "p"), start.copy(kind = BattleFinisherKind.JOGRESS).participantIds)
        assertEquals(listOf("a", "p"), start.copy(kind = BattleFinisherKind.DUO).participantIds)
        assertEquals(listOf("a"), start.copy(kind = BattleFinisherKind.POWER).participantIds)
        assertEquals(450L, BattleFinisherTimeline.phaseDurationMillis(BattleFinisherKind.POWER, BattleFinisherPhase.TRANSFORM))
        assertEquals(650L, BattleFinisherTimeline.phaseDurationMillis(BattleFinisherKind.DUO, BattleFinisherPhase.TRANSFORM))
        assertEquals(300L, BattleFinisherTimeline.phaseDurationMillis(BattleFinisherKind.DUO, BattleFinisherPhase.REVEAL))
    }

    @Test fun launchConsumesWindowButNeverResolvesOrdinaryStartupBeforeImpact() {
        val sim = formSimulator()
        charge(sim)
        val orderId = open(sim)
        val startup = sim.snapshot()
        assertEquals(470, startup.alliedMembers.single().energy)
        assertEquals(95, startup.commandPoints)
        confirm(sim)
        val launched = sim.snapshot()
        assertEquals(BattleFinisherPhase.FOCUS, launched.finisher!!.phase)
        assertEquals(0L, launched.finisher!!.elapsedMillis)
        assertTrue(launched.pendingBlastTiming.isEmpty())
        assertEquals(startup.alliedMembers.single().energy, launched.alliedMembers.single().energy)
        assertEquals(startup.commandPoints, launched.commandPoints)
        assertEquals(0, launched.alliedMembers.single().specialCharge)
        assertEquals(1, launched.statistics.specialsUsed)
        assertEquals(OrderFailure.BLAST_NO_WINDOW,
            sim.issueOrder("a", TrainerAction.ConfirmBlastTiming()).reasonCode)
        val impactAt = BattleFinisherTimeline.phaseStartMillis(BattleFinisherKind.FORM, BattleFinisherPhase.IMPACT)
        advance(sim, impactAt - 1)
        assertEquals(startup.opposingMembers.single().health, sim.snapshot().opposingMembers.single().health)
        assertEquals(startup.elapsedMillis, sim.snapshot().elapsedMillis)
        assertFalse(sim.snapshot().finisher!!.impactCommitted)
        assertEquals(0, sim.snapshot().finisher!!.damage)
        sim.advance(1)
        val impact = sim.snapshot()
        assertTrue(impact.finisher!!.impactCommitted)
        assertEquals(startup.opposingMembers.single().health - impact.opposingMembers.single().health,
            impact.finisher!!.damage)
        assertEquals(1, impact.statistics.specialsHit)
        assertEquals(1, impact.statistics.blastTimedHits)
        assertEquals(0, impact.alliedMembers.single().specialCharge)
        assertEquals(orderId, impact.alliedMembers.single().currentOrderId)
        val healthAfterImpact = impact.opposingMembers.single().health
        advance(sim, BattleFinisherTimeline.durationMillis(BattleFinisherKind.FORM) - impactAt - 1)
        assertEquals(healthAfterImpact, sim.snapshot().opposingMembers.single().health)
        assertEquals("WarGreymon X", sim.snapshot().alliedMembers.single().blastFormSpecies)
        assertEquals(BattleFinisherPhase.RESTORE, sim.snapshot().finisher!!.phase)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastFormEnded>().isEmpty())
        sim.advance(1)
        val restored = sim.snapshot()
        assertNull(restored.finisher)
        assertNull(restored.alliedMembers.single().blastFormSpecies)
        assertNull(restored.alliedMembers.single().activeTechniqueId)
        assertEquals(0, restored.alliedMembers.single().specialCharge)
        assertEquals(470, restored.alliedMembers.single().energy)
        assertEquals(95, restored.commandPoints)
        assertEquals(1, restored.recentEvents.filterIsInstance<BattleEvent.BlastFormStarted>().size)
        assertEquals(1, restored.recentEvents.filterIsInstance<BattleEvent.BlastFormEnded>().size)
        assertEquals(1, restored.recentEvents.filterIsInstance<BattleEvent.OrderChanged>()
            .count { it.update.orderId == orderId && it.update.status == OrderStatus.COMPLETED })
        advance(sim, 1_020)
        assertEquals(healthAfterImpact, sim.snapshot().opposingMembers.single().health)
        assertEquals(1, sim.snapshot().statistics.specialsHit)
    }

    @Test fun allOrdinaryTimersProjectilesDotAndReservationsAreFrozen() {
        val poison = TechniqueDefinition("poison", "Poison", TechniqueKind.MELEE, 1, maxRange = 30f,
            startupMillis = 34, activeMillis = 34, recoveryMillis = 34, criticalChance = 0f,
            statusEffects = listOf(BattleStatusEffect("poison", 20_000, damagePerSecond = 100f)))
        val projectile = TechniqueDefinition("projectile", "Projectile", TechniqueKind.PROJECTILE, 10,
            maxRange = 30f, startupMillis = 34, activeMillis = 34, recoveryMillis = 34,
            projectileSpeed = 0.01f, projectileLifetimeMillis = 20_000, criticalChance = 0f)
        val sim = BattleSimulator(BattleConfiguration(commandPoints = 100),
            BattleTeam("allies", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED),
                fighter("p", BattleSide.ALLIED).copy(techniqueIds = listOf("poison", "projectile", "slow"),
                    specialTechniqueId = null, initialEnergy = 100))),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING))),
            listOf(special, slow, poison, projectile))
        until(sim) { it.alliedMembers.first().specialCharge == 100 }
        sim.issueOrder("p", TrainerAction.UseTechnique("poison", "b"))
        until(sim) { it.opposingMembers.single().statuses.isNotEmpty() }
        sim.issueOrder("p", TrainerAction.UseTechnique("projectile", "b"))
        until(sim) { it.projectiles.isNotEmpty() }
        open(sim)
        val queued = sim.issueOrder("p", TrainerAction.Defend(), lifetimeMillis = 100)
        confirm(sim)
        val frozen = sim.snapshot()
        advance(sim, BattleFinisherTimeline.phaseStartMillis(frozen.finisher!!.kind, BattleFinisherPhase.IMPACT) - 1)
        val later = sim.snapshot()
        assertEquals(frozen.elapsedMillis, later.elapsedMillis)
        assertEquals(frozen.projectiles, later.projectiles)
        assertEquals(frozen.alliedMembers, later.alliedMembers)
        assertEquals(frozen.opposingMembers, later.opposingMembers)
        assertEquals(frozen.statistics, later.statistics)
        assertTrue(later.alliedMembers.last().queuedOrderIds.contains(queued.orderId))
    }

    @Test fun jogressSuspendsPartnersActiveActionAndQueueWithoutSpendingItsCharge() {
        val sim = simulator(partner = true) { it.copy(jogressResultSpecies = "Equipped Lead Result") }
        charge(sim)
        sim.issueOrder("p", TrainerAction.UseTechnique("slow", "b"))
        until(sim) { it.alliedMembers.last().activeTechniqueId == "slow" }
        val queued = sim.issueOrder("p", TrainerAction.Defend())
        open(sim)
        val before = sim.snapshot().alliedMembers.last()
        confirm(sim, "Other Result")
        val finisher = sim.snapshot().finisher!!
        assertEquals(BattleFinisherKind.JOGRESS, finisher.kind)
        assertEquals("Equipped Lead Result", finisher.resultSpecies)
        assertEquals("Fusion Special", finisher.specialName)
        assertEquals(listOf("a", "p"), finisher.participantIds)
        advance(sim, finisher.durationMillis - 1)
        assertEquals(before, sim.snapshot().alliedMembers.last())
        assertEquals(1, sim.snapshot().statistics.specialsUsed)
        assertEquals(1, sim.snapshot().statistics.specialsHit)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().none { it.combatantId == "p" })
        assertFalse(sim.snapshot().finisher!!.resultVisible)
        sim.advance(1)
        assertNull(sim.snapshot().finisher)
        assertEquals(before, sim.snapshot().alliedMembers.last())
        assertEquals(listOf(queued.orderId), sim.snapshot().alliedMembers.last().queuedOrderIds)
        until(sim) { it.recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().any { hit -> hit.combatantId == "p" } }
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().count { it.combatantId == "p" })
    }

    @Test fun killingBlowPublishesResultOnlyAfterRestoration() {
        val sim = formSimulator(kill = true)
        charge(sim)
        open(sim)
        confirm(sim)
        advance(sim, 5_000)
        assertEquals(0, sim.snapshot().opposingMembers.single().health)
        assertNull(sim.snapshot().result)
        assertTrue(sim.snapshot().finisher!!.resultVisible)
        advance(sim, BattleFinisherTimeline.durationMillis(BattleFinisherKind.FORM) - 5_000 - 1)
        assertNull(sim.snapshot().result)
        sim.advance(1)
        val ended = sim.snapshot()
        assertNull(ended.finisher)
        assertNull(ended.alliedMembers.single().blastFormSpecies)
        assertEquals(BattleOutcome.ALLIED_VICTORY, ended.result!!.outcome)
        val events = ended.recentEvents
        assertTrue(events.indexOfFirst { it is BattleEvent.BlastFormEnded } < events.indexOfFirst { it is BattleEvent.BattleEnded })
        assertEquals(1, ended.statistics.specialsHit)
    }

    @Test fun completedCinematicDoesNotReplayItsImpactWhenOrdinaryCombatResumes() {
        val sim = formSimulator()
        charge(sim)
        open(sim)
        confirm(sim)
        advance(sim, 5_000)
        assertTrue(sim.snapshot().impacts.any { it.isSpecial })
        advance(sim, BattleFinisherTimeline.durationMillis(BattleFinisherKind.FORM) - 5_000)
        assertNull(sim.snapshot().finisher)
        assertTrue("Cinematic impact already had its aftermath and must not flash again", sim.snapshot().impacts.isEmpty())
    }

    @Test fun externalPauseFreezesCinematicAndDoesNotAccumulateResumeTime() {
        val sim = formSimulator()
        charge(sim)
        open(sim)
        sim.setPaused(true, "blast-menu")
        confirm(sim)
        val initial = sim.snapshot().finisher
        advance(sim, 1_000)
        assertEquals(initial, sim.snapshot().finisher)
        sim.setPaused(false)
        advance(sim, 1_000)
        sim.setPaused(true, "background")
        val before = sim.snapshot()
        advance(sim, 60_000)
        assertEquals(before, sim.snapshot())
        sim.setPaused(false)
        sim.advance(100)
        assertEquals(1_100L, sim.snapshot().finisher!!.elapsedMillis)
    }

    @Test fun cancelAndAbandonAreIdempotentAndNeverRefundOrRepeatResolution() {
        listOf(false, true).forEach { afterImpact ->
            val sim = formSimulator()
            charge(sim)
            open(sim)
            confirm(sim)
            if (afterImpact) advance(sim, 5_000)
            sim.cancelFinisher()
            val cancelled = sim.snapshot()
            sim.cancelFinisher()
            assertEquals(cancelled, sim.snapshot())
            assertNull(cancelled.finisher)
            assertNull(cancelled.alliedMembers.single().blastFormSpecies)
            assertEquals(0, cancelled.alliedMembers.single().specialCharge)
            assertEquals(1, cancelled.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>().size)
            assertEquals(1, cancelled.recentEvents.filterIsInstance<BattleEvent.BlastFormEnded>().size)
            val abandoned = sim.abandon()
            assertEquals(abandoned, sim.abandon())
            assertEquals(BattleOutcome.ABANDONED, abandoned.outcome)
        }
        val fused = simulator(partner = true)
        charge(fused)
        open(fused)
        confirm(fused, "Omegamon")
        fused.abandon()
        assertNull(fused.snapshot().finisher)
        assertEquals(1, fused.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastJogressEnded>().size)
        assertTrue(fused.snapshot().alliedMembers.all { it.blastFormSpecies == null && it.reservedSpecialCharge == 0 })
    }

    @Test fun completedSequenceAllowsFreshWindowAndHigherSequenceId() {
        val sim = simulator(partner = true)
        charge(sim)
        open(sim)
        confirm(sim)
        val firstId = sim.snapshot().finisher!!.sequenceId
        advance(sim, sim.snapshot().finisher!!.durationMillis)
        charge(sim)
        open(sim, "p")
        assertEquals(OrderStatus.COMPLETED, sim.issueOrder("p", TrainerAction.ConfirmBlastTiming()).status)
        val second = sim.snapshot().finisher!!
        assertTrue(second.sequenceId > firstId)
        assertEquals("p", second.leadId)
        advance(sim, second.durationMillis)
        assertEquals(4, sim.snapshot().statistics.specialsUsed)
        assertEquals(4, sim.snapshot().statistics.specialsHit)
        assertTrue(sim.snapshot().pendingBlastTiming.isEmpty())
    }

    @Test fun duoCannotSpendResourcesReservedForAPartnersQueuedOrder() {
        val sim = simulator(partner = true)
        charge(sim)
        open(sim)
        val queued = sim.issueOrder("p", TrainerAction.UseTechnique("special", "b"))
        val partner = sim.snapshot().alliedMembers.last()
        assertEquals(100, partner.reservedSpecialCharge)
        confirm(sim)
        assertEquals(BattleFinisherKind.DUO, sim.snapshot().finisher!!.kind)
        advance(sim, sim.snapshot().finisher!!.durationMillis)
        assertEquals(partner, sim.snapshot().alliedMembers.last())
        assertEquals(listOf(queued.orderId), sim.snapshot().alliedMembers.last().queuedOrderIds)
        assertEquals(1, sim.snapshot().statistics.specialsUsed)
        assertEquals(1, sim.snapshot().statistics.specialsHit)
    }

    @Test fun duoPresentationDoesNotGrantMissingEnergyCommandPointsOrReadiness() {
        listOf("energy", "command", "readiness").forEach { missing ->
            val sim = simulator(partner = true, commandPoints = if (missing == "command") 5 else 100,
                configurePartner = { partner -> when (missing) {
                    "energy" -> partner.copy(initialEnergy = 0, energyRegenerationPerSecond = 0f)
                    "readiness" -> partner.copy(techniqueIds = listOf("exhaust"), readinessRegenerationPerSecond = 0.01f)
                    else -> partner
                } })
            charge(sim)
            if (missing == "readiness") {
                sim.issueOrder("p", TrainerAction.UseTechnique("exhaust", "b"))
                advance(sim, 136)
                assertNull(sim.snapshot().alliedMembers.last().currentOrderId)
                assertTrue(sim.snapshot().alliedMembers.last().debug.readiness < 0f)
            }
            open(sim)
            val partner = sim.snapshot().alliedMembers.last()
            confirm(sim)
            assertEquals(BattleFinisherKind.DUO, sim.snapshot().finisher!!.kind)
            advance(sim, sim.snapshot().finisher!!.durationMillis)
            assertEquals(partner, sim.snapshot().alliedMembers.last())
            assertEquals(1, sim.snapshot().statistics.specialsUsed)
            assertEquals(1, sim.snapshot().statistics.specialsHit)
            advance(sim, 1_020)
            assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.SpecialStarted>().none { it.combatantId == "p" })
        }
    }

    @Test fun cancellingDuoResolvesSpentContributionsOnceWithoutALaterAutoAttack() {
        val sim = simulator(partner = true)
        charge(sim)
        open(sim)
        confirm(sim)
        assertEquals(2, sim.snapshot().statistics.specialsUsed)
        assertTrue(sim.snapshot().alliedMembers.all { it.specialCharge == 0 })
        sim.cancelFinisher()
        sim.cancelFinisher()
        assertEquals(2, sim.snapshot().statistics.specialsMissed)
        assertTrue(sim.snapshot().alliedMembers.all { it.queuedOrderIds.isEmpty() && it.currentOrderId == null })
        advance(sim, 1_020)
        assertEquals(0, sim.snapshot().statistics.specialsHit)
        assertEquals(2, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.SpecialResolved>().size)
    }

    @Test fun interruptedStartupClosesWindowAndCannotLaunchAStaleFinisher() {
        val sim = formSimulator()
        charge(sim)
        open(sim)
        sim.issueOrder("a", TrainerAction.Defend(), interruptCurrentAction = true)
        sim.advance(34)
        assertTrue(sim.snapshot().pendingBlastTiming.isEmpty())
        assertEquals(OrderFailure.BLAST_NO_WINDOW, sim.issueOrder("a", TrainerAction.ConfirmBlastTiming()).reasonCode)
        assertNull(sim.snapshot().finisher)
        assertNull(sim.snapshot().alliedMembers.single().blastFormSpecies)
        assertEquals(0, sim.snapshot().alliedMembers.single().specialCharge)
        assertEquals(1, sim.snapshot().statistics.specialsMissed)
    }

    @Test fun cinematicAndResumeAreDeterministicAcrossFrameChunks() {
        fun prepared(): BattleSimulator = formSimulator().also { charge(it); open(it); confirm(it) }
        val fine = prepared()
        val coarse = prepared()
        advance(fine, 5_327, 1)
        advance(coarse, 5_327, 137)
        assertEquals(fine.snapshot(), coarse.snapshot())
        advance(fine, 3_093, 17)
        advance(coarse, 3_093, 250)
        assertEquals(fine.snapshot(), coarse.snapshot())
        assertNull(fine.snapshot().finisher)
    }
}
