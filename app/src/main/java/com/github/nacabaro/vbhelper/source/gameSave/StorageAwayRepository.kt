package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.daos.AwayCharacterRow
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.ExternalIndividualBinding
import com.github.nacabaro.vbhelper.domain.transfer.LinkedGameSave
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.identityEvidenceKey
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** A native record still resident in a linked save that has no VBHelper representation. */
data class AwayNativeRecord(
    val sourceId: String,
    val sourceLabel: String,
    val gameId: String,
    val recordKey: String,
    val name: String,
    val species: CanonicalSpeciesDto?,
)

/** Read-only away listing: browsing never imports, binds or creates a partner. */
class StorageAwayRepository(
    private val db: AppDatabase,
    private val sources: LinkedGameSaveRepository,
    private val presentation: NativeSpeciesPresentationRepository,
) {
    fun observeAwayCharacters(): Flow<List<AwayCharacterRow>> =
        db.nativeMaterializationDao().observeAwayCharacters()

    val nativeRecords: Flow<List<AwayNativeRecord>> = sources.sources.map { views ->
        val bound = db.nativeGameArchiveDao().allBindings().map(::bindingKey).toSet()
        views.flatMap { view ->
            val observation = view.observation ?: return@flatMap emptyList()
            val species = presentation.sources(view)
            observation.records
                .filter { bindingKey(view.source, it) !in bound }
                // Both save copies are listed once per native identity; the reviewed copy wins a tie.
                .groupBy { it.appIdentity() }
                .map { (_, copies) ->
                    copies.firstOrNull { it.nativeParameters?.saveBlock == view.source.reviewedSaveCopy }
                        ?: copies.minBy { it.nativeParameters?.saveBlock ?: Int.MAX_VALUE }
                }
                .map { record ->
                    AwayNativeRecord(
                        sourceId = view.source.sourceId,
                        sourceLabel = view.source.label,
                        gameId = observation.gameId,
                        recordKey = record.recordKey,
                        name = record.storedName.ifBlank { record.recordKey },
                        species = species[record.recordKey]?.species,
                    )
                }
        }
    }.flowOn(Dispatchers.IO)

    private fun bindingKey(binding: ExternalIndividualBinding): String =
        "${binding.sourceId}|${binding.documentKey}|${binding.nativeIdentity}"

    private fun bindingKey(source: LinkedGameSave, record: ObservedDigimon): String =
        "${source.sourceId}|${source.documentKey}|${record.appIdentity()}"
}

/** Mirrors [VerifiedSnapshotImportProfile.nativeIdentity] so unbound records are detected exactly. */
internal fun ObservedDigimon.appIdentity(): String =
    identityEvidenceKey()?.removePrefix("candidate:") ?: identityCandidate.toString()
