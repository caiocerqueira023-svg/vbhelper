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
