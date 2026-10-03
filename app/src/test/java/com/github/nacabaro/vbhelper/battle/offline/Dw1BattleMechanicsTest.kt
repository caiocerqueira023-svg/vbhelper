package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.screens.assetOfflineBattleParticipant
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingParticipantInput
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import org.junit.Assert.*
import org.junit.Test

class Dw1BattleMechanicsTest {
    private fun fighter(id: String, side: BattleSide, skills: List<String> = listOf("strike")) =
        CombatantDefinition(id, displayName = id, side = side, maxHealth = 10_000, maxEnergy = 500,
            attack = 100, defense = 40, movementSpeed = 0f, techniqueIds = skills,
            decisionDelayMinMillis = 0, decisionDelayMaxMillis = 0)

    private fun strike(power: Int = 100) = TechniqueDefinition("strike", "Strike", TechniqueKind.MELEE,
        power, maxRange = 20f, startupMillis = 68, activeMillis = 34, recoveryMillis = 68,
        cooldownMillis = 0, criticalChance = 0f)

    private fun battle(a: CombatantDefinition, b: CombatantDefinition, catalog: List<TechniqueDefinition>) =
        BattleSimulator(BattleConfiguration(randomSeed = 71), BattleTeam("a", BattleSide.ALLIED, listOf(a)),
            BattleTeam("b", BattleSide.OPPOSING, listOf(b)), catalog)

    private fun run(sim: BattleSimulator, millis: Long) = repeat((millis / 34).toInt()) { sim.advance(34) }

    @Test fun sourceDamageUsesTechniquePowerAndTruncatesNegativeDivisionTowardZero() {
        assertEquals(100, Dw1DamageFormula.normal(100, 300, 300, 100))
        assertEquals(200, Dw1DamageFormula.normal(100, 999, 0, 100))
        assertEquals(1, Dw1DamageFormula.normal(100, 0, 999, 100))
        assertEquals(101, Dw1DamageFormula.normal(101, 300, 301, 100))
        assertEquals(150, Dw1DamageFormula.normal(100, 500, 250, 100))
    }

    @Test fun sourceFinisherUsesOffenseAndChargeWhileCounterUsesReducedDefense() {
        assertEquals(1_100, Dw1DamageFormula.finisher(200, 300, 80, 110))
        assertEquals(500, Dw1DamageFormula.finisher(200, 300, 40, 100))
        assertTrue(Dw1DamageFormula.normal(100, 300, 300, 100, counter = true) > 100)
    }

    @Test fun damageStatusOrderAndAttributeTriangleAreExplicit() {
        val input = BattleDamageInput(power = 101, attack = 100, defense = 0, variancePercent = 100,
            attackerAttribute = BattleAttribute.DATA, defenderAttribute = BattleAttribute.DATA,
            burned = true, frozen = true)
        assertEquals(95, BattleDamageResolver.resolve(input).damage)
        val neutral = input.copy(burned = false, frozen = false)
        assertTrue(BattleDamageResolver.resolve(neutral.copy(defenderAttribute = BattleAttribute.VACCINE)).damage >
            BattleDamageResolver.resolve(neutral).damage)
        assertTrue(BattleDamageResolver.resolve(neutral.copy(defenderAttribute = BattleAttribute.VIRUS)).damage <
            BattleDamageResolver.resolve(neutral).damage)
        assertEquals(0, BattleDamageResolver.resolve(input.copy(power = 0)).damage)
        assertEquals(9999, BattleDamageResolver.resolve(input.copy(power = 100_000)).damage)
        assertEquals(100, BattleDamageResolver.resolve(input.copy(power = 100, attack = 300, defense = 300,
            burned = false, frozen = false, formula = BattleDamageFormula.DW1_REFERENCE)).damage)
    }

