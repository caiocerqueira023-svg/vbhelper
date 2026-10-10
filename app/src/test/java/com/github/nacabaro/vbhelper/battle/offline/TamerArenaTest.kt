package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TamerArenaTest {
    private fun catalog() = TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText())
    private fun species(name: String, stage: Int) = ArenaSpecies(name, stage, "fixture:$name", null,
        BattleAttribute.VACCINE, listOf("Signature"), null)

    @Test fun rosterHasBroadMediaCoverageAndDistinctContinuities() {
        val roster = catalog()
        assertTrue(roster.size >= 120)
        assertEquals(roster.size, roster.map { it.id }.distinct().size)
        assertTrue(roster.any { it.series == "Digimon World 2" })
        assertTrue(roster.any { it.series == "Digimon Liberator" })
        assertTrue(roster.any { it.series == "Digimon Survive" })
        assertNotEquals(roster.single { it.id == "taichi-adventure" }.primary,
            roster.single { it.id == "taichi-vtamer" }.primary)
        assertTrue(roster.single { it.id == "ruki" }.searchMatches("Rika"))
    }

    @Test fun teamUsesActualStageFormsAndLabelsBorrowedPartners() {
        val resolver = TamerTeamResolver(catalog(), listOf(species("Agumon", 2), species("Greymon", 3),
            species("Gabumon", 2), species("Garurumon", 3)))
        val team = resolver.resolve("taichi-adventure", listOf(3, 2))!!
        assertEquals(listOf("Greymon", "Gabumon"), team.members.map { it.species.name })
        assertEquals(ArenaPartnerOrigin.GUEST, team.members.last().origin)
        assertEquals("yamato", team.members.last().ownerId)
        assertFalse(team.members.first().inferred)
        assertEquals(2, team.members.map { it.partnerId }.distinct().size)
    }

    @Test fun missingStageArtScalesTheSamePartnerLineInsteadOfUnrelatedSpecies() {
        val resolver = TamerTeamResolver(catalog(), listOf(species("Greymon", 3),
            species("Shine Greymon", 5), species("Alphamon", 5)))
        val member = resolver.resolve("taichi-adventure", listOf(2))!!.members.single()
        assertEquals("Greymon", member.species.name)
        assertEquals(2, member.battleStage)
        assertTrue(member.scaled)
        assertFalse(member.inferred)
        assertNotNull(resolver.resolve("taichi-adventure", listOf(3)))
    }

    @Test fun missingStagesRetainDocumentedFormsWithScaledStats() {
        val resolver = TamerTeamResolver(catalog(), listOf(species("Plotmon", 2), species("Angewomon", 4),
            species("Pico Devimon", 2), species("Lady Devimon", 4)))
        val lower = resolver.resolve("mirei", listOf(2, 2))!!
        assertEquals(listOf("Angewomon", "Lady Devimon"), lower.members.map { it.species.name })
        assertTrue(lower.members.all { it.scaled && !it.inferred && it.battleStage == 2 })
        assertEquals(listOf(ArenaPartnerOrigin.OWNED, ArenaPartnerOrigin.OWNED), lower.members.map { it.origin })
        val actual = resolver.resolve("mirei", listOf(4, 4))!!
        assertFalse(actual.members.any { it.inferred })
    }

    @Test fun aliasMatchingPreservesSpeciesVariants() {
        assertEquals(BattleSpeciesIdentity.normalize("Gallantmon"), BattleSpeciesIdentity.normalize("Dukemon"))
        assertEquals(BattleSpeciesIdentity.normalize("Agumon (2006)"),
            BattleSpeciesIdentity.normalize("Agumon (2006 Anime Version)"))
        assertNotEquals(BattleSpeciesIdentity.normalize("Agumon"), BattleSpeciesIdentity.normalize("Agumon (2006)"))
        assertNotEquals(BattleSpeciesIdentity.normalize("Metal Greymon"),
            BattleSpeciesIdentity.normalize("Metal Greymon (Virus)"))
    }

    private val strike = TechniqueDefinition("hit", "Hit", TechniqueKind.MELEE, 1, maxRange = 30f,
        startupMillis = 34, activeMillis = 34, recoveryMillis = 34)
    private val special = TechniqueDefinition("special", "Special", TechniqueKind.SPECIAL, 50,
        maxRange = 30f, startupMillis = 1_200, activeMillis = 34, recoveryMillis = 34)
    private fun fighter(id: String, side: BattleSide) = CombatantDefinition(id, displayName = id,
        side = side, maxHealth = 10_000, maxEnergy = 500, attack = 100, defense = 40,
        movementSpeed = 0f, techniqueIds = listOf("hit"), specialTechniqueId = "special",
        decisionDelayMinMillis = 100_000, decisionDelayMaxMillis = 100_000)

    @Test fun aiUsesItsOwnFiniteStockAndCannotSpendPlayerItems() {
        val sim = BattleSimulator(BattleConfiguration(randomSeed = 11, itemCooldownMillis = 5_000),
            BattleTeam("a", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING).copy(initialHealth = 2_000))),
            listOf(strike, special),
            trainingItems = listOf(BattleItemDefinition("recovery", "Recovery", BattleItemKind.HEAL_HEALTH, 2, 3_000)),
            opposingItems = listOf(BattleItemDefinition("recovery", "Recovery", BattleItemKind.HEAL_HEALTH, 1, 3_000)),
            trainerPolicies = mapOf(BattleSide.OPPOSING to TrainerAiPolicy(reactionMillis = 34)))
        repeat(30) { sim.advance(34) }
        assertEquals(2, sim.snapshot().trainingItems.single().remaining)
        assertEquals(0, sim.snapshot().opposingItems.single().remaining)
        assertEquals(1, sim.snapshot().recentEvents.filterIsInstance<BattleEvent.ItemUsed>().size)
        assertEquals(OrderFailure.NOT_ALLIED, sim.issueOrder("b", TrainerAction.Defend()).reasonCode)
    }

    @Test fun opposingFinisherPublishesItsActualDamageAndRestoresForm() {
        val sim = BattleSimulator(BattleConfiguration(randomSeed = 12, strictFinisherEligibility = true),
            BattleTeam("a", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING).copy(
                blastMode = "FORM", blastTargetSpecies = "Greymon", blastFormSpecial = "Mega Flame"))),
            listOf(strike, special), trainerPolicies = mapOf(BattleSide.OPPOSING to
                TrainerAiPolicy(reactionMillis = 34, finisherHitChance = 1f)))
        var movie: BattleFinisherSnapshot? = null
        repeat(4_000) {
            sim.advance(34)
            sim.snapshot().finisher?.takeIf { it.impactCommitted }?.let { movie = it }
        }
        assertNotNull(movie)
        assertEquals("b", movie!!.leadId)
        assertEquals("Greymon", movie!!.resultSpecies)
        assertTrue(movie!!.damage > 0)
    }

    @Test fun seededTrainerDecisionsAreReplayable() {
        fun battle() = BattleSimulator(BattleConfiguration(randomSeed = 88),
            BattleTeam("a", BattleSide.ALLIED, listOf(fighter("a", BattleSide.ALLIED))),
            BattleTeam("b", BattleSide.OPPOSING, listOf(fighter("b", BattleSide.OPPOSING))),
            listOf(strike, special), trainerPolicies = mapOf(BattleSide.OPPOSING to TrainerAiPolicy()))
        val first = battle()
        val second = battle()
        repeat(3_000) { first.advance(34); second.advance(34) }
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test fun blankStoredIdsFallThroughToUsableIdentities() {
        val participant = OfflineBattleParticipant(null, "dim012_mon03", "Agumon", 1, 1, stage = 2,
            individualId = "", battleInstanceId = "")
        assertEquals("dim012_mon03", participant.stableId)
        val frozen = TamerBattleFactory.freeze(participant)
        assertEquals("dim012_mon03", frozen.stableId)
        assertEquals("dim012_mon03", frozen.battleInstanceId)
    }

    @Test fun outOfRangeStoredStagesAreCoercedBeforeValidation() {
        val player = TamerBattleFactory.freeze(
            OfflineBattleParticipant(null, "dim012_mon03", "Agumon", 1, 1, stage = 9, individualId = "stored-1"))
        assertEquals(5, player.stage)
        val foe = OfflineBattleParticipant(null, "dim012_mon04", "Greymon", 1, 1, stage = 5,
            battleInstanceId = "tamer:masaru:0", speciesName = "Greymon")
        val spec = ArenaMatchSpec("fixture-coerced", ArenaEntrant("player", "You", null, listOf(player), TrainerAiPolicy()),
            ArenaEntrant("masaru", "Masaru", "masaru", listOf(foe), TrainerAiPolicy()), ArenaDifficulty.NORMAL,
            BattleConfiguration(strictFinisherEligibility = true, itemCooldownMillis = 5_000))
        TamerBattleFactory.validate(spec)
    }

    @Test fun invalidSpecsNameTheProblemInsteadOfAFailedRequirement() {
        val bad = OfflineBattleParticipant(null, null, "", 1, 1, stage = 2, individualId = "", battleInstanceId = "")
        assertEquals("", bad.stableId)
        val foe = OfflineBattleParticipant(null, "dim012_mon04", "Greymon", 1, 1, stage = 5,
            battleInstanceId = "tamer:masaru:0", speciesName = "Greymon")
        val spec = ArenaMatchSpec("fixture-bad", ArenaEntrant("player", "You", null, listOf(bad), TrainerAiPolicy()),
            ArenaEntrant("masaru", "Masaru", "masaru", listOf(foe), TrainerAiPolicy()), ArenaDifficulty.NORMAL,
            BattleConfiguration(strictFinisherEligibility = true, itemCooldownMillis = 5_000))
        try {
            TamerBattleFactory.validate(spec)
            fail("Blank identities must be rejected")
        } catch (failure: IllegalArgumentException) {
            assertTrue(failure.message!!.contains("non-blank identity"))
        }
    }

    @Test fun tournamentAdvancementIsDeterministicAndDrawsHaveABoundedTiebreak() {
        val members = (0..7).map { ArenaEntrant("e$it", "Entry $it", null, emptyList(), TrainerAiPolicy()) }
        val run = ArenaTournament.create("run", members, 2, "Normal", 123)
        assertEquals(4, run.matches.size)
        assertEquals(123L, run.seed)
        assertEquals(run, ArenaTournament.create("run", members, 2, "Normal", 123))
        val finished = run.matches.fold(run) { current, match -> current.finish(match.id, match.leftId, "ALLIED_VICTORY") }
        val next = finished.advanceRound()
        assertEquals(2, next.matches.count { it.round == 1 })
        assertEquals(4, next.matches.count { it.round == 0 && it.winnerId != null })
        assertEquals(next, next.advanceRound())
    }
}
