package com.tamerslab.transfer.vpet

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.card.CardCharacter
import com.tamerslab.domain.device_data.IndividualOrigin
import com.tamerslab.domain.identity.IndividualIdentity
import com.tamerslab.domain.vpet.VpetImportOutcome
import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.domain.vpet.VpetPendingReason
import com.tamerslab.domain.vpet.VpetReadSnapshot
import com.tamerslab.domain.vpet.VpetSpeciesChoice
import com.tamerslab.species.AppPartnerFactory
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDto
import com.tamerslab.species.PartnerUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Imports decoded V-Pet reads into Storage as APP partners with `IMPORTED` origin (PLAN.md D3),
 * or keeps them as pending reads when the species is unknown or the collection participation
 * gate blocks them (D7). Every successful import creates a brand-new individual: reads are never
 * deduplicated (D4).
 *
 * Species resolution, the participation gate and partner creation reuse the existing partner
 * rules ([CanonicalSpeciesCatalog.resolve], `ExpandedCatalogRepository.definitionFor`,
 * [AppPartnerFactory.createLocked]); no Room schema or origin value is added.
 *
 * @param catalogLoader supplies the bundled canonical catalog (aliases included), normally
 *   `SpeciesRepository.getCanonicalCatalog()`.
 * @param pendingStore persists reads that cannot become partners yet.
 * @param clock injected for deterministic tests.
 */
