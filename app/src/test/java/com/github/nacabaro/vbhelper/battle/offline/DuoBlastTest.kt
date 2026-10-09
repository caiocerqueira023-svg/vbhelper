package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.domain.device_data.BlastEvolutionSlot
import org.junit.Assert.*
import org.junit.Test

class DuoBlastTest {
    private fun fighter(id: String, side: BattleSide, skills: List<String> = emptyList()) =
        CombatantDefinition(id, displayName = id, side = side, maxHealth = 10_000, maxEnergy = 500,
            attack = 100, defense = 0, movementSpeed = 0f, techniqueIds = skills,
            sourceCharacterId = if (side == BattleSide.ALLIED) id.hashCode().toLong() else null,
            decisionDelayMinMillis = 0, decisionDelayMaxMillis = 0)

    private fun strike() = TechniqueDefinition("strike", "Strike", TechniqueKind.MELEE, 10,
        maxRange = 25f, startupMillis = 68, activeMillis = 34, recoveryMillis = 68,
        cooldownMillis = 200, criticalChance = 0f)

    private fun special() = TechniqueDefinition("blast_special", "Blast Special",
        TechniqueKind.SPECIAL, 100, maxRange = 25f, startupMillis = 340, activeMillis = 68,
        recoveryMillis = 200, cooldownMillis = 0, criticalChance = 0f)

