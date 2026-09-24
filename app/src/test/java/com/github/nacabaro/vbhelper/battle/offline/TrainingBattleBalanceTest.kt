package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.data.*
import org.junit.Assert.*
import org.junit.Test

class TrainingBattleBalanceTest {
    private fun input(
        id: String,
        stage: Int,
        hp: Int = 8,
        attack: Int = 3,
        vitalStats: VitalBattleProfile? = null
    ) = TrainingParticipantInput(
        id,
        displayName = id,
        stage = stage,
        maxHealth = hp,
        attack = attack,
        vitalStats = vitalStats
    )

    private fun dim(
        hp: Int,
        bp: Int,
        ap: Int,
        vitals: Int = 0,
        mood: Int = 50
    ) = VitalBattleProfile(
        scale = VitalStatScale.DIM,
        baseHp = hp,
        baseBp = bp,
        baseAp = ap,
        vitalPoints = vitals,
        mood = mood
    )

    private fun bem(
        hp: Int,
        bp: Int,
        ap: Int,
        trainingHp: Int = 0,
        trainingBp: Int = 0,
        trainingAp: Int = 0,
        vitals: Int = 0,
        mood: Int = 50
    ) = VitalBattleProfile(
        scale = VitalStatScale.BEM,
        baseHp = hp,
        baseBp = bp,
        baseAp = ap,
        trainingHp = trainingHp,
        trainingBp = trainingBp,
        trainingAp = trainingAp,
        vitalPoints = vitals,
        mood = mood
    )

    @Test fun cardAndOnlineUnitsUseACompactStageBasedHealthScale() {
        val card = TrainingBattleFactory.definition(input("card", 3), BattleSide.ALLIED)
        val online = TrainingBattleFactory.definition(input("online", 3, 3000, 1100), BattleSide.OPPOSING)
        assertEquals(card.maxHealth, online.maxHealth)
        assertEquals(card.attack, online.attack)
        for (stage in 0..5) {
            for ((hp, ap) in listOf(1 to 1, 8 to 3, 9999 to 9999, Int.MAX_VALUE to Int.MAX_VALUE)) {
                val profile = TrainingBattleFactory.definition(input("custom", stage, hp, ap), BattleSide.ALLIED)
                assertTrue(profile.maxHealth in 900..1800)
                assertTrue(profile.attack in 110..180)
            }
        }
    }

    @Test fun equivalentDimAndBemStatsLandOnTheSameCombatScale() {
        val dimDefinition = TrainingBattleFactory.definition(
            input("dim", stage = 2, vitalStats = dim(hp = 3, bp = 10, ap = 2)),
            BattleSide.ALLIED
        )
        val bemDefinition = TrainingBattleFactory.definition(
            input("bem", stage = 2, vitalStats = bem(hp = 3_005, bp = 4_657, ap = 1_022)),
            BattleSide.OPPOSING
        )

        assertEquals(dimDefinition.maxHealth, bemDefinition.maxHealth)
        assertEquals(dimDefinition.attack, bemDefinition.attack)
        assertEquals(dimDefinition.defense, bemDefinition.defense)
    }

