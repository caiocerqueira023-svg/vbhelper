package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.species.SpeciesDatabaseDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TamerRosterAssetsTest {
    private fun realSpecies(): List<ArenaSpecies> {
        val root = File("src/main/assets")
        val metadata = Gson().fromJson(File(root, "species.json").readText(), SpeciesDatabaseDto::class.java)
        val sprites = File(root, "battle_sprites/extracted_assets/sprites")
        return metadata.species.filterKeys { it != "0" }.flatMap { (card, entries) ->
            entries.mapNotNull { (slot, entry) ->
                val id = "dim${card.padStart(3, '0')}_mon${slot.padStart(2, '0')}"
                if (!File(sprites, "$id/${id}_01.png").exists() || !File(sprites, "$id/${id}_11.png").exists()) return@mapNotNull null
                val stage = when (entry.level) {
                    "Baby I" -> 0; "Baby II" -> 1; "Child" -> 2; "Adult", "Armor" -> 3; "Perfect" -> 4; "Ultimate" -> 5
                    else -> return@mapNotNull null
                }
                ArenaSpecies(entry.name, stage, id, null, BattleAttribute.NONE, entry.specialMoves, null)
            }
        }
    }

    private fun realResolver() = TamerTeamResolver(
        TamerCatalog.parse(File("src/main/assets/tamers.tsv").readText()), realSpecies())

    @Test fun bundledMainPartnerLinesProducePlayableTeamsAndValidCombatDefinitions() {
        val root = File("src/main/assets")
        val species = realSpecies()
        val roster = TamerCatalog.parse(File(root, "tamers.tsv").readText())
        val resolver = TamerTeamResolver(roster, species)
        val singles = roster.mapNotNull { resolver.resolve(it.id, listOf(2)) }
        val doubles = roster.mapNotNull { resolver.resolve(it.id, listOf(3, 3)) }
        assertTrue("Expected broad existing-asset coverage, got ${singles.size}", singles.size >= 60)
        assertTrue("Expected many playable doubles, got ${doubles.size}", doubles.size >= 40)
        for (id in listOf("taichi-adventure", "takato", "ruki", "hiro", "eiji", "leon", "kouta-chronicle", "masaru", "thoma")) {
            assertNotNull("Missing existing rookie assets for $id", resolver.resolve(id, listOf(2)))
        }
        for (team in singles + doubles) {
            assertTrue(team.members.all { it.sourceUri != null && !it.inferred })
            assertTrue(team.members.all { it.scaled == (it.battleStage != it.species.stage) })
            if (team.members.size == 2) assertTrue(team.members[0].componentPartnerIds.intersect(team.members[1].componentPartnerIds).isEmpty())
            val participants = TamerBattleLoadouts.participants(team, resolver)
            val opponent = ArenaEntrant(team.tamer.id, team.tamer.name, team.tamer.id, participants,
                TamerBattleLoadouts.policy(team.tamer.style, ArenaDifficulty.NORMAL))
            val ally = opponent.copy(id = "mirror", members = participants.map { it.copy(battleInstanceId = "mirror:${it.stableId}") })
            val spec = ArenaMatchSpec("fixture:${team.tamer.id}", ally, opponent, ArenaDifficulty.NORMAL,
                BattleConfiguration(strictFinisherEligibility = true, itemCooldownMillis = 5_000))
            val simulator = TamerBattleFactory.create(spec, autoplayLeft = true)
            repeat(10) { simulator.advance(34) }
            assertEquals(participants.size, simulator.snapshot().opposingMembers.size)
        }
        println("Tamer assets: ${roster.size} roster entries, ${singles.size} rookie-tier 1v1 teams, ${doubles.size} champion-tier 2v2 teams")
    }

    @Test fun documentedFusionDuosEquipTheirCombinedFormWithRealArtwork() {
        val resolver = realResolver()
        fun team(id: String, stages: List<Int>): ResolvedTamerTeam =
            resolver.resolve(id, stages) ?: throw AssertionError("No $id team at $stages with real artwork")
        fun fusion(id: String, stages: List<Int>): String? =
            TamerBattleLoadouts.participants(team(id, stages), resolver)[0].jogressResultSpecies
        // Shoutmon X2 ships without bundled art: the owned pair still fights,
        // and the fusion equips as soon as the user imports its card.
        val taiki = team("taiki", listOf(2, 2))
        assertEquals(listOf("Shoutmon", "Ballistamon"), taiki.members.map { it.species.name })
        assertNull(fusion("taiki", listOf(2, 2)))
        // Kiriha's base form ships without art, so the art-backed DigiXros form
        // scales down and keeps the lead; the absorbed component cannot field
        // beside its own combined form, so an owned army companion fills the slot.
        val kiriha = team("kiriha", listOf(3, 3))
        assertEquals("Metal Greymon (2010 Anime Version)", kiriha.members[0].species.name)
        assertTrue(kiriha.members[0].scaled)
        assertEquals("kiriha", kiriha.members[1].ownerId)
        assertEquals(ArenaPartnerOrigin.OWNED, kiriha.members[1].origin)
        assertNull(fusion("kiriha", listOf(3, 3)))
        // Shoma fields Gaioumon and Kuzuhamon with bundled art; Alter-B has
        // no bundled sprite, so the movie stays gated while the pair still
        // fights, and the solo tier falls back to Gaioumon at tier.
        val shoma = team("shoma", listOf(5, 5))
        assertEquals(listOf("Gaioumon", "Kuzuhamon"), shoma.members.map { it.species.name })
        assertNull(fusion("shoma", listOf(5, 5)))
        assertEquals("Gaioumon", team("shoma", listOf(5)).members.single().species.name)
        assertEquals("ken", team("daisuke", listOf(3, 3)).members[1].ownerId)
        assertEquals("Paildramon", fusion("daisuke", listOf(3, 3)))
        assertEquals("daisuke", team("ken", listOf(3, 3)).members[1].ownerId)
        assertEquals("Paildramon", fusion("ken", listOf(3, 3)))
        assertEquals("hikari", team("miyako", listOf(3, 3)).members[1].ownerId)
        assertEquals("Silphymon", fusion("miyako", listOf(3, 3)))
        assertEquals("miyako", team("hikari", listOf(3, 3)).members[1].ownerId)
        assertEquals("Silphymon", fusion("hikari", listOf(3, 3)))
        assertEquals("takeru", team("iori", listOf(3, 3)).members[1].ownerId)
        assertEquals("Shakkoumon", fusion("iori", listOf(3, 3)))
        assertEquals("iori", team("takeru", listOf(3, 3)).members[1].ownerId)
        assertEquals("Shakkoumon", fusion("takeru", listOf(3, 3)))
    }
}