class VpetImportService(
    private val db: AppDatabase,
    private val catalogLoader: suspend () -> CanonicalSpeciesCatalog?,
    private val pendingStore: VpetPendingReadStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val pendingReads: Flow<List<VpetPendingRead>> = pendingStore.pendingReads

    /**
     * Imports one decoded read. Known species become a new Storage partner; unknown or blocked
     * species are appended to the pending list and returned as [VpetImportOutcome.Pending].
     */
    suspend fun import(snapshot: VpetReadSnapshot): VpetImportOutcome = withContext(Dispatchers.IO) {
        try {
            val decodedName = decodedNameOf(snapshot)
            val canonical = resolveSpecies(decodedName)
            val definition = canonical?.let { definitionFor(it) }
            val plan = VpetImportPolicy.plan(
                decodedName = decodedName,
                canonicalId = canonical?.id,
                canonicalName = canonical?.name,
                participationAllowed = definition == null || participationAllows(definition.id),
            )
            when (plan) {
                is VpetImportPolicy.SpeciesPlan.KeepPending -> keepPending(snapshot, plan)
                is VpetImportPolicy.SpeciesPlan.CreateIndividual -> {
                    val resolved = requireNotNull(canonical)
                    val catalogDefinition = requireNotNull(definition)
                    when (val attempt = createPartner(decodedName, catalogDefinition)) {
                        is ImportAttempt.Imported -> VpetImportOutcome.Imported(
                            individualId = attempt.individualId,
                            characterId = attempt.characterId,
                            speciesName = resolved.name,
                        )

                        // The gate is re-read by createLocked; a concurrent collection change can
                        // still block an import the pre-check allowed.
                        ImportAttempt.Blocked -> keepPending(
                            snapshot,
                            VpetPendingReason.PARTICIPATION_BLOCKED,
                            resolved.name,
                        )
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            VpetImportOutcome.Failed(failureMessage(failure))
        }
    }

    /**
     * Maps a pending read to a canonical species chosen by the user, imports it and removes the
     * pending entry. A blocked gate keeps the pending entry untouched and returns it again.
     */
    suspend fun mapPending(id: String, canonicalId: String): VpetImportOutcome = withContext(Dispatchers.IO) {
        try {
            val pending = pendingStore.find(id)
                ?: return@withContext VpetImportOutcome.Failed("This pending V-Pet read is no longer available.")
            val canonical = resolveSpeciesById(canonicalId)
                ?: return@withContext VpetImportOutcome.Failed("The selected species is no longer available in the catalog.")
            val decodedName = decodedNameOf(pending.snapshot)
            val definition = definitionFor(canonical)
            val plan = VpetImportPolicy.plan(
                decodedName = decodedName,
                canonicalId = canonical.id,
                canonicalName = canonical.name,
                participationAllowed = participationAllows(definition.id),
            )
            when (plan) {
                is VpetImportPolicy.SpeciesPlan.KeepPending -> VpetImportOutcome.Pending(pending)
                is VpetImportPolicy.SpeciesPlan.CreateIndividual -> when (val attempt = createPartner(decodedName, definition)) {
                    is ImportAttempt.Imported -> {
                        pendingStore.remove(id)
                        VpetImportOutcome.Imported(
                            individualId = attempt.individualId,
                            characterId = attempt.characterId,
                            speciesName = canonical.name,
                        )
                    }

                    ImportAttempt.Blocked -> VpetImportOutcome.Pending(pending)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            VpetImportOutcome.Failed(failureMessage(failure))
        }
    }

    suspend fun discardPending(id: String) = pendingStore.remove(id)

    /**
     * Case-insensitive canonical DIGIMON search for the mapping dialog: name/id/alias
     * containment, also tolerant of compact names, then alphabetical order, capped at [limit].
     * An empty query lists the first species alphabetically.
     */
    suspend fun searchSpecies(query: String, limit: Int = 20): List<VpetSpeciesChoice> =
        withContext(Dispatchers.IO) {
            val catalog = catalogLoader() ?: return@withContext emptyList()
            val term = query.trim()
            val compactTerm = CanonicalSpeciesCatalog.key(term)
            catalog.data.species.asSequence()
                .filter { species -> species.kind == DIGIMON_SPECIES_KIND }
                .filter { species -> matchesSearch(species, term, compactTerm) }
                .sortedWith(compareBy({ it.name.lowercase(Locale.ROOT) }, { it.id }))
                .map { species ->
                    VpetSpeciesChoice(canonicalId = species.id, name = species.name, level = species.level)
                }
                .take(limit.coerceAtLeast(0))
                .toList()
        }

    private fun matchesSearch(species: CanonicalSpeciesDto, term: String, compactTerm: String): Boolean {
        if (term.isEmpty()) return true
        if (species.name.contains(term, ignoreCase = true)) return true
        if (species.id.contains(term, ignoreCase = true)) return true
        if (species.aliases.any { alias -> alias.contains(term, ignoreCase = true) }) return true
        return compactTerm.isNotEmpty() && CanonicalSpeciesCatalog.key(species.name).contains(compactTerm)
    }

    /** Resolves a decoded name through the alias-aware catalog; null when unknown or missing. */
    private suspend fun resolveSpecies(decodedName: String?): CanonicalSpeciesDto? {
        val requested = decodedName ?: return null
        return catalogLoader()?.resolve(requested)?.takeIf { it.kind == DIGIMON_SPECIES_KIND }
    }

    private suspend fun resolveSpeciesById(canonicalId: String): CanonicalSpeciesDto? =
        catalogLoader()?.resolve(canonicalId)?.takeIf { it.kind == DIGIMON_SPECIES_KIND }

    private suspend fun definitionFor(canonical: CanonicalSpeciesDto): CardCharacter {
        val expandedCatalog = requireNotNull(db.expandedCatalog) {
            "The expanded species catalog is unavailable."
        }
        return expandedCatalog.definitionFor(canonical.id)
    }

    private fun decodedNameOf(snapshot: VpetReadSnapshot): String? =
        snapshot.name?.trim()?.takeIf { it.isNotEmpty() }

    /** Creates the partner inside one Room transaction; never reuses a previous individual (D4). */
    private suspend fun createPartner(decodedName: String?, definition: CardCharacter): ImportAttempt {
        val individualId = IndividualIdentity.generate()
        val now = clock()
        return try {
            val characterId = db.withTransaction {
                AppPartnerFactory.createLocked(
                    db = db,
                    definition = definition,
                    individualId = individualId,
                    now = now,
                    nickname = decodedName,
                    activate = false,
                    origin = IndividualOrigin.IMPORTED,
                )
            }
            ImportAttempt.Imported(individualId, characterId)
        } catch (unavailable: PartnerUnavailableException) {
            ImportAttempt.Blocked
        }
    }

    private fun participationAllows(cardCharacterId: Long): Boolean = try {
        db.participation?.snapshot()?.requireCard(cardCharacterId)
        true
    } catch (_: PartnerUnavailableException) {
        false
    }

    private suspend fun keepPending(
        snapshot: VpetReadSnapshot,
        plan: VpetImportPolicy.SpeciesPlan.KeepPending,
    ): VpetImportOutcome = keepPending(snapshot, plan.reason, plan.blockedSpeciesName)

    private suspend fun keepPending(
        snapshot: VpetReadSnapshot,
        reason: VpetPendingReason,
        blockedSpeciesName: String,
    ): VpetImportOutcome = VpetImportOutcome.Pending(
        pendingStore.add(snapshot, reason, blockedSpeciesName)
    )

    private fun failureMessage(failure: Exception): String =
        "Could not import this V-Pet read: ${failure.message ?: failure::class.simpleName ?: "unknown error"}"

    private sealed interface ImportAttempt {
        data class Imported(val individualId: String, val characterId: Long) : ImportAttempt

        data object Blocked : ImportAttempt
    }

    private companion object {
        const val DIGIMON_SPECIES_KIND = "DIGIMON"
    }
}
