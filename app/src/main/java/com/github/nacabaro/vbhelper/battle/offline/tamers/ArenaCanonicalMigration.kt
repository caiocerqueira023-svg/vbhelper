package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity

class CanonicalTeamUnavailable(val tamerName: String) : IllegalStateException("Canonical partner artwork is needed for $tamerName")

/** Correct unplayed legacy NPC setups without rerolling the bracket or changing the registered player. */
object ArenaCanonicalMigration {
    fun refresh(run: ArenaTournament, resolver: TamerTeamResolver): ArenaTournament {
        require(run.canonRevision in 0..TamerCanonicalPartners.REVISION)
        if (run.canonRevision == TamerCanonicalPartners.REVISION || run.withdrawn || run.championId != null) return run
        val player = run.entrants.single { it.id == ArenaRepository.PLAYER_ID }
        val stages = player.members.map { it.stage }
        val difficulty = ArenaDifficulty.valueOf(run.difficulty.uppercase())
        val entrants = run.entrants.map { entry ->
            if (entry.id == ArenaRepository.PLAYER_ID) entry else {
                val id = requireNotNull(entry.tamerId)
                val team = resolver.resolve(id, stages) ?: throw CanonicalTeamUnavailable(entry.name)
                entry.copy(members = TamerBattleLoadouts.participants(team, resolver),
                    policy = TamerBattleLoadouts.policy(team.tamer.style, difficulty))
            }
        }
        val targets = entrants.flatMap { it.members }.flatMap { listOfNotNull(it.blastTargetSpecies, it.jogressResultSpecies) }
            .distinctBy(BattleSpeciesIdentity::normalize)
        val art = targets.mapNotNull { name -> resolver.speciesNamed(name)
            ?: run.resultSpecies.firstOrNull { BattleSpeciesIdentity.normalize(it.name) == BattleSpeciesIdentity.normalize(name) } }
        return run.copy(entrants = entrants, resultSpecies = art, canonRevision = TamerCanonicalPartners.REVISION)
    }
}