    private fun duoSim(seed: Long, configure: (CombatantDefinition) -> CombatantDefinition = { it }): BattleSimulator {
        val strike = strike()
        val special = special()
        val lead = configure(fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId)))
            .copy(specialTechniqueId = special.techniqueId)
        val partner = configure(fighter("p", BattleSide.ALLIED, listOf(strike.techniqueId)))
            .copy(specialTechniqueId = special.techniqueId)
        val enemy = fighter("b", BattleSide.OPPOSING)
        return BattleSimulator(
            BattleConfiguration(randomSeed = seed),
            BattleTeam("allies", BattleSide.ALLIED, listOf(lead, partner)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
    }

    private fun runFor(sim: BattleSimulator, millis: Long) {
        repeat((millis / 34).toInt()) { sim.advance(34) }
    }

    private fun until(sim: BattleSimulator, millis: Long = 180_000, condition: (BattleSnapshot) -> Boolean) {
        repeat((millis / 34).toInt()) {
            if (condition(sim.snapshot())) return
            sim.advance(34)
        }
        assertTrue("Condition not reached", condition(sim.snapshot()))
    }

    private fun chargeBoth(sim: BattleSimulator) {
        until(sim) {
            it.alliedMembers.all { member -> member.specialCharge >= 100 }
        }
    }

    private fun specialHit(sim: BattleSimulator, who: String): Int =
        sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .last { it.combatantId == who && it.techniqueId == "blast_special" }.amount

    @Test fun readyDuoCommitsBothSpecialsInsideOneCinematic() {
        val sim = duoSim(81L) { if (it.combatantId == "p")
            it.copy(decisionDelayMinMillis = 150_000, decisionDelayMaxMillis = 150_000) else it }
        chargeBoth(sim)
        val order = sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        assertEquals(OrderStatus.QUEUED, order.status)
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        assertEquals(OrderStatus.COMPLETED, sim.issueOrder("a", TrainerAction.ConfirmBlastTiming()).status)
        assertEquals(BattleFinisherKind.DUO, sim.snapshot().finisher!!.kind)
        assertEquals(listOf("a", "p"), sim.snapshot().finisher!!.participantIds)
        until(sim) { it.finisher == null }
        assertEquals(2, sim.snapshot().statistics.blastTimedHits)
        assertEquals(2, sim.snapshot().statistics.specialsUsed)
        assertEquals(0, sim.snapshot().alliedMembers.last().specialCharge)
        // Both fighters visibly landed their own specials.
        val hits = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .filter { it.techniqueId == "blast_special" }.map { it.combatantId }.toSet()
        assertEquals(setOf("a", "p"), hits)
    }

    @Test fun fusionViaTapTransformsLead() {
        val sim = duoSim(82L)
        chargeBoth(sim)
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        sim.issueOrder("a", TrainerAction.ConfirmBlastTiming("Omegamon", "Garuru Cannon"))
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.BlastJogressStarted>().any { started ->
                started.leadId == "a" && started.partnerId == "p" && started.resultSpecies == "Omegamon"
            }
        }
        until(sim) { it.finisher == null }
        val ended = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastJogressEnded>()
        assertTrue(ended.any { it.leadId == "a" && it.resultSpecies == "Omegamon" })
        assertEquals(1, sim.snapshot().statistics.blastTimedHits)
        assertEquals(1, sim.snapshot().statistics.specialsUsed)
        assertEquals(100, sim.snapshot().alliedMembers.last().specialCharge)
        val fusedLead = specialHit(sim, "a")

        // Solo baseline: same seed, single ally, plain tapped special.
        val strike = strike()
        val special = special()
        val soloLead = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(specialTechniqueId = special.techniqueId)
        val soloEnemy = fighter("b", BattleSide.OPPOSING)
        val solo = BattleSimulator(
            BattleConfiguration(randomSeed = 82L),
            BattleTeam("allies", BattleSide.ALLIED, listOf(soloLead)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(soloEnemy)),
            listOf(strike, special)
        )
        until(solo) { it.alliedMembers.single().specialCharge >= 100 }
        solo.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(solo) { it.pendingBlastTiming.containsKey("a") }
        solo.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        until(solo) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        val soloHit = specialHit(solo, "a")
        assertTrue("Fused $fusedLead should exceed solo $soloHit", fusedLead > soloHit)
    }

    @Test fun partnerWithoutSpecialStillGetsDuoPresentationWithoutExtraDamage() {
        val strike = strike()
        val special = special()
        val lead = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(specialTechniqueId = special.techniqueId)
        val partner = fighter("p", BattleSide.ALLIED, listOf(strike.techniqueId))
        val enemy = fighter("b", BattleSide.OPPOSING)
        val sim = BattleSimulator(
            BattleConfiguration(randomSeed = 83L),
            BattleTeam("allies", BattleSide.ALLIED, listOf(lead, partner)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
        until(sim) {
            it.alliedMembers.first { member -> member.combatantId == "a" }.specialCharge >= 100
        }
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        assertEquals(BattleFinisherKind.DUO, sim.snapshot().finisher!!.kind)
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        assertEquals(1, sim.snapshot().statistics.blastTimedHits)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastJogressStarted>().isEmpty())
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .none { it.combatantId == "p" && it.techniqueId == "blast_special" })
    }

    @Test fun formTransformEmitsEventsAndBoosts() {
        val strike = strike()
        val special = special()
        val lead = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(specialTechniqueId = special.techniqueId,
                blastMode = BlastEvolutionSlot.FORM,
                blastTargetSpecies = "WarGreymon X",
                blastFormSpecial = "Gaia Force")
        val enemy = fighter("b", BattleSide.OPPOSING)
        val sim = BattleSimulator(
            BattleConfiguration(randomSeed = 84L),
            BattleTeam("allies", BattleSide.ALLIED, listOf(lead)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
        until(sim) { it.alliedMembers.single().specialCharge >= 100 }
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.BlastFormEnded>().isNotEmpty()
        }
        val started = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastFormStarted>().last()
        assertEquals("a", started.combatantId)
        assertEquals("WarGreymon X", started.targetSpecies)
        assertEquals("Gaia Force", started.specialName)
        assertEquals(1, sim.snapshot().statistics.blastTimedHits)
    }

    @Test fun powerBlastBeatsUnslottedHit() {
        fun tappedDamage(seed: Long, mode: String): Int {
            val strike = strike()
            val special = special()
            val lead = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
                .copy(specialTechniqueId = special.techniqueId, blastMode = mode)
            val enemy = fighter("b", BattleSide.OPPOSING)
            val sim = BattleSimulator(
                BattleConfiguration(randomSeed = seed),
                BattleTeam("allies", BattleSide.ALLIED, listOf(lead)),
                BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
                listOf(strike, special)
            )
            until(sim) { it.alliedMembers.single().specialCharge >= 100 }
            sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
            until(sim) { it.pendingBlastTiming.containsKey("a") }
            sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
            until(sim) {
                it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                    .any { resolved -> resolved.combatantId == "a" }
            }
            return specialHit(sim, "a")
        }
        val power = tappedDamage(85L, BlastEvolutionSlot.POWER)
        val none = tappedDamage(85L, BlastEvolutionSlot.NONE)
        assertTrue("POWER $power should exceed NONE $none", power > none)
    }

    @Test fun fusionEndedFollowsFusedResolve() {
        val sim = duoSim(86L)
        chargeBoth(sim)
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        sim.issueOrder("a", TrainerAction.ConfirmBlastTiming("Omegamon", "Garuru Cannon"))
        until(sim) { it.finisher == null }
        val ended = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.BlastJogressEnded>()
        assertTrue(ended.any { it.leadId == "a" && it.resultSpecies == "Omegamon" })
    }

    @Test fun transformFlagVisibleInSnapshotOnlyWhileActive() {
        val strike = strike()
        val special = special()
        val lead = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(specialTechniqueId = special.techniqueId,
                blastMode = BlastEvolutionSlot.FORM,
                blastTargetSpecies = "WarGreymon X")
        val enemy = fighter("b", BattleSide.OPPOSING)
        val sim = BattleSimulator(
            BattleConfiguration(randomSeed = 87L),
            BattleTeam("allies", BattleSide.ALLIED, listOf(lead)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
        until(sim) { it.alliedMembers.single().specialCharge >= 100 }
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        assertNull(sim.snapshot().alliedMembers.single().blastFormSpecies)
        sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        assertEquals("WarGreymon X", sim.snapshot().alliedMembers.single().blastFormSpecies)
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        assertEquals("WarGreymon X", sim.snapshot().alliedMembers.single().blastFormSpecies)
        assertTrue(sim.snapshot().finisher!!.resultVisible)
        until(sim) { it.finisher == null }
        assertNull(sim.snapshot().alliedMembers.single().blastFormSpecies)
    }

    @Test fun visibleFightersPreferMatchingAltWhileTransformed() {
        fun presentation(id: String, name: String, key: String) =
            com.github.nacabaro.vbhelper.screens.offlineBattle.BattleFighterPresentation(
                combatantId = id, externalCharacterId = "x", displayName = name,
                modelGlb = ByteArray(0), poseNames = emptySet(), setKey = key,
                attackVisuals = emptyMap(), impactModels = emptyMap()
            )
        fun member(id: String, form: String?) = CombatantSnapshot(
            combatantId = id, sourceCharacterId = 1, externalCharacterId = "x",
            displayName = "Base", side = BattleSide.ALLIED, health = 100, maxHealth = 100,
            energy = 10, maxEnergy = 10, position = BattlePosition(0f, 0f), targetId = null,
            state = CombatantState.ATTACK_STARTUP, strategy = BattleStrategy.BALANCED,
            activeTechniqueId = null, statuses = emptyList(), cooldownsMillis = emptyMap(),
            blastFormSpecies = form
        )
        fun snapshotWith(form: String?) = BattleSnapshot(
            elapsedMillis = 0, isPaused = false, pauseReason = null, commandPoints = 0,
            maxCommandPoints = 100, alliedMembers = listOf(member("ally:a", form)),
            opposingMembers = emptyList(), pendingSupportCombatantIds = emptySet(),
            result = null, recentEvents = emptyList()
        )
        val base = mapOf("ally:a" to presentation("ally:a", "Agumon", "base"))
        val alts = mapOf("ally:a" to presentation("ally:a", "WarGreymon X", "alt"))
        val shown = com.github.nacabaro.vbhelper.screens.offlineBattle.resolveVisibleFighters(
            base, alts, snapshotWith("WarGreymon X"))
        assertEquals("WarGreymon X", shown.getValue("ally:a").displayName)
        assertEquals("alt", shown.getValue("ally:a").setKey)
        val reverted = com.github.nacabaro.vbhelper.screens.offlineBattle.resolveVisibleFighters(
            base, alts, snapshotWith(null))
        assertEquals("Agumon", reverted.getValue("ally:a").displayName)
        // Alt for a different species never applies.
        val mismatched = com.github.nacabaro.vbhelper.screens.offlineBattle.resolveVisibleFighters(
            base, alts, snapshotWith("Omegamon"))
        assertEquals("Agumon", mismatched.getValue("ally:a").displayName)
    }
}
