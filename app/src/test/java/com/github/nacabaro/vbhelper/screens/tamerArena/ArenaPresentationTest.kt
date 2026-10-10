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
}
