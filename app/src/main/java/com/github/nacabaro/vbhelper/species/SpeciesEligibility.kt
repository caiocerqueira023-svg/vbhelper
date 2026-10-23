package com.github.nacabaro.vbhelper.species

/** Identity validation for references; card/artwork availability is never a gameplay gate. */
enum class EligibilityReason {
    USABLE,
    UNKNOWN_SPECIES,
    NOT_A_DIGIMON,
}

data class Eligibility(val usable: Boolean, val reason: EligibilityReason)

/** Source-independent inputs for one species. Card/slot coordinates are never part of identity. */
data class SpeciesAvailability(
    val canonicalId: String?,
    val isDigimon: Boolean,
    val hasBundledDot: Boolean,
    val loadedInVb: Boolean,
    /** False when the bundled catalog does not know this species at all. */
    val resolved: Boolean = true,
) {
    companion object {
        val NONE = SpeciesAvailability(null, false, false, false, resolved = false)
    }
}

/** Stored definitions are always usable; this rule validates unowned species references. */
object SpeciesEligibility {
    fun evaluate(availability: SpeciesAvailability): Eligibility = when {
        !availability.resolved && availability.loadedInVb -> Eligibility(true, EligibilityReason.USABLE)
        !availability.resolved -> Eligibility(false, EligibilityReason.UNKNOWN_SPECIES)
        !availability.isDigimon -> Eligibility(false, EligibilityReason.NOT_A_DIGIMON)
        else -> Eligibility(true, EligibilityReason.USABLE)
    }

    /** Convenience for a species the catalog does not know and no card references. */
    fun unavailable(canonicalId: String? = null): Eligibility =
        Eligibility(false, EligibilityReason.UNKNOWN_SPECIES)
}
