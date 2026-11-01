package com.tamerslab.transfer.data.gameSave

import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.transfer.AppProgressDelta
import com.tamerslab.gamesave.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DestinationOriginChangedException : IllegalStateException("Native anchor or recorded progress changed during planning")
data class PreparedDestinationPreview(val snapshotId: Long, val preview: DestinationPreview)
data class PreparedBatchDestinationPreview(val snapshotIds: List<Long>, val preview: BatchDestinationPreview)

/** Ephemeral planning only: fresh reads may update source health/cache, never collection or game data. */
class DestinationPreviewRepository(
    private val loadOrigin: suspend (Long) -> NativeTransferOrigin,
    private val readDestination: suspend (String) -> SaveObservation,
) {
    constructor(db: AppDatabase, sources: LinkedGameSaveRepository) : this(
        { id -> withContext(Dispatchers.IO) { db.withTransaction {
            val dao = db.nativeGameArchiveDao()
            val snapshot = requireNotNull(dao.planningSnapshot(id)) { "Local native anchor unavailable" }
            val binding = requireNotNull(dao.binding(snapshot.bindingId))
            val record = requireNotNull(Gson().fromJson(snapshot.recordJson, ObservedDigimon::class.java))
            NativeTransferOrigin(snapshot.individualId, snapshot.id.toString(), snapshot.contentRevision,
                binding.gameId, binding.formatProfile, snapshot.codecVersion, record,
                binding.identityConfidence != "VERIFIED", readProgressContext(db, snapshot.individualId))
        } } }, sources::readFresh,
    )

    suspend fun prepare(snapshotId: Long, destinationId: String, container: SaveContainer): PreparedDestinationPreview {
        val origin = loadOrigin(snapshotId)
        val destination = readDestination(destinationId)
        if (loadOrigin(snapshotId) != origin) throw DestinationOriginChangedException()
        return PreparedDestinationPreview(snapshotId,
            DestinationPreviewPlanner.preview(origin, destinationId, destination, container))
    }

    suspend fun isCurrent(prepared: PreparedDestinationPreview): Boolean {
        val previous = prepared.preview
        return prepare(prepared.snapshotId, previous.destinationKey, previous.destinationContainer)
            .preview.fingerprint == previous.fingerprint
    }

    /** One fresh destination read and one re-check of every selected anchor during the read. */
    suspend fun prepareBatch(snapshotIds: List<Long>, destinationId: String,
        container: SaveContainer): PreparedBatchDestinationPreview {
        require(snapshotIds.isNotEmpty() && snapshotIds.distinct().size == snapshotIds.size) { "Snapshot ids must be distinct" }
        val origins = snapshotIds.map { loadOrigin(it) }
        val destination = readDestination(destinationId)
        if (snapshotIds.map { loadOrigin(it) } != origins) throw DestinationOriginChangedException()
        return PreparedBatchDestinationPreview(snapshotIds,
            DestinationPreviewPlanner.batch(origins, destinationId, destination, container))
    }

    suspend fun isCurrent(prepared: PreparedBatchDestinationPreview): Boolean {
        val previous = prepared.preview
        return prepareBatch(prepared.snapshotIds, previous.destinationKey, previous.destinationContainer)
            .preview.fingerprint == previous.fingerprint
    }
}

/** Read-only and bounded. Any later app growth or native review invalidates the origin and the preview. */
private suspend fun readProgressContext(db: AppDatabase, individualId: String): TransferProgressContext {
    val continuity = db.transferContinuityDao()
    val archive = db.nativeGameArchiveDao()
    val account = continuity.journey(individualId)
    val state = account?.stateJson?.let { Gson().fromJson(it, UniversalProgressState::class.java) }
        ?.also { require(it.version == 1) { "Unsupported canonical progress unit version" } } ?: UniversalProgressState()
    val anchorLedger = state.checkpoints["vbhelper-storage"]?.ledgerAtNativeAnchor ?: ProgressVector.of()
    var recorded = state.total - anchorLedger
    if (account != null) continuity.pendingAppDeltas(individualId, account.lastAppDeltaId)
        .forEach { recorded += it.toProgressVector() }
    val anchors = archive.nativeAnchors(individualId).map { row ->
        RetainedNativeAnchor(row.gameId, row.formatProfile, row.codecVersion, row.snapshotId.toString(),
            row.contentRevision, continuity.containerForSnapshot(row.snapshotId) != null,
            Gson().fromJson(row.recordJson, ObservedDigimon::class.java)?.nativeRecordBase64?.isNotBlank() == true)
    }
    return TransferProgressContext(account?.revision ?: 0, account?.lastAppDeltaId ?: 0,
        anchors.firstOrNull()?.snapshotKey?.toLongOrNull() ?: 0L, recorded,
        continuity.recentPendingNativeIds(individualId), anchors)
}

/** Mirrors the canonical app-delta mapping; deliberately never receipt-producing. */
private fun AppProgressDelta.toProgressVector(): ProgressVector = ProgressVector.of(mapOf(
    "training.hp" to hp.toLong(), "training.ap" to ap.toLong(), "training.bp" to bp.toLong(),
    "vitals" to vitals.toLong(), "battles.won" to wins.toLong(), "battles.lost" to losses.toLong(),
    "trophies" to trophies.toLong()))
