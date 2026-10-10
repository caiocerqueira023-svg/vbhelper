package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import com.github.nacabaro.vbhelper.species.SpeciesDatabaseDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TamerTeamConstructionSweepTest {
    @Test fun everyResolvableTeamBuildsASimulatorAtEveryStage() {
        val root = File("src/main/assets")
        val metadata = Gson().fromJson(File(root, "species.json").readText(), SpeciesDatabaseDto::class.java)
        val sprites = File(root, "battle_sprites/extracted_assets/sprites")
        val species = metadata.species.filterKeys { it != "0" }.flatMap { (card, entries) ->
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
        val roster = TamerCatalog.parse(File(root, "tamers.tsv").readText())
        val resolver = TamerTeamResolver(roster, species)
        val failures = mutableListOf<String>()
        var checked = 0
        for (tamer in roster) for (stage in 0..5) for (format in 1..2) {
            val stages = List(format) { stage }
            val team = resolver.resolve(tamer.id, stages) ?: continue
            checked++
            try {
                val participants = TamerBattleLoadouts.participants(team, resolver)
                val opponent = ArenaEntrant(team.tamer.id, team.tamer.name, team.tamer.id, participants,
                    TamerBattleLoadouts.policy(team.tamer.style, ArenaDifficulty.NORMAL))
                val ally = opponent.copy(id = "mirror",
                    members = participants.map { it.copy(battleInstanceId = "mirror:${it.stableId}") })
                val spec = ArenaMatchSpec("sweep:${team.tamer.id}:$stage:$format", ally, opponent,
                    ArenaDifficulty.NORMAL,
                    BattleConfiguration(strictFinisherEligibility = true, itemCooldownMillis = 5_000))
                val simulator = TamerBattleFactory.create(spec, autoplayLeft = true)
                repeat(20) { simulator.advance(34) }
            } catch (failure: Throwable) {
                val frame = failure.stackTrace.firstOrNull { it.className.contains("nacabaro") }
                failures += "${tamer.id} stage=$stage format=${format}x$format : $failure at $frame"
            }
        }
        assertTrue("Checked $checked resolvable teams, failures:\n${failures.joinToString("\n")}", failures.isEmpty())
        println("Sweep: $checked resolvable team/stage/format combinations constructed cleanly")
    }
}
