package com.github.nacabaro.vbhelper.screens.tamerArena

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ArenaPresentationTest {
    private fun art(name: String) = ArenaSpecies(name, 5, "fixture", null, BattleAttribute.NONE, emptyList(), null)
    private fun state() = TamerArenaState(loading = false,
        resolver = TamerTeamResolver(TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText()), listOf(art("Shine Greymon"))),
        partners = listOf(OfflineBattleParticipant(null, "player", "Partner", 1, 1, stage = 5)), selectedPartnerIds = listOf("player"))

    @Test fun readyFilterRemovesUnusableOpponentsWithoutLosingNameAliasSearch() {
        val state = state()
        val filtered = ArenaPresentation.roster(state, "Marcus", null, true)
        assertEquals(listOf("masaru"), filtered.map { it.tamer.id })
        assertNotNull(filtered.single().team)
        assertTrue(ArenaPresentation.roster(state, "Tsurugi", null, true).isEmpty())
        val unavailable = ArenaPresentation.roster(state, "Tsurugi", null, false).single { it.tamer.id == "tsurugi" }
        assertEquals(listOf("Victory Greymon"), unavailable.missingArtwork)
    }

    @Test fun noPartnerStillAllowsBrowsingInsteadOfAnEmptyReadyList() {
        val state = state().copy(partners = emptyList(), selectedPartnerIds = emptyList())
        assertTrue(ArenaPresentation.roster(state, "", null, true).isNotEmpty())
        assertTrue(ArenaPresentation.roster(state, "", null, true).all { it.team == null })
    }

    @Test fun displayedBaseRewardMatchesSettlementRuleAndMixedTeamTier() {
        assertEquals(640, ArenaPresentation.baseReward(state()))
        val second = OfflineBattleParticipant(null, "second", "Second", 1, 1, stage = 2)
        val state = state().copy(partners = state().partners + second, selectedPartnerIds = listOf("player", "second"), format = 2)
        assertEquals(1_280, ArenaPresentation.baseReward(state))
        assertEquals(1_920, ArenaPresentation.baseReward(state.copy(difficulty = ArenaDifficulty.EXPERT)))
    }

    @Test fun artworkSourceDoesNotExcludeTeamsFromCupsOrDemoteThemInTheRoster() {
        val catalog = TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText())
        val primary = art("Shine Greymon").copy(cardCharacterId = 1L, assetId = null, isCatalogDefinition = true)
        val fallback = art("Shine Greymon").copy(assetId = "fixture", cardCharacterId = null, modeFallbackArtwork = true)
        val resolver = TamerTeamResolver(catalog, listOf(primary, fallback))
        val team = resolver.resolve("masaru", listOf(5))!!
        assertEquals("Shine Greymon", team.members.single().species.name)
        // Retired mode flags on saved artwork cannot restrict cup participation.
        val fallbackOnly = TamerTeamResolver(catalog, listOf(fallback))
        assertNotNull(fallbackOnly.resolveForCup("masaru", listOf(5)))
        assertEquals(listOf(fallback), fallbackOnly.forCups().species)
        val mixed = TamerArenaState(loading = false, resolver = TamerTeamResolver(catalog,
            listOf(primary.copy(name = "Agumon"), fallback.copy(name = "Shine Greymon"))),
            partners = listOf(OfflineBattleParticipant(null, "player", "Partner", 1, 1, stage = 5)),
            selectedPartnerIds = listOf("player"))
        val roster = ArenaPresentation.roster(mixed, "", null, false)
        assertNotNull(roster.single { it.tamer.id == "masaru" }.team)
        assertTrue(roster.first().team != null)
    }
}
