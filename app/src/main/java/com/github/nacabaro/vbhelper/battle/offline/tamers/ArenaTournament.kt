package com.github.nacabaro.vbhelper.battle.offline.tamers

import kotlin.random.Random

data class ArenaBracketMatch(
    val id: String,
    val round: Int,
    val position: Int,
    val leftId: String,
    val rightId: String,
    val seed: Long,
    val winnerId: String? = null,
    val outcome: String? = null,
)

data class ArenaTournament(
    val id: String,
    val entrants: List<ArenaEntrant>,
    val format: Int,
    val difficulty: String,
    val seed: Long,
    val matches: List<ArenaBracketMatch>,
    val title: String = "All franchises",
    val championId: String? = null,
    val withdrawn: Boolean = false,
    val configuration: com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration =
        com.github.nacabaro.vbhelper.battle.offline.core.BattleConfiguration(itemCooldownMillis = 5_000, strictFinisherEligibility = true),
    val resultSpecies: List<ArenaSpecies> = emptyList(),
    val contentVersion: Int = TamerCatalog.VERSION,
    val aiVersion: Int = 1,
    val canonRevision: Int = TamerCanonicalPartners.REVISION,
) {
    fun finish(matchId: String, winnerId: String, outcome: String): ArenaTournament {
        val match = matches.single { it.id == matchId }
        require(winnerId == match.leftId || winnerId == match.rightId)
        require(match.winnerId == null || match.winnerId == winnerId) { "A settled bracket cannot be rewritten" }
        return copy(matches = matches.map { if (it.id == matchId) it.copy(winnerId = winnerId, outcome = outcome) else it })
    }

    fun advanceRound(): ArenaTournament {
        if (championId != null || withdrawn) return this
        val round = matches.maxOf { it.round }
        val current = matches.filter { it.round == round }.sortedBy { it.position }
        if (current.any { it.winnerId == null }) return this
        val winners = current.map { requireNotNull(it.winnerId) }
        if (winners.size == 1) return copy(championId = winners.single())
        val next = winners.chunked(2).mapIndexed { index, pair ->
            ArenaBracketMatch("$id:r${round + 1}:$index", round + 1, index, pair[0], pair[1],
                Random(seed xor ((round + 1L) shl 32) xor index.toLong()).nextLong())
        }
        return copy(matches = matches + next)
    }

    companion object {
        fun create(id: String, entrants: List<ArenaEntrant>, format: Int, difficulty: String, seed: Long,
            title: String = "All franchises"): ArenaTournament {
            require(entrants.size in setOf(8, 16) && entrants.map { it.id }.distinct().size == entrants.size) {
                "Tournaments need 8 or 16 distinct entrants, got ${entrants.size}"
            }
            require(format in 1..2) { "Tournament formats are 1x1 and 2x2" }
            val shuffled = entrants.shuffled(Random(seed))
            val matches = shuffled.chunked(2).mapIndexed { index, pair ->
                ArenaBracketMatch("$id:r0:$index", 0, index, pair[0].id, pair[1].id,
                    Random(seed xor index.toLong()).nextLong())
            }
            return ArenaTournament(id, entrants, format, difficulty, seed, matches, title)
        }
    }
}