    @Test fun eachBemTrainingStatOnlyImprovesItsMatchingCombatStat() {
        val base = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222)
        )
        val hpTrained = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, trainingHp = 999)
        )
        val bpTrained = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, trainingBp = 999)
        )
        val apTrained = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, trainingAp = 999)
        )

        assertTrue(hpTrained.health > base.health)
        assertEquals(base.attack, hpTrained.attack)
        assertEquals(base.defense, hpTrained.defense)
        assertTrue(bpTrained.defense > base.defense)
        assertEquals(base.health, bpTrained.health)
        assertEquals(base.attack, bpTrained.attack)
        assertTrue(apTrained.attack > base.attack)
        assertEquals(base.health, apTrained.health)
        assertEquals(base.defense, apTrained.defense)
    }

    @Test fun vitalPointsAndMoodApplyBraceletBattleBonuses() {
        val neutral = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, vitals = 0, mood = 50)
        )
        val boosted = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, vitals = 8_000, mood = 80)
        )
        val upset = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222, vitals = 0, mood = 10)
        )

        assertTrue(kotlin.math.abs((neutral.health * 1.10f).toInt() - boosted.health) <= 1)
        assertTrue(kotlin.math.abs((neutral.attack * 1.05f).toInt() - boosted.attack) <= 1)
        assertEquals(neutral.defense, boosted.defense)
        assertTrue(upset.attack < neutral.attack)
    }

    @Test fun bemTrainingBonusesAreCappedAtTheBraceletLimit() {
        val maximum = TrainingBattleStats.forParticipant(
            stage = 4,
            vitalStats = bem(4_408, 5_664, 1_506, trainingHp = 999, trainingBp = 999, trainingAp = 999)
        )
        val corrupted = TrainingBattleStats.forParticipant(
            stage = 4,
            vitalStats = bem(4_408, 5_664, 1_506, trainingHp = 50_000, trainingBp = 50_000, trainingAp = 50_000)
        )

        assertEquals(maximum, corrupted)
    }

    @Test fun strongerDimCardStatsRemainStrongerAfterCrossGenerationScaling() {
        val minimum = TrainingBattleStats.forParticipant(
            stage = 4,
            vitalStats = dim(hp = 6, bp = 25, ap = 3)
        )
        val maximum = TrainingBattleStats.forParticipant(
            stage = 4,
            vitalStats = dim(hp = 12, bp = 40, ap = 6)
        )

        assertTrue(maximum.health > minimum.health)
        assertTrue(maximum.attack > minimum.attack)
        assertTrue(maximum.defense > minimum.defense)
    }

    @Test fun autonomousTrainingNeverRestoresHealthWithoutAnItem() {
        val sim = TrainingBattleFactory.create(
            listOf(input("ally", 3)),
            listOf(input("enemy", 3)),
            BattleConfiguration(randomSeed = 17L)
        )
        val previousHealth = mutableMapOf<String, Int>()
        var observedDamage = false
        repeat(2_500) {
            sim.advance(34)
            val snapshot = sim.snapshot()
            for (member in snapshot.alliedMembers + snapshot.opposingMembers) {
                val previous = previousHealth.put(member.combatantId, member.health)
                if (previous != null) {
                    assertTrue(
                        "${member.combatantId} regenerated from $previous to ${member.health}",
                        member.health <= previous
                    )
                    observedDamage = observedDamage || member.health < previous
                }
            }
            if (snapshot.result != null) return@repeat
        }
        assertTrue("The monotonic-health assertion never observed combat damage", observedDamage)
    }

    @Test fun autonomousFightersWalkBeforeSettlingIntoTheirAttackRhythm() {
        val sim = TrainingBattleFactory.create(
            listOf(input("ally", 2)),
            listOf(input("enemy", 2)),
            BattleConfiguration(randomSeed = 23L)
        )
        val initial = (sim.snapshot().alliedMembers + sim.snapshot().opposingMembers)
            .associate { it.combatantId to it.position }
        var greatestDisplacement = 0f
        var observedMovementState = false
        var firstAttackObserved = false
        repeat(180) {
            if (firstAttackObserved) return@repeat
            sim.advance(34)
            val snapshot = sim.snapshot()
            firstAttackObserved = snapshot.recentEvents.any { it is BattleEvent.TechniqueStarted }
            for (member in snapshot.alliedMembers + snapshot.opposingMembers) {
                greatestDisplacement = maxOf(
                    greatestDisplacement,
                    member.position.distanceTo(initial.getValue(member.combatantId))
                )
                observedMovementState = observedMovementState || member.state in setOf(
                    CombatantState.MOVE_TO_TARGET,
                    CombatantState.MOVE_AWAY,
                    CombatantState.POSITIONING
                )
            }
        }
        assertTrue("No autonomous attack was eventually selected", firstAttackObserved)
        assertTrue("Autonomous fighters never moved while reading the opening", observedMovementState)
        assertTrue("Autonomous fighters barely left their lineup before attacking: $greatestDisplacement", greatestDisplacement >= 0.35f)
    }

    @Test fun openingHasAReadableTellInsteadOfAnImmediateAttack() {
        val sim = TrainingBattleFactory.create(
            listOf(input("ally", 2)),
            listOf(input("enemy", 2)),
            BattleConfiguration(randomSeed = 31L)
        )
        repeat(17) { sim.advance(34) }
        assertTrue(
            "A technique started during the opening read",
            sim.snapshot().recentEvents.none { it is BattleEvent.TechniqueStarted }
        )
        repeat(360) { sim.advance(34) }
        val starts = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
            .groupingBy { it.combatantId }.eachCount()
        assertTrue("No fighter attacked after its opening read", starts.isNotEmpty())
        assertTrue("Attack rhythm is still spammy: $starts", starts.values.all { it <= 5 })
    }

    @Test fun allFormatsGiveTimeToCommandAndFinishWithoutIntervention() {
        val durations = mutableListOf<Long>()
        for ((allies, enemies) in listOf(1 to 1, 1 to 2, 2 to 2)) for (stage in listOf(0, 3, 5)) for (seed in 1L..8L) {
            val sim = TrainingBattleFactory.create(
                (1..allies).map { input("a$it", stage) },
                (1..enemies).map { input("e$it", stage, 3000, 1100) },
                BattleConfiguration(randomSeed = seed)
            )
            var firstDefeat: Long? = null
            repeat(4500) {
                sim.advance(34)
                val state = sim.snapshot()
                if (firstDefeat == null && (state.alliedMembers + state.opposingMembers).any { it.health == 0 }) firstDefeat = state.elapsedMillis
            }
            val result = sim.snapshot().result
            assertNotNull("Unfinished ${allies}x$enemies stage=$stage seed=$seed", result)
            assertTrue("No time to command: $firstDefeat", (firstDefeat ?: 0L) >= 12_000L)
            assertTrue("Fight too long: $result", result!!.elapsedMillis < 150_000L)
            assertTrue("No combat happened", result.statistics.damageDealt > 0 && result.statistics.damageReceived > 0)
            durations += result.elapsedMillis
        }
        println("PRACTICE_BALANCE: 72 sessions; min=${durations.min()}ms max=${durations.max()}ms mean=${durations.average().toLong()}ms")
    }

    @Test fun evenTheLargestStageMismatchCannotEndInTheOpeningSecond() {
        val sim = TrainingBattleFactory.create(listOf(input("rookie", 0)), listOf(input("mega1", 5), input("mega2", 5)))
        repeat(147) { sim.advance(34) }
        assertNull(sim.snapshot().result)
        assertTrue(sim.snapshot().alliedMembers.single().health > 0)
    }
}