    @Test fun readinessOverlapsRecoveryAndHeavyMovesConsumeMoreOfIt() {
        val light = battle(fighter("a", BattleSide.ALLIED),
            fighter("b", BattleSide.OPPOSING, emptyList()).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            listOf(strike(20)))
        val heavy = battle(fighter("a", BattleSide.ALLIED),
            fighter("b", BattleSide.OPPOSING, emptyList()).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            listOf(strike(220)))
        run(light, 5_000); run(heavy, 5_000)
        fun attacks(sim: BattleSimulator) = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
            .count { it.combatantId == "a" }
        assertTrue("Heavy attacks should impose a readiness cost", attacks(light) > attacks(heavy))
        assertTrue(heavy.snapshot().alliedMembers.single().debug.readiness < 100f)
    }

    @Test fun zeroChanceStatusNeverAppliesAndExclusiveEffectsDoNotReplaceEachOther() {
        val poison = BattleStatusEffect("poison", 10_000, procChance = 0f,
            tickIntervalMillis = 1_000, maxHealthPercentPerTickMin = 1, maxHealthPercentPerTickMax = 1,
            exclusivityGroup = "persistent_ailment")
        val sim = battle(fighter("a", BattleSide.ALLIED), fighter("b", BattleSide.OPPOSING, emptyList()),
            listOf(strike().copy(statusEffects = listOf(poison))))
        run(sim, 1_000)
        assertTrue(sim.snapshot().opposingMembers.single().statuses.isEmpty())

        val exclusive = battle(fighter("a", BattleSide.ALLIED), fighter("b", BattleSide.OPPOSING, emptyList()),
            listOf(strike().copy(statusEffects = listOf(poison.copy(procChance = 1f),
                BattleStatusEffect("freeze", 10_000, procChance = 1f, exclusivityGroup = "persistent_ailment")))))
        run(exclusive, 500)
        assertEquals(listOf("poison"), exclusive.snapshot().opposingMembers.single().statuses.map { it.id })
    }

    @Test fun percentPoisonTicksScaleWithMaximumHpAndDoNotTickImmediately() {
        val toxin = strike(0).copy(statusEffects = listOf(BattleStatusEffect("poison", 5_000,
            tickIntervalMillis = 1_000, maxHealthPercentPerTickMin = 2, maxHealthPercentPerTickMax = 2,
            refreshPolicy = StatusRefreshPolicy.IGNORE)))
        val sim = battle(fighter("a", BattleSide.ALLIED).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            fighter("b", BattleSide.OPPOSING, emptyList()).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            listOf(toxin))
        sim.issueOrder("a", TrainerAction.UseTechnique("strike", "b"))
        run(sim, 500)
        assertEquals(10_000, sim.snapshot().opposingMembers.single().health)
        run(sim, 1_000)
        assertEquals(9_800, sim.snapshot().opposingMembers.single().health)
    }

