package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog

data class NativeSpeciesHint(val sourceId: String, val documentKey: String, val gameId: String,
    val formatProfile: String, val codecVersion: Int, val nativeIdentity: String,
    val nativeSpeciesId: Long, val canonicalSpeciesId: String)

/** Previously reviewed individual mappings may be reused automatically, never generalized to unrelated IDs. */
object NativeSpeciesResolution {
    fun reviewedIdentity(sourceId: String, documentKey: String, gameId: String, profile: String,
        codec: Int, identity: String?, speciesId: Long, hints: List<NativeSpeciesHint>): String? {
        if (identity.isNullOrBlank()) return null
        return hints.filter { (it.sourceId == sourceId || it.documentKey == documentKey) &&
            it.gameId == gameId && it.formatProfile == profile && it.codecVersion == codec &&
            it.nativeIdentity == "native:$identity" && it.nativeSpeciesId == speciesId }
            .map { it.canonicalSpeciesId }.distinct().singleOrNull()
    }

    /**
     * The game's own species table names a species before any review when the profile matches.
     * A name the catalog cannot resolve, an unused slot and any other game, profile or codec
     * version stay unidentified; a review is never implied by this lookup.
     */
    fun tableIdentity(gameId: String, profile: String, codec: Int, speciesId: Long,
        table: WorldDsSpeciesTable?, catalog: CanonicalSpeciesCatalog): String? {
        val source = table ?: return null
        if (gameId != source.gameId || profile != source.formatProfile || codec != source.codecVersion) return null
        val name = source.name(speciesId) ?: return null
        val species = catalog.resolve(name) ?: return null
        return species.id.takeIf { species.kind == "DIGIMON" }
    }
}
