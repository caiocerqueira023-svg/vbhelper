package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import org.junit.Assert.*
import org.junit.Test

class BlastTimingTest {
    private fun fighter(id: String, side: BattleSide, skills: List<String> = emptyList()) =
        CombatantDefinition(id, displayName = id, side = side, maxHealth = 10_000, maxEnergy = 500,
            attack = 100, defense = 0, movementSpeed = 0f, techniqueIds = skills,
            decisionDelayMinMillis = 0, decisionDelayMaxMillis = 0)

    private fun strike() = TechniqueDefinition("strike", "Strike", TechniqueKind.MELEE, 10,
        maxRange = 20f, startupMillis = 68, activeMillis = 34, recoveryMillis = 68,
        cooldownMillis = 200, criticalChance = 0f)

    private fun special(power: Int = 100) = TechniqueDefinition("blast_special", "Blast Special",
        TechniqueKind.SPECIAL, power, maxRange = 20f, startupMillis = 340, activeMillis = 68,
        recoveryMillis = 200, cooldownMillis = 0, criticalChance = 0f)

    private fun sim(seed: Long, specialPower: Int = 100): BattleSimulator {
        val strike = strike()
        val special = special(specialPower)
        val ally = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
            .copy(specialTechniqueId = special.techniqueId)
        val enemy = fighter("b", BattleSide.OPPOSING)
        return BattleSimulator(
            BattleConfiguration(randomSeed = seed),
            BattleTeam("allies", BattleSide.ALLIED, listOf(ally)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
    }

    private fun runFor(sim: BattleSimulator, millis: Long) {
        repeat((millis / 34).toInt()) { sim.advance(34) }
    }

    private fun until(sim: BattleSimulator, millis: Long = 60_000, condition: (BattleSnapshot) -> Boolean) {
        repeat((millis / 34).toInt()) {
            if (condition(sim.snapshot())) return
            sim.advance(34)
        }
        assertTrue("Condition not reached", condition(sim.snapshot()))
    }

    private fun chargeAndFire(sim: BattleSimulator, tap: Boolean): Int {
        until(sim) { it.alliedMembers.single().specialCharge >= 100 }
        val order = sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        assertEquals(OrderStatus.QUEUED, order.status)
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        if (tap) {
            val tapUpdate = sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
            assertEquals(OrderStatus.COMPLETED, tapUpdate.status)
        }
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        return sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .last { it.combatantId == "a" && it.techniqueId == "blast_special" }.amount
    }

    @Test fun confirmedBlastHitsHarderThanUnconfirmedSpecial() {
        val tappedDamage = chargeAndFire(sim(71L), tap = true)
        val untappedDamage = chargeAndFire(sim(71L), tap = false)
        assertTrue("Tapped $tappedDamage should exceed untapped $untappedDamage", tappedDamage > untappedDamage)
    }

    @Test fun confirmedBlastCountsTimedHit() {
        val tapped = sim(72L)
        chargeAndFire(tapped, tap = true)
        val stats = tapped.snapshot().statistics
        assertEquals(1, stats.specialsUsed)
        assertEquals(1, stats.specialsHit)
        assertEquals(1, stats.blastTimedHits)

        val untapped = sim(72L)
        chargeAndFire(untapped, tap = false)
        val plain = untapped.snapshot().statistics
        assertEquals(1, plain.specialsUsed)
        assertEquals(1, plain.specialsHit)
        assertEquals(0, plain.blastTimedHits)
    }

    @Test fun expiredWindowFallsBackToNormalSpecial() {
        val sim = sim(73L)
        until(sim) { it.alliedMembers.single().specialCharge >= 100 }
        sim.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(sim) { it.pendingBlastTiming.containsKey("a") }
        // Let the startup window lapse without tapping.
        runFor(sim, 2_000)
        assertFalse(sim.snapshot().pendingBlastTiming.containsKey("a"))
        val late = sim.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        assertEquals(OrderStatus.FAILED, late.status)
        until(sim) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        assertEquals(0, sim.snapshot().statistics.blastTimedHits)
    }

    @Test fun opposingSpecialOpensNoBlastWindow() {
        val strike = strike()
        val special = special()
        val ally = fighter("a", BattleSide.ALLIED, listOf(strike.techniqueId))
        val enemy = fighter("b", BattleSide.OPPOSING)
            .copy(specialTechniqueId = special.techniqueId,
                aiProfile = BattleAiProfile(profileId = "eager", autonomousSpecial = true))
        val sim = BattleSimulator(
            BattleConfiguration(randomSeed = 74L),
            BattleTeam("allies", BattleSide.ALLIED, listOf(ally)),
            BattleTeam("enemies", BattleSide.OPPOSING, listOf(enemy)),
            listOf(strike, special)
        )
        until(sim, 120_000) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialStarted>()
                .any { started -> started.combatantId == "b" }
        }
        assertTrue(sim.snapshot().pendingBlastTiming.isEmpty())
    }

    @Test fun blastTapAcceptedWhileBlastPaused() {
        val s = sim(75L)
        until(s) { it.alliedMembers.single().specialCharge >= 100 }
        s.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(s) { it.pendingBlastTiming.containsKey("a") }
        // The timing overlay pauses the sim while aiming; the tap must land.
        s.setPaused(true, "blast-menu")
        val tap = s.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        assertEquals(OrderStatus.COMPLETED, tap.status)
        s.setPaused(false, "blast-menu")
        until(s) {
            it.recentEvents.filterIsInstance<BattleEvent.SpecialResolved>()
                .any { resolved -> resolved.combatantId == "a" }
        }
        assertEquals(1, s.snapshot().statistics.blastTimedHits)
    }

    @Test fun blastTapRejectedWhileOtherwisePaused() {
        val s = sim(76L)
        until(s) { it.alliedMembers.single().specialCharge >= 100 }
        s.issueOrder("a", TrainerAction.UseTechnique("blast_special", "b"))
        until(s) { it.pendingBlastTiming.containsKey("a") }
        s.setPaused(true, "manual")
        val tap = s.issueOrder("a", TrainerAction.ConfirmBlastTiming())
        assertEquals(OrderStatus.FAILED, tap.status)
        assertEquals(OrderFailure.BLAST_PAUSED, tap.reasonCode)
    }
}