    @Test fun autonomousBuffsAreBoundedAndDoNotKeepRefreshingAnActiveBuff() {
        val boost = strike(0).copy(kind = TechniqueKind.SUPPORT, rangeProfile = TechniqueRangeProfile.SELF,
            statusEffects = listOf(BattleStatusEffect("boost", 10_000, attackMultiplier = 1.2f)))
        val actor = fighter("a", BattleSide.ALLIED).copy(aiProfile = BattleAiProfile(buffLimit = 1))
        val sim = battle(actor, fighter("b", BattleSide.OPPOSING, emptyList()), listOf(boost))
        run(sim, 5_000)
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueStarted>()
            .count { it.combatantId == "a" && it.techniqueId == "strike" })
    }

    @Test fun stalledPositioningReplansWithoutSpendingResourcesOrHittingOutOfRange() {
        val short = strike().copy(maxRange = 1f, energyCost = 50)
        val sim = battle(fighter("a", BattleSide.ALLIED), fighter("b", BattleSide.OPPOSING, emptyList()), listOf(short))
        run(sim, 7_000)
        assertEquals(500, sim.snapshot().alliedMembers.single().energy)
        assertTrue(sim.snapshot().alliedMembers.single().debug.positioningReplans > 0)
        assertTrue(sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>().none { it.techniqueId == "strike" })
    }

    @Test fun initialTargetUsesDistanceInsteadOfAnIdPrefix() {
        val a = fighter("a", BattleSide.ALLIED).copy(aiProfile = BattleAiProfile(targetPolicy = BattleTargetPolicy.CLOSEST))
        val far = fighter("aa", BattleSide.OPPOSING, emptyList())
        val near = fighter("zz", BattleSide.OPPOSING, emptyList())
        val sim = BattleSimulator(BattleConfiguration(), BattleTeam("a", BattleSide.ALLIED,
            listOf(a, fighter("a2", BattleSide.ALLIED, emptyList()))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(near, far)), listOf(strike()))
        run(sim, 500)
        val selected = sim.snapshot().alliedMembers.first { it.combatantId == "a" }
        assertEquals("zz", selected.targetId)
        assertTrue(selected.debug.targetReason.isNotBlank())
    }

    @Test fun counterOnlyReactsToAnInRangeGuardAndCannotStartAReactionChain() {
        val counter = TechniqueDefinition("counter", "Counter", TechniqueKind.MELEE, 80, energyCost = 25,
            maxRange = 2.5f, startupMillis = 34, activeMillis = 34, recoveryMillis = 34,
            cooldownMillis = 3_500, reactionOnly = true, criticalChance = 0f)
        val a = fighter("a", BattleSide.ALLIED).copy(movementSpeed = 3f, counterTechniqueId = "counter",
            aiProfile = BattleAiProfile(counterChance = 1f))
        val b = fighter("b", BattleSide.OPPOSING, emptyList()).copy(counterTechniqueId = "counter",
            aiProfile = BattleAiProfile(counterChance = 1f))
        val reaction = BattleSimulator(BattleConfiguration(arenaRadius = 2f),
            BattleTeam("a", BattleSide.ALLIED, listOf(a.copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(b.copy(techniqueIds = listOf("strike")))),
            listOf(strike().copy(maxRange = 2.5f), counter))
        reaction.issueOrder("a", TrainerAction.Defend(2_000))
        run(reaction, 1_000)
        assertTrue(reaction.snapshot().recentEvents.any { it is BattleEvent.CounterTriggered })
        assertEquals(OrderStatus.FAILED, reaction.issueOrder("a", TrainerAction.UseTechnique("counter", "b")).status)
        assertTrue(reaction.snapshot().recentEvents.filterIsInstance<BattleEvent.CounterTriggered>().size <= 2)
    }

    @Test fun aStatusRollDoesNotPerturbTheAttackersDamageOrAiRandomStreams() {
        fun simulation(chance: Float) = battle(fighter("a", BattleSide.ALLIED),
            fighter("b", BattleSide.OPPOSING, emptyList()).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            listOf(strike(20).copy(statusEffects = listOf(BattleStatusEffect("marker", 100, procChance = chance)))))
        val without = simulation(0f)
        val with = simulation(0.5f)
        run(without, 3_000); run(with, 3_000)
        fun hits(sim: BattleSimulator) = sim.snapshot().recentEvents.filterIsInstance<BattleEvent.TechniqueHit>()
            .filter { it.combatantId == "a" }.map { it.amount }
        assertEquals(hits(without), hits(with))
        assertEquals(without.snapshot().alliedMembers.single().position, with.snapshot().alliedMembers.single().position)
    }

    @Test fun protectionRejectsAilmentsButAllowsBuffsAndPauseFreezesNewClocks() {
        val ward = strike(0).copy(kind = TechniqueKind.SUPPORT, rangeProfile = TechniqueRangeProfile.SELF,
            statusEffects = listOf(BattleStatusEffect("ward", 5_000, protectsFromStatuses = true)))
        val attack = strike().copy(techniqueId = "toxin", statusEffects = listOf(BattleStatusEffect("poison", 4_000)))
        val sim = battle(fighter("a", BattleSide.ALLIED).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            fighter("b", BattleSide.OPPOSING, listOf("toxin")), listOf(ward, attack))
        sim.issueOrder("a", TrainerAction.UseTechnique("strike"))
        run(sim, 500)
        assertEquals(listOf("ward"), sim.snapshot().alliedMembers.single().statuses.map { it.id })
        sim.setPaused(true)
        val before = sim.snapshot()
        run(sim, 5_000)
        assertEquals(before, sim.snapshot())
    }

    @Test fun controlEffectsHaveARecoveryWindowInsteadOfRefreshingForever() {
        val stun = strike(1).copy(statusEffects = listOf(BattleStatusEffect("stun", 500,
            preventsActions = true, refreshPolicy = StatusRefreshPolicy.IGNORE)))
        val sim = battle(fighter("a", BattleSide.ALLIED), fighter("b", BattleSide.OPPOSING, emptyList()), listOf(stun))
        var disabled = 0
        repeat(150) {
            sim.advance(34)
            if (sim.snapshot().opposingMembers.single().state == CombatantState.STUNNED) disabled++
        }
        assertTrue("Control never activated", disabled > 0)
        assertTrue("Control chained without recovery", disabled < 100)
    }

    @Test fun missedContactCannotDealDamageApplyStatusesOrChargeFromAnAttack() {
        val sim = battle(fighter("a", BattleSide.ALLIED),
            fighter("b", BattleSide.OPPOSING, emptyList()).copy(decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000),
            listOf(strike().copy(accuracyPercent = 0, statusEffects = listOf(BattleStatusEffect("poison", 5_000)))))
        run(sim, 1_000)
        assertEquals(10_000, sim.snapshot().opposingMembers.single().health)
        assertTrue(sim.snapshot().opposingMembers.single().statuses.isEmpty())
        assertTrue(sim.snapshot().recentEvents.any { it is BattleEvent.TechniqueMissed })
    }

    @Test fun aZeroEncounterWeightExcludesAMoveAndFullReadinessCreatesLongerRecovery() {
        val strong = strike(220)
        val forbidden = strike(500).copy(techniqueId = "forbidden")
        val profile = BattleAiProfile(selectionMode = BattleSelectionMode.WEIGHTED,
            techniqueWeights = mapOf("strike" to 1f, "forbidden" to 0f), chargeMode = BattleChargeMode.FULL)
        val actor = fighter("a", BattleSide.ALLIED, listOf("strike", "forbidden")).copy(aiProfile = profile)
        val sim = battle(actor, fighter("b", BattleSide.OPPOSING, emptyList()).copy(
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000), listOf(strong, forbidden))
        run(sim, 5_000)
        val state = sim.snapshot().alliedMembers.single()
        assertFalse(state.debug.techniqueUses.containsKey("forbidden"))
        assertEquals(1, state.debug.techniqueUses["strike"])
        assertTrue(state.debug.readinessWaitingMillis > 1_000)
    }

    @Test fun defensiveReadCanReactWhileWaitingForTheNextOrdinaryDecision() {
        val defender = fighter("a", BattleSide.ALLIED, emptyList()).copy(strategy = BattleStrategy.DEFENSIVE,
            decisionDelayMinMillis = 60_000, decisionDelayMaxMillis = 60_000)
        val attacker = fighter("b", BattleSide.OPPOSING)
        val sim = battle(defender, attacker, listOf(strike(180).copy(startupMillis = 900)))
        run(sim, 800)
        assertEquals(CombatantState.DEFENDING, sim.snapshot().alliedMembers.single().state)
    }

    @Test fun arenaAssetsHaveStableSpeciesLoadoutsAndEncounterPreferences() {
        val a = assetOfflineBattleParticipant("dim012_mon03", "Agumon", 1000, 100)
        val same = assetOfflineBattleParticipant("dim012_mon03", "Renamed", 2000, 200)
        assertEquals(a.techniqueIds, same.techniqueIds)
        assertEquals(a.aiProfile, same.aiProfile)
        assertEquals(BattleSelectionMode.WEIGHTED, a.aiProfile.selectionMode)
        val distinctKits = (0..15).map { assetOfflineBattleParticipant("asset$it", "Asset", 1000, 100).techniqueIds }.toSet()
        assertTrue("Arena enemies still share one universal kit", distinctKits.size > 1)
        val customized = TrainingBattleFactory.definition(TrainingParticipantInput("asset", displayName = "Asset",
            stage = 3, maxHealth = 1000, attack = 100, techniqueIds = GenericTechniqueCatalog.defaultTechniqueIds,
            aiProfile = a.aiProfile), BattleSide.OPPOSING)
        assertTrue(customized.aiProfile.techniqueWeights.keys.all { it in customized.techniqueIds })
    }
}
