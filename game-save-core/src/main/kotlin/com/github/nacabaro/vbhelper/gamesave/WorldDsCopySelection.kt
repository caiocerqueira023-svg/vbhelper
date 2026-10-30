package com.github.nacabaro.vbhelper.gamesave

/**
 * World DS copy authority facade over [NdsStoryCopySelection]. Copy authority is either an exact
 * user review or, failing that, the game-written newest copy; profile matching now comes from the
 * registered layout registry instead of a World DS literal.
 */
object WorldDsCopySelection {
    fun latestCopy(observation: SaveObservation): Int? = NdsStoryCopySelection.latestCopy(observation)

    fun authoritativeCopy(observation: SaveObservation): Int? = NdsStoryCopySelection.authoritativeCopy(observation)

    fun recordsForCopy(observation: SaveObservation, copy: Int): List<ObservedDigimon> =
        NdsStoryCopySelection.recordsForCopy(observation, copy)

    fun apply(observation: SaveObservation, reviewedRevision: String?, copy: Int?): SaveObservation =
        NdsStoryCopySelection.apply(observation, reviewedRevision, copy)

    fun applyAutomatic(observation: SaveObservation, copy: Int? = NdsStoryCopySelection.latestCopy(observation)): SaveObservation =
        NdsStoryCopySelection.applyAutomatic(observation, copy)
}
