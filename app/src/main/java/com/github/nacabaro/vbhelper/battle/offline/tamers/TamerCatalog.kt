package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.BattleStrategy
import java.util.Locale

enum class ArenaPartnerOrigin { OWNED, ASSOCIATED, GUEST }
enum class TamerStyle(val strategy: BattleStrategy) {
    RUSH(BattleStrategy.AGGRESSIVE), TACTICAL(BattleStrategy.BALANCED), RANGED(BattleStrategy.RANGED),
    GUARD(BattleStrategy.DEFENSIVE), SUPPORT(BattleStrategy.SUPPORT), CONTROL(BattleStrategy.CONSERVATIVE),
    BALANCED(BattleStrategy.BALANCED)
}
enum class ArenaDifficulty(val rewardMultiplier: Int) { CASUAL(1), NORMAL(2), EXPERT(3) }

data class TamerDefinition(
    val id: String,
    val name: String,
    val aliases: List<String>,
    val series: String,
    val primary: String,
    val secondary: String?,
    val secondaryOrigin: ArenaPartnerOrigin,
    val guestId: String?,
    val style: TamerStyle,
    val sourceUri: String,
) {
    fun searchMatches(query: String): Boolean = query.trim().lowercase(Locale.ROOT).let { needle ->
        needle.isEmpty() || (listOf(name, series, primary, secondary.orEmpty()) + aliases)
            .any { it.lowercase(Locale.ROOT).contains(needle) }
    }
}

/** Names/relationships are sourced; tactical styles and inferred lines are arena adaptations. */
object TamerCatalog {
    const val VERSION = 1
    const val ASSET_PATH = "tamers.tsv"

    fun parse(text: String): List<TamerDefinition> {
        val entries = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            val columns = line.split('|')
            require(columns.size == 11) { "Invalid tamer entry: ${columns.firstOrNull()}" }
            require(columns[0].matches(Regex("[a-z0-9-]+"))) { "Invalid tamer identity" }
            require(columns[1].isNotBlank() && columns[3].isNotBlank() && columns[4].isNotBlank())
            TamerDefinition(columns[0], columns[1], columns[2].split(';').filter(String::isNotBlank), columns[3],
                columns[4], columns[5].takeIf(String::isNotBlank),
                ArenaPartnerOrigin.valueOf(columns[6].ifBlank { "OWNED" }), columns[7].takeIf(String::isNotBlank),
                TamerStyle.valueOf(columns[8]),
                "https://wikimon.net/${columns[10]}")
        }.toList()
        require(entries.map { it.id }.distinct().size == entries.size) { "Duplicate tamer identity" }
        val ids = entries.map { it.id }.toSet()
        require(entries.all { it.guestId == null || it.guestId in ids }) { "Unknown guest tamer" }
        return entries
    }
}
