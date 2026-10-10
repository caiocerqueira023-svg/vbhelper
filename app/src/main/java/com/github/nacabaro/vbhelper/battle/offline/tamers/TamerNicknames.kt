package com.github.nacabaro.vbhelper.battle.offline.tamers

import com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity

data class PartnerNickname(val tamerId: String, val anchor: String, val nickname: String, val sourceUri: String,
    /** Optional form restriction for names that must not carry over to an unfused partner. */
    val formSpecies: String? = null)

/** Names tamers actually use for their partners. Keyed by tamer and partner
 * anchor, so nicknames survive form changes, stat scaling and guest slots,
 * unless the entry explicitly restricts the nickname to one species form. */
object TamerNicknames {
    const val ASSET_PATH = "tamer_nicknames.tsv"

    fun parse(text: String): Map<Pair<String, String>, PartnerNickname> {
        val entries = text.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line ->
                val columns = line.split('|')
                require(columns.size in 4..5) { "Invalid nickname entry: ${columns.firstOrNull()}" }
                val (id, anchor, nickname, source) = columns
                require(id.matches(Regex("[a-z0-9-]+"))) { "Invalid tamer identity: $id" }
                require(anchor.isNotBlank() && nickname.isNotBlank() && source.isNotBlank()) {
                    "Invalid nickname entry: $id"
                }
                PartnerNickname(id, anchor, nickname, "https://wikimon.net/$source",
                    formSpecies = columns.getOrNull(4)?.takeIf(String::isNotBlank))
            }.toList()
        val keys = entries.map { it.tamerId to BattleSpeciesIdentity.normalize(it.anchor) }
        require(keys.distinct().size == keys.size) { "Duplicate nickname entry" }
        return keys.zip(entries).toMap()
    }

    fun forPartner(
        nicknames: Map<Pair<String, String>, PartnerNickname>,
        tamerId: String,
        anchor: String,
        species: String? = null,
    ): PartnerNickname? = nicknames[tamerId to BattleSpeciesIdentity.normalize(anchor)]?.takeIf { entry ->
        entry.formSpecies == null || species?.let(BattleSpeciesIdentity::normalize) ==
            BattleSpeciesIdentity.normalize(entry.formSpecies)
    }
}
