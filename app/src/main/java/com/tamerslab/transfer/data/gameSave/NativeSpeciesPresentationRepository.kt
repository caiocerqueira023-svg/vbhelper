package com.tamerslab.transfer.data.gameSave

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.species.BundledDotSpriteLibrary
import com.tamerslab.species.CanonicalSpeciesDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Presentation plus who chose the identity: the user (a reviewed mapping) or the game's own species table. */
data class NativePresentationSource(val species: CanonicalSpeciesDto, val userConfirmed: Boolean) {
    val authority: String get() = if (userConfirmed) "USER_CONFIRMED" else "GAME_TABLE"
}

/** Read-only presentation: browsing does not create species, partners, mappings or import receipts. */
class NativeSpeciesPresentationRepository(private val db: AppDatabase, private val library: BundledDotSpriteLibrary,
    private val speciesTables: NdsStorySpeciesTables? = null) {
    suspend fun sources(view: LinkedSaveView): Map<String, NativePresentationSource> = withContext(Dispatchers.IO) {
        val observation = view.observation ?: return@withContext emptyMap()
        val hints = db.nativeGameArchiveDao().presentationMappings(view.source.sourceId, view.source.documentKey)
        val catalog = library.catalog()
        observation.records.mapNotNull { record ->
            val reviewed = NativeSpeciesResolution.reviewedIdentity(view.source.sourceId, view.source.documentKey,
                observation.gameId, observation.formatProfile, observation.codecVersion, record.nativeIdentity,
                record.nativeSpeciesId, hints)
            val id = reviewed ?: NativeSpeciesResolution.tableIdentity(observation.gameId, observation.formatProfile,
                observation.codecVersion, record.nativeSpeciesId, speciesTables, catalog)
            val species = id?.let { catalog.speciesById[it]?.takeIf { species -> species.kind == "DIGIMON" } }
                ?: speciesTables?.name(observation.gameId, observation.formatProfile, observation.codecVersion, record.nativeSpeciesId)?.let { name ->
                    CanonicalSpeciesDto(NativeSpeciesResolution.unresolvedIdentity(name), name, kind = "DIGIMON")
                }
                ?: return@mapNotNull null
            record.recordKey to NativePresentationSource(species, userConfirmed = reviewed != null)
        }.toMap()
    }

    suspend fun source(view: LinkedSaveView): Map<String, CanonicalSpeciesDto> =
        sources(view).mapValues { it.value.species }

    suspend fun archive(snapshotId: Long, record: ObservedDigimon?): CanonicalSpeciesDto? = withContext(Dispatchers.IO) {
        val catalog = library.catalog()
        val mapping = db.nativeMaterializationDao().mapping(snapshotId)
        if (mapping != null && mapping.nativeSpeciesId == record?.nativeSpeciesId && mapping.authority == "USER_CONFIRMED") {
            return@withContext catalog.speciesById[mapping.canonicalSpeciesId]?.takeIf { it.kind == "DIGIMON" }
        }
        val speciesId = record?.nativeSpeciesId ?: return@withContext null
        val snapshot = db.nativeMaterializationDao().snapshot(snapshotId) ?: return@withContext null
        val binding = db.nativeGameArchiveDao().binding(snapshot.bindingId) ?: return@withContext null
        val id = NativeSpeciesResolution.tableIdentity(binding.gameId, binding.formatProfile, snapshot.codecVersion,
            speciesId, speciesTables, catalog)
        id?.let { catalog.speciesById[it]?.takeIf { species -> species.kind == "DIGIMON" } }?.let { return@withContext it }
        return@withContext speciesTables?.name(binding.gameId, binding.formatProfile, snapshot.codecVersion, speciesId)?.let { name ->
            CanonicalSpeciesDto(NativeSpeciesResolution.unresolvedIdentity(name), name, kind = "DIGIMON")
        }
    }
}
