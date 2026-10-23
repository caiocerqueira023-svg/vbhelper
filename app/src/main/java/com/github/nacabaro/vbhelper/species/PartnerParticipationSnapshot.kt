package com.github.nacabaro.vbhelper.species

data class ParticipationCard(val id: Long, val name: String?, val originalName: String? = null, val imported: Boolean = true)

class PartnerUnavailableException(val reason: EligibilityReason) : IllegalStateException("This Digimon is no longer available in this collection.")

/** A cheap immutable read shared by one transaction or selection list, never a permanent species assignment. */
class PartnerParticipationSnapshot private constructor(
    private val index: SpeciesAvailabilityIndex,
    private val cards: Map<Long, Eligibility>,
    private val owned: Map<Long, Pair<String, Long>>,
    private val individuals: Map<String, Long>,
    private val references: Map<String, String?>,
) {
    fun matches(current: PartnerParticipationSnapshot) = owned == current.owned && cards.keys == current.cards.keys
    fun species(name: String?) = index.eligibilityFor(name)
    fun card(id: Long) = cards[id] ?: SpeciesEligibility.unavailable()
    fun owned(id: Long) = owned[id]?.let { card(it.second) } ?: SpeciesEligibility.unavailable()
    fun individual(id: String) = individuals[id]?.takeIf { it in cards }?.let(::card) ?: species(references[id])
    fun requireCard(id: Long) = requireUsable(card(id))
    fun requireOwned(id: Long, expectedIndividual: String? = null, expectedCard: Long? = null) {
        // A watch round-trip can replace the local representation ID, but not the permanent individual.
        val row = if (expectedIndividual == null) owned[id] else owned.values.singleOrNull { it.first == expectedIndividual }
        if (row == null || (expectedIndividual != null && row.first != expectedIndividual) ||
            (expectedCard != null && row.second != expectedCard)) throw PartnerUnavailableException(EligibilityReason.UNKNOWN_SPECIES)
        requireUsable(card(row.second))
    }
    fun requireIndividual(id: String) = requireUsable(individual(id))
    fun requireSpecies(name: String?) = requireUsable(species(name))

    private fun requireUsable(result: Eligibility) { if (!result.usable) throw PartnerUnavailableException(result.reason) }

    companion object {
        fun build(catalog: CanonicalSpeciesCatalog?, cards: List<ParticipationCard>,
            owned: Map<Long, Pair<String, Long>>, individuals: Map<String, Long>, references: Map<String, String?>): PartnerParticipationSnapshot {
            val names = cards.associate { card -> card.id to (
                catalog?.resolve(card.name.orEmpty())?.id ?: catalog?.resolve(card.originalName.orEmpty())?.id ?: card.name) }
            val index = SpeciesAvailabilityIndex(catalog, cards.filter { it.imported }.mapNotNull { names[it.id] }.toSet())
            val eligibility = cards.associate { it.id to Eligibility(true, EligibilityReason.USABLE) }
            return PartnerParticipationSnapshot(index, eligibility, owned, individuals, references)
        }
    }
}
