package com.github.nacabaro.vbhelper.species

enum class SpeciesArtworkSource { DOT, VB_CHOICE, VB_IMPORTED, VB_ASSET, NONE }

object SpeciesArtworkPolicy {
    fun choose(preference: SpritePreference, dot: Boolean, choice: Boolean, imported: Boolean, asset: Boolean): SpeciesArtworkSource = when {
        preference == SpritePreference.DOT_FIRST && dot -> SpeciesArtworkSource.DOT
        choice -> SpeciesArtworkSource.VB_CHOICE
        imported -> SpeciesArtworkSource.VB_IMPORTED
        asset -> SpeciesArtworkSource.VB_ASSET
        dot -> SpeciesArtworkSource.DOT
        else -> SpeciesArtworkSource.NONE
    }
}
