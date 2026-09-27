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

    @Test fun eachBemTrainingStatOnlyImprovesItsMatchingPrimaryCombatStat() {
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

    @Test fun braceletStatsAlsoShapeEnergyRegenerationMovementAndCooldowns() {
        val baseline = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 1_222)
        )
        val tank = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 4_495, bp = 5_171, ap = 1_222)
        )
        val disciplined = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 6_170, ap = 1_222)
        )
        val striker = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_171, ap = 2_221)
        )

        assertTrue("HP should add resource capacity", tank.energy > baseline.energy)
        assertTrue("HP-heavy tanks should move more slowly", tank.movementSpeed < baseline.movementSpeed)
        assertTrue("HP-heavy tanks should cycle techniques more slowly", tank.cooldownMultiplier > baseline.cooldownMultiplier)
        assertTrue("HP-heavy tanks should regenerate more slowly", tank.energyRegenerationPerSecond < baseline.energyRegenerationPerSecond)

        assertTrue("BP should add resource capacity", disciplined.energy > baseline.energy)
        assertTrue("BP should improve sustained regeneration", disciplined.energyRegenerationPerSecond > baseline.energyRegenerationPerSecond)
        assertTrue("BP should make action timing more stable", disciplined.cooldownMultiplier < baseline.cooldownMultiplier)

        assertTrue("AP should add resource capacity", striker.energy > baseline.energy)
        assertTrue("AP should improve movement tempo", striker.movementSpeed > baseline.movementSpeed)
        assertTrue("AP should shorten technique cooldowns", striker.cooldownMultiplier < baseline.cooldownMultiplier)
    }

    @Test fun higherStagesKeepStrictSpeedAndCooldownBands() {
        val fastestLowerStage = TrainingBattleStats.forParticipant(
            stage = 3,
            vitalStats = bem(hp = 1, bp = 65_534, ap = 65_534)
        )
        val slowestHigherStage = TrainingBattleStats.forParticipant(
            stage = 4,
            vitalStats = bem(hp = 65_534, bp = 1, ap = 1)
        )

        assertTrue(
            "A higher-stage Digimon must remain faster even with a tank profile",
            slowestHigherStage.movementSpeed > fastestLowerStage.movementSpeed
        )
        assertTrue(
            "A higher-stage Digimon must retain a faster cooldown floor",
            slowestHigherStage.cooldownMultiplier < fastestLowerStage.cooldownMultiplier
        )
    }

    @Test fun factoryCopiesEveryDerivedCombatStatIntoTheDefinition() {
        val profile = bem(hp = 4_495, bp = 6_170, ap = 2_221)
        val expected = TrainingBattleStats.forParticipant(stage = 3, vitalStats = profile)
        val definition = TrainingBattleFactory.definition(
            input("derived", stage = 3, vitalStats = profile),
            BattleSide.ALLIED
        )

        assertEquals(expected.health, definition.maxHealth)
        assertEquals(expected.attack, definition.attack)
        assertEquals(expected.defense, definition.defense)
        assertEquals(expected.energy, definition.maxEnergy)
        assertEquals(expected.energyRegenerationPerSecond, definition.energyRegenerationPerSecond, 0.0001f)
        assertEquals(expected.movementSpeed, definition.movementSpeed, 0.0001f)
        assertEquals(expected.cooldownMultiplier, definition.techniqueCooldownMultiplier, 0.0001f)
        assertEquals(expected.decisionDelayMinMillis, definition.decisionDelayMinMillis)
        assertEquals(expected.decisionDelayMaxMillis, definition.decisionDelayMaxMillis)
    }

    @Test fun fivePercentAcrossAllBraceletStatsStaysBelowTheV3DominanceGate() {
        val average = input(
            id = "average",
            stage = 3,
            vitalStats = bem(hp = 3_496, bp = 5_172, ap = 1_223)
        )
        val stronger = input(
            id = "stronger",
            stage = 3,
            vitalStats = bem(hp = 3_671, bp = 5_431, ap = 1_284)
        )
        var strongerWins = 0
        var decided = 0

        for (seed in 1L..100L) {
            for (sideSwapped in listOf(false, true)) {
                val simulator = TrainingBattleFactory.create(
                    allies = listOf(if (sideSwapped) stronger else average),
                    opponents = listOf(if (sideSwapped) average else stronger),
                    configuration = BattleConfiguration(randomSeed = seed)
                )
                repeat(3_600) {
                    if (simulator.snapshot().result == null) simulator.advance(34L)
                }
                when (simulator.snapshot().result?.outcome) {
                    BattleOutcome.ALLIED_VICTORY -> {
                        decided++
                        if (sideSwapped) strongerWins++
                    }
                    BattleOutcome.OPPOSING_VICTORY -> {
                        decided++
                        if (!sideSwapped) strongerWins++
                    }
                    else -> Unit
                }
            }
        }

        val winRate = strongerWins.toFloat() / decided.coerceAtLeast(1)
        println("DERIVED_STAT_BALANCE: stronger=$strongerWins decided=$decided rate=$winRate")
        assertTrue("The stronger profile should retain a measurable advantage: $winRate", winRate > 0.50f)
        assertTrue("A +5% profile exceeded the V3 70% dominance gate: $winRate", winRate <= 0.70f)
    }

    @Test fun tankAndStrikerProfilesRemainInTheSameStageBalanceBand() {
        val tank = input(
            id = "tank",
            stage = 3,
            vitalStats = bem(hp = 4_195, bp = 5_689, ap = 978)
        )
        val striker = input(
            id = "striker",
            stage = 3,
            vitalStats = bem(hp = 3_146, bp = 4_913, ap = 1_468)
        )
        var tankWins = 0
        var decided = 0

        for (seed in 101L..200L) {
            for (sideSwapped in listOf(false, true)) {
                val simulator = TrainingBattleFactory.create(
                    allies = listOf(if (sideSwapped) striker else tank),
                    opponents = listOf(if (sideSwapped) tank else striker),
                    configuration = BattleConfiguration(randomSeed = seed)
                )
                repeat(3_600) {
                    if (simulator.snapshot().result == null) simulator.advance(34L)
                }
                when (simulator.snapshot().result?.outcome) {
                    BattleOutcome.ALLIED_VICTORY -> {
                        decided++
                        if (!sideSwapped) tankWins++
                    }
                    BattleOutcome.OPPOSING_VICTORY -> {
                        decided++
                        if (sideSwapped) tankWins++
                    }
                    else -> Unit
                }
            }
        }

        val tankWinRate = tankWins.toFloat() / decided.coerceAtLeast(1)
        println("DERIVED_ROLE_BALANCE: tank=$tankWins decided=$decided rate=$tankWinRate")
        assertTrue("Tank profile fell outside the same-stage balance band: $tankWinRate", tankWinRate in 0.35f..0.65f)
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
