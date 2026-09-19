package com.github.nacabaro.vbhelper.species

data class SpeciesDatabaseDto(
    val version: Int = 0,
    val species: Map<String, Map<String, SpeciesEntryDto>> = emptyMap()
)

data class SpeciesEntryDto(
    val name: String,
    val level: String? = null,
    val type: String? = null,
    val profile: String? = null,
    val specialMoves: List<String> = emptyList()
)

/** A short source dialogue set used as a style and interaction reference. */
data class SpeciesConversationExchange(
    val tamer: String = "",
    val digimon: String = ""
)

data class SpeciesConversationEntry(
    val characterId: String = "",
    val speciesName: String = "",
    val opening: String? = null,
    val exchanges: List<SpeciesConversationExchange> = emptyList()
)

data class SpeciesConversationDatabase(
    val version: Int = 1,
    val source: String = "",
    val entries: List<SpeciesConversationEntry> = emptyList()
)
