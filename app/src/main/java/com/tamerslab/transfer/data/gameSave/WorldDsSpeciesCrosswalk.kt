package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.WorldDsSaveReader
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.gamesave.LostEvolution
import com.tamerslab.species.CanonicalSpeciesDto

/**
 * Maps the game's own species-name table onto bundled canonical identities. Duplicate names resolve
 * to the lowest species id and never to a different canonical species; a name the catalog cannot
 * resolve is simply absent, so sending it is blocked rather than substituted.
 */
internal object WorldDsSpeciesCrosswalk {
    fun dsIdsByCanonical(table: WorldDsSpeciesTable, catalog: CanonicalSpeciesCatalog): Map<String, Long> =
        idsByCanonical(0 until WorldDsSaveReader.SPECIES_COUNT, catalog) { table.name(it) }

    /**
     * Dawn/Dusk share one species space; only ids the engine actually creates are mappable. The
     * mapping comes from the table's canonical identity (full ROM name plus documented localization
     * aliases), never from the truncated display name alone.
     */
    fun ds2IdsByCanonical(table: Ds2SpeciesTable, catalog: CanonicalSpeciesCatalog): Map<String, Long> {
        val resolved = mutableMapOf<String, Long>()
        for (speciesId in 0x41..0x1F4) {
            val canonical = table.canonicalId(speciesId.toLong(), catalog) ?: continue
            val existing = resolved[canonical]
            if (existing == null || speciesId.toLong() < existing) resolved[canonical] = speciesId.toLong()
        }
        return resolved
    }

    /**
     * Lost Evolution species ids mapped to their bundled canonical identity (explicit ROM-derived
     * overrides first, then the game's own name through the catalog). Lowest id wins on duplicates.
     */
    fun leIdsByCanonical(table: LeSpeciesTable, catalog: CanonicalSpeciesCatalog): Map<String, Long> {
        val resolved = mutableMapOf<String, Long>()
        for (speciesId in 0 until LostEvolution.SPECIES_COUNT) {
            val canonical = table.canonicalId(speciesId.toLong(), catalog) ?: continue
            val existing = resolved[canonical]
            if (existing == null || speciesId.toLong() < existing) resolved[canonical] = speciesId.toLong()
        }
        return resolved
    }

    /**
     * Some app partners carry a base species name while their exported/assigned card character is a
     * variant of that same Digimon ("Agumon" vs "Agumon (Black)"). The variant is the form the user
     * sees on the card, so a transfer prefers it; a different Digimon never replaces the individual.
     */
    fun preferRefined(catalog: CanonicalSpeciesCatalog, base: CanonicalSpeciesDto?,
        refined: CanonicalSpeciesDto?): CanonicalSpeciesDto? {
        if (base == null) return refined
        if (refined == null || refined.id == base.id) return base
        val baseKey = base.name.lowercase().filter { it.isLetterOrDigit() }
        val refinedKey = refined.name.lowercase().filter { it.isLetterOrDigit() }
        return if (refinedKey.startsWith(baseKey)) refined else base
    }

    private fun idsByCanonical(range: IntRange, catalog: CanonicalSpeciesCatalog,
        nameOf: (Long) -> String?): Map<String, Long> {
        val resolved = mutableMapOf<String, Long>()
        for (speciesId in range) {
            val name = nameOf(speciesId.toLong()) ?: continue
            val canonical = catalog.resolve(name) ?: continue
            val existing = resolved[canonical.id]
            if (existing == null || speciesId.toLong() < existing) resolved[canonical.id] = speciesId.toLong()
        }
        return resolved
    }
}
