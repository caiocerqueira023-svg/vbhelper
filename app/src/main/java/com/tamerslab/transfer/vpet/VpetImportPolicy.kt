package com.tamerslab.transfer.vpet

import com.tamerslab.domain.vpet.VpetPendingReason

/**
 * Pure decision rules for turning a decoded read into either a Storage partner or a pending
 * read (PLAN.md D3/D4/D7). Kept separate from Room/DataStore access so the selection is
 * unit-testable on the JVM.
 */
internal object VpetImportPolicy {

    sealed interface SpeciesPlan {
        /** A canonical species was resolved and the participation gate allows a new partner. */
        data class CreateIndividual(val canonicalId: String) : SpeciesPlan

        /** The read cannot become a partner yet and must stay in the pending list. */
        data class KeepPending(
            val reason: VpetPendingReason,
            val blockedSpeciesName: String,
        ) : SpeciesPlan
    }

    /**
     * Selects the import path for one read.
     *
     * [canonicalId] is null when the read has no name or the bundled catalog does not resolve
     * it: the read is kept as an `UNKNOWN_SPECIES` pending entry with the decoded name as
     * provenance (D7). [participationAllowed] is the collection gate verdict for an already
     * resolved species and is ignored while no canonical identity exists.
     *
     * The policy is stateless and never deduplicates: planning the same read twice always yields
     * [SpeciesPlan.CreateIndividual], which the service honors with a brand-new individual (D4).
     */
    fun plan(
        decodedName: String?,
        canonicalId: String?,
        canonicalName: String?,
        participationAllowed: Boolean,
    ): SpeciesPlan {
        if (canonicalId == null || canonicalId.isBlank()) {
            return SpeciesPlan.KeepPending(
                reason = VpetPendingReason.UNKNOWN_SPECIES,
                blockedSpeciesName = decodedName.orEmpty(),
            )
        }
        if (!participationAllowed) {
            return SpeciesPlan.KeepPending(
                reason = VpetPendingReason.PARTICIPATION_BLOCKED,
                blockedSpeciesName = canonicalName?.takeIf { it.isNotBlank() } ?: canonicalId,
            )
        }
        return SpeciesPlan.CreateIndividual(canonicalId)
    }
}
