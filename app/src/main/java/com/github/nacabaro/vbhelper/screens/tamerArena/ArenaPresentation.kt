package com.github.nacabaro.vbhelper.screens.tamerArena

import com.github.nacabaro.vbhelper.battle.offline.tamers.*

data class ArenaRosterEntry(val tamer: TamerDefinition, val team: ResolvedTamerTeam?,
    val preview: ArenaSpecies?, val missingArtwork: List<String>)

object ArenaPresentation {
    fun roster(state: TamerArenaState, query: String, series: String?, availableOnly: Boolean): List<ArenaRosterEntry> {
        val resolver = state.resolver ?: return emptyList()
        val stages = state.stages
        return resolver.tamers.asSequence().filter {
            it.searchMatches(query) || resolver.nicknameMatches(it.id, query)
        }.filter { series == null || it.series == series }
            .map { tamer ->
                val team = if (state.teamReady) resolver.resolve(tamer.id, stages) else null
                ArenaRosterEntry(tamer, team, team?.members?.firstOrNull()?.species ?: resolver.speciesNamed(tamer.primary),
                    if (state.teamReady && team == null) resolver.missingArtwork(tamer.id, stages) else emptyList())
            }.filter { !availableOnly || !state.teamReady || it.team != null }
            .sortedWith(compareByDescending<ArenaRosterEntry> { it.team != null }.thenBy { it.tamer.name }).toList()
    }

    fun baseReward(state: TamerArenaState): Int = if (state.teamReady)
        ArenaRewards.victoryBits(state.stages.max(), state.format, state.difficulty) else 0

    fun totalRounds(run: ArenaTournament): Int = if (run.entrants.size == 16) 4 else 3

    fun defaultRound(run: ArenaTournament): Int = run.matches.firstOrNull {
        it.winnerId == null && (it.leftId == ArenaRepository.PLAYER_ID || it.rightId == ArenaRepository.PLAYER_ID)
    }?.round ?: run.matches.maxOf { it.round }
}

/** Pure lobby actions let native layout tests exercise the actual production surface. */
data class ArenaUiActions(
    val onBack: () -> Unit = {},
    val onReload: () -> Unit = {},
    val onSelectTamer: (String?) -> Unit = {},
    val onFormat: (Int) -> Unit = {},
    val onDifficulty: (ArenaDifficulty) -> Unit = {},
    val onPickPartner: (Int) -> Unit = {},
    val onChallenge: (String) -> Unit = {},
    val onStartCup: (Int, String?) -> Unit = { _, _ -> },
    val onResumeCup: (String) -> Unit = {},
    val onShowBracket: (String?) -> Unit = {},
    val onWithdraw: (String) -> Unit = {},
    val onDismissError: () -> Unit = {},
)
