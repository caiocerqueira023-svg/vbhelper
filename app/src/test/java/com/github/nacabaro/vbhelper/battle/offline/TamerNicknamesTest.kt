package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.screens.OfflineBattleParticipant
import com.github.nacabaro.vbhelper.screens.tamerArena.ArenaPresentation
import com.github.nacabaro.vbhelper.screens.tamerArena.TamerArenaState
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TamerNicknamesTest {
    private val nicknames = TamerNicknames.parse(File("src/main/assets/tamer_nicknames.tsv").readText())
    private fun art(name: String, stage: Int) = ArenaSpecies(name, stage, "fixture:$name", null,
        BattleAttribute.VACCINE, listOf("Signature"), null)
    private fun resolver(vararg arts: Pair<String, Int>) = TamerTeamResolver(
        TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText()),
        arts.map { art(it.first, it.second) }, nicknames)

    @Test fun nicknameFileParsesWithSourcesAndUniqueKeys() {
        assertTrue(nicknames.size >= 14)
        assertTrue(nicknames.values.all {
            it.nickname.isNotBlank() && it.sourceUri.startsWith("https://wikimon.net/")
        })
    }

    @Test fun everyNicknameAnchorsARealCatalogPartner() {
        val byId = TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText()).associateBy { it.id }
        assertTrue(nicknames.isNotEmpty())
        nicknames.values.forEach { entry ->
            val tamer = byId[entry.tamerId] ?: throw AssertionError("Unknown tamer ${entry.tamerId}")
            val anchors = listOfNotNull(tamer.primary, tamer.secondary) +
                TamerCanonicalPartners.companions[tamer.id].orEmpty()
            assertTrue("Orphan nickname ${entry.tamerId}/${entry.anchor}",
                anchors.any {
                    BattleSpeciesIdentity.normalize(it) == BattleSpeciesIdentity.normalize(entry.anchor)
                })
        }
    }

    @Test fun documentedNicknamesResolveOnTheirPartners() {
        val r = resolver("Agumon" to 2, "V-dramon" to 3, "War Greymon" to 5, "Metal Garurumon" to 5,
            "Guilmon" to 2, "Plotmon" to 2, "Gaomon" to 2, "Piyomon" to 2,
            "Black War Greymon (X-Antibody)" to 5, "Numemon" to 2,
            "Mega Seadramon" to 5, "Lilimon" to 5)
        fun nickname(id: String, stages: List<Int>): String? =
            r.resolve(id, stages)?.members?.firstOrNull()?.nickname
        assertEquals("Digimaru", nickname("taiga", listOf(2)))
        assertEquals("V.V.", nickname("rina", listOf(3)))
        assertEquals("Zeromaru", nickname("taichi-vtamer", listOf(3)))
        assertEquals("Warg", nickname("hideto", listOf(5)))
        assertEquals("Yukimura", nickname("kouta-next", listOf(2)))
        assertEquals("Rikka", nickname("himari", listOf(2)))
        assertEquals("Sashenka", nickname("nicolai", listOf(2)))
        assertEquals("Digitorin", nickname("akiho", listOf(2)))
        assertEquals("Black", nickname("yuuya", listOf(5)))
        assertEquals("Catherine", nickname("lili", listOf(2)))
        assertEquals("AKAGI", nickname("zudokan", listOf(5)))
        assertEquals("DINOGON", TamerNicknames.forPartner(nicknames, "zudokan", "Triceramon")?.nickname)
        // Nicknames never leak across tamers sharing a species line.
        assertNull(r.resolve("nokia", listOf(2))?.members?.firstOrNull()?.nickname)
    }

    @Test fun borrowedPartnersKeepTheirOwnNames() {
        val r = resolver("Agumon" to 2, "Gabumon" to 2, "V-dramon" to 3, "War Greymon" to 5, "Metal Garurumon" to 5)
        val hideto = r.resolve("hideto", listOf(5, 5))!!
        assertEquals(listOf("Warg", "Melga"), hideto.members.map { it.nickname })
        val nicolai = r.resolve("nokia", listOf(2, 2))!!
        // Nokia's own Agumon has no nickname even though other tamers' Agumon do.
        assertNull(nicolai.members[0].nickname)
    }

    @Test fun battlesAndBriefingsUseNicknamesWhileMatchingKeepsSpecies() {
        val r = resolver("Agumon" to 2)
        val team = r.resolve("taiga", listOf(2))!!
        val fighter = TamerBattleLoadouts.participants(team, r).single()
        assertEquals("Digimaru", fighter.displayName)
        assertEquals("Agumon", fighter.speciesName)
        assertEquals("Digimaru", r.memberLabel(team.members.single()))
        assertEquals("Agumon", r.memberLabel(team.members.single().copy(nickname = null)))
    }

    @Test fun loadedAlterBUsesNoirOnlyForShomasSoloForm() {
        val r = resolver("Gaioumon" to 5, "Kuzuhamon" to 5, "Omegamon Alter-B" to 5)
        val solo = r.resolve("shoma", listOf(5))!!
        assertEquals("Omegamon Alter-B", solo.members.single().species.name)
        assertEquals("Noir", solo.members.single().nickname)
        assertEquals("Noir", r.memberLabel(solo.members.single()))
        val fighter = TamerBattleLoadouts.participants(solo, r).single()
        assertEquals("Noir", fighter.displayName)
        assertEquals("Omegamon Alter-B", fighter.speciesName)

        val duo = r.resolve("shoma", listOf(5, 5))!!
        assertTrue(duo.members.all { it.nickname == null })
        assertEquals(listOf("Gaioumon", "Kuzuhamon"), duo.members.map(r::memberLabel))
        val fighters = TamerBattleLoadouts.participants(duo, r)
        assertEquals(listOf("Gaioumon", "Kuzuhamon"), fighters.map { it.displayName })
        assertEquals("Omegamon Alter-B", fighters.first().jogressResultSpecies)
        assertNull(r.resolve("takumi-rearise", listOf(5))!!.members.single().nickname)
    }

    @Test fun missingAlterBArtworkKeepsGaioumonWithoutNoirNickname() {
        val r = resolver("Gaioumon" to 5, "Kuzuhamon" to 5)
        val solo = r.resolve("shoma", listOf(5))!!
        assertEquals("Gaioumon", solo.members.single().species.name)
        assertNull(solo.members.single().nickname)
        assertEquals("Gaioumon", r.memberLabel(solo.members.single()))
        assertEquals("Gaioumon", TamerBattleLoadouts.participants(solo, r).single().displayName)
    }

    @Test fun nicknameSearchFindsTamersByPartnerName() {
        val r = TamerTeamResolver(TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText()),
            listOf(art("Agumon", 2)), nicknames)
        val state = TamerArenaState(loading = false, resolver = r,
            partners = listOf(OfflineBattleParticipant(null, "player", "Partner", 1, 1, stage = 2)),
            selectedPartnerIds = listOf("player"))
        assertTrue(ArenaPresentation.roster(state, "digimaru", null, false).any { it.tamer.id == "taiga" })
        assertTrue(ArenaPresentation.roster(state, "sashenka", null, false).any { it.tamer.id == "nicolai" })
        assertTrue(ArenaPresentation.roster(state, "agumon", null, false).any { it.tamer.id == "taiga" })
    }
}
