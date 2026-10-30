package com.github.nacabaro.vbhelper.source.gameSave

import android.util.Log
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.NativeCanonicalMapping
import com.github.nacabaro.vbhelper.domain.transfer.NativeGameSnapshot
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordIssue
import com.github.nacabaro.vbhelper.gamesave.WorldDsCopySelection
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Keeps already-imported individuals in step with the newest save without making the user
 * repeat an import. New native records are still created only through the reviewed import
 * flow; this only refreshes identities the app already owns.
 */
class AutomaticSaveReconciler(
    private val db: AppDatabase,
    private val materialization: NativeMaterializationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val gson = Gson()
    private val mutex = Mutex()

    suspend fun reconcile(view: LinkedSaveView): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val observation = view.observation ?: return@withLock materialization.reconcileReturnedSpecies()
            if (!TransferGameCapabilities.isNdsStory(observation)) {
                return@withLock materialization.reconcileReturnedSpecies()
            }
            val copy = WorldDsCopySelection.latestCopy(observation)
                ?: return@withLock materialization.reconcileReturnedSpecies()
            val latestByName = observation.records
                .filter { it.nativeParameters?.saveBlock == copy }
                .mapNotNull { record -> record.nativeIdentity?.takeIf(String::isNotBlank)?.let { "native:$it" to record } }
                .groupBy({ it.first }, { it.second })
                .mapNotNull { (identity, records) -> records.singleOrNull()?.let { identity to it } }
                .toMap()
            val bindings = db.nativeGameArchiveDao().allBindings()
                .filter { binding -> TransferGameCapabilities.isNdsStoryBinding(binding.gameId, binding.formatProfile) &&
                    (binding.sourceId == view.source.sourceId || binding.documentKey == view.source.documentKey) }
                .groupBy { it.nativeIdentity }
                .mapNotNull { (_, values) -> values.singleOrNull() }
            if (bindings.isEmpty() || latestByName.isEmpty()) return@withLock materialization.reconcileReturnedSpecies()

            val previous = db.nativeGameArchiveDao().latestSnapshots(bindings.map { it.bindingId })
                .associateBy { it.bindingId }
            var updated = 0
            for (binding in bindings) {
                val record = latestByName[binding.nativeIdentity] ?: continue
                if (!isSafeToReconcile(record)) continue
                val old = previous[binding.bindingId] ?: continue
                val mapping = materialization.mapping(old.id)
                val canonical = mapping?.takeIf { it.nativeSpeciesId == record.nativeSpeciesId }
                val recordJson = gson.toJson(record)
                if (old.contentRevision == observation.contentRevision && old.recordKey == record.recordKey &&
                    old.recordJson == recordJson) {
                    // A previous snapshot may have been stored before applying its app-side
                    // result. Retry that result instead of waiting for another native change.
                    if (db.transferContinuityDao().projection(binding.individualId)?.nativeSnapshotId != old.id) {
                        applyReturn(binding.individualId, old.id, canonical)
                    }
                    continue
                }
                val snapshotId = db.withTransaction {
                    val id = db.nativeGameArchiveDao().insertSnapshot(NativeGameSnapshot(
                        individualId = old.individualId,
                        bindingId = old.bindingId,
                        sourceLabel = view.source.label,
                        contentRevision = observation.contentRevision,
                        codecVersion = observation.codecVersion,
                        recordKey = record.recordKey,
                        recordJson = recordJson,
                        nativePersonalityId = record.personalityId,
                        mappedPersonality = old.mappedPersonality,
                        importedAt = clock(),
                    ))
                    NativeMappingContinuity.carried(mapping, record.nativeSpeciesId, id)?.let {
                        db.nativeMaterializationDao().saveMapping(it)
                    }
                    id
                }
                applyReturn(binding.individualId, snapshotId, canonical)
                updated++
            }
            val repaired = materialization.reconcileReturnedSpecies()
            updated + repaired
        }
    }

    private suspend fun applyReturn(individualId: String, snapshotId: Long, canonical: NativeCanonicalMapping?) {
        try {
            materialization.applyWorldDsReturn(snapshotId, canonical?.canonicalSpeciesId,
                canonical?.authority ?: "GAME_TABLE")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e("SaveReconciliation", "Could not apply $individualId from snapshot $snapshotId", failure)
        }
    }

    /**
     * A malformed or duplicate record must not overwrite a known-good app individual. Normal
     * storage/party movement and unassigned records are still safe to track.
     */
    private fun isSafeToReconcile(record: ObservedDigimon): Boolean = record.issues.none {
        it == RecordIssue.MISSING_IDENTITY || it == RecordIssue.DUPLICATE_IDENTITY ||
            it == RecordIssue.CONFLICTING_LOCATION || it == RecordIssue.IMPLAUSIBLE_VALUES ||
            it == RecordIssue.FOREIGN_RECORD || it == RecordIssue.EGG_RECORD
    }
}
