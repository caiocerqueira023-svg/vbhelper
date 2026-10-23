package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog

/**
 * Maps the game's own species-name table onto bundled canonical identities. Duplicate names resolve
 * to the lowest species id and never to a different canonical species; a name the catalog cannot
 * resolve is simply absent, so sending it is blocked rather than substituted.
 */
internal object WorldDsSpeciesCrosswalk {
    fun dsIdsByCanonical(table: WorldDsSpeciesTable, catalog: CanonicalSpeciesCatalog): Map<String, Long> {
        val resolved = mutableMapOf<String, Long>()
        for (speciesId in 0 until WorldDsSaveReader.SPECIES_COUNT) {
            val name = table.name(speciesId.toLong()) ?: continue
            val canonical = catalog.resolve(name) ?: continue
            val existing = resolved[canonical.id]
            if (existing == null || speciesId.toLong() < existing) resolved[canonical.id] = speciesId.toLong()
        }
        return resolved
    }
}