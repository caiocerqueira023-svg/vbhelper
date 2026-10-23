package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile

/** Narrow extraction cleanup. Species names, mode notation, and digital/game origins are research content. */
object ResearchText {
    private val noteMarker = Regex("\\[N\\s*\\d+\\]", RegexOption.IGNORE_CASE)
    private val productionTrivia = Regex("(?:digimon\\s+(?:design|original)\\s+contest|original\\s+digimon\\s+contest)", RegexOption.IGNORE_CASE)

    fun clean(text: String): String = text.replace(noteMarker, "").trim()
    private fun sentences(text: String): List<String> = clean(text).split(Regex("(?<=[.!?])\\s+"))
    fun description(text: String): String = sentences(text).filterNot { productionTrivia.containsMatchIn(it) }.joinToString(" ").trim()
    fun productionNotes(text: String): List<String> = sentences(text).filter { productionTrivia.containsMatchIn(it) }
    fun moves(moves: List<String>): List<String> = moves.map(::clean).filter(String::isNotBlank).distinct()
    fun profile(profile: SpeciesProfile): SpeciesProfile = profile.copy(
        profileDescription = profile.profileDescription?.let(::description), specialMoves = moves(profile.specialMoves)
    )
}
