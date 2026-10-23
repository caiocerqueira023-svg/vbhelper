package com.github.nacabaro.vbhelper.species

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.IndividualOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a source-independent species reference for each individual and records where
 * it came from. Immutable identity: an individual's [DigimonIndividual.speciesName] is
 * filled exactly once and never rewritten, so a later card deletion or rename cannot
 * change what a partner already is.
 */
class SpeciesBindingRepository(
    private val db: AppDatabase,
    private val catalogLoader: suspend () -> CanonicalSpeciesCatalog?,
) {
    /**
     * Fills missing species references from the individual's current card slot.
     * Runs at startup and is idempotent; rows that already carry a reference are untouched.
     */
    suspend fun backfillMissingBindings(): Int = withContext(Dispatchers.IO) {
        val pending = db.digimonIndividualDao().individualIdsMissingSpecies()
        if (pending.isEmpty()) return@withContext 0
        var filled = 0
        for (individualId in pending) {
            val binding = db.userCharacterDao().getByIndividualIdSync(individualId).firstOrNull() ?: continue
            val profile = db.speciesProfileDao().getByCardCharacterId(binding.charId) ?: continue
            val name = (profile.speciesName ?: profile.matchedName)?.takeIf { it.isNotBlank() } ?: continue
            db.digimonIndividualDao().backfillSpeciesName(individualId, name)
            db.digimonIndividualDao().backfillOriginCardCharacter(individualId, binding.charId)
            filled++
        }
        filled
    }

    /** The canonical identity for an individual, resolved through aliases; null when unknown. */
    suspend fun canonicalIdentityFor(speciesName: String?): CanonicalSpeciesDto? {
        val requested = speciesName?.takeIf { it.isNotBlank() } ?: return null
        return catalogLoader()?.resolve(requested)
    }

    /**
     * Records the initial binding for a newly created partner. Never overwrites an
     * existing reference. Expanded recruits additionally carry the expanded origin.
     */
    suspend fun bindNewIndividual(individualId: String, speciesName: String?, originKind: String, originCardCharacterId: Long?) {
        require(IndividualOrigin.isValid(originKind)) { "Unknown individual origin" }
        if (!speciesName.isNullOrBlank()) db.digimonIndividualDao().backfillSpeciesName(individualId, speciesName)
        originCardCharacterId?.let { db.digimonIndividualDao().backfillOriginCardCharacter(individualId, it) }
    }
}