package com.tamerslab.species

/**
 * Resolves per-species availability from two source-independent inputs: the bundled
 * canonical catalog and the set of species names present on imported DIM/BEM cards.
 * Pure and JVM-testable; the Room-backed caller only supplies the loaded name set.
 */
class SpeciesAvailabilityIndex(
    private val catalog: CanonicalSpeciesCatalog?,
    loadedVbSpeciesNames: Set<String>,
) {
    private val loadedKeys: Set<String> = loadedVbSpeciesNames.mapNotNull { it.takeIf(String::isNotBlank) }
        .map(CanonicalSpeciesCatalog::key).toSet()
    private val loadedIds = loadedVbSpeciesNames.mapNotNull { catalog?.resolve(it)?.id }.toSet()

    fun availabilityFor(speciesNameOrId: String?): SpeciesAvailability {
        val requested = speciesNameOrId?.takeIf { it.isNotBlank() } ?: return SpeciesAvailability.NONE
        val species = catalog?.resolve(requested)
        // An imported species stays valid even if the bundled catalog does not name it.
        val loadedInVb = key(requested) in loadedKeys || species?.id in loadedIds
        if (species == null) return SpeciesAvailability(null, false, false, loadedInVb, resolved = false)
        return SpeciesAvailability(
            canonicalId = species.id,
            isDigimon = species.kind == "DIGIMON",
            hasBundledDot = species.dotSprites.setIds.isNotEmpty(),
            loadedInVb = loadedInVb,
        )
    }

    fun eligibilityFor(speciesNameOrId: String?): Eligibility =
        SpeciesEligibility.evaluate(availabilityFor(speciesNameOrId))

    private fun key(name: String) = CanonicalSpeciesCatalog.key(name)
}
