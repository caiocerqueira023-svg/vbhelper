package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.*
import com.github.nacabaro.vbhelper.gamesave.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Versioned semantic progression. Real game policies must classify pending native changes before transfer. */
class UniversalJourneyRepository(private val db: AppDatabase) {
    private val dao get() = db.transferContinuityDao()
    private val gson = Gson()
    private suspend fun <T> atomic(block: suspend () -> T): T =
        if (db.inTransaction()) block() else withContext(Dispatchers.IO) { db.withTransaction { block() } }

    /** Projection/configuration writes are never counted as new progression; rollback removes the guard too. */
    internal suspend fun <T> projection(id: String, operationId: String, apply: suspend () -> T): T = atomic {
        dao.projectionGuard(ProgressProjectionGuard(id, operationId))
        try { apply() } finally { dao.clearProjectionGuard(id) }
    }

    suspend fun originalNativeRecordIfNoProgress(id: String): ByteArray? = atomic {
        val account = synchronizeAppChanges(id)
        if (!gson.fromJson(account.stateJson, UniversalProgressState::class.java).total.isZero || dao.pendingNativeCount(id) != 0) return@atomic null
        val snapshot = account.originSnapshotId?.let { db.nativeMaterializationDao().snapshot(it) } ?: return@atomic null
        val record = gson.fromJson(snapshot.recordJson, ObservedDigimon::class.java)
        record.nativeRecordBase64?.let { java.util.Base64.getDecoder().decode(it) }
    }

    private suspend fun account(id: String): UniversalJourneyAccount = dao.journey(id) ?: UniversalJourneyAccount(id,
        db.openHelper.readableDatabase.query("SELECT MIN(id) FROM NativeGameSnapshot WHERE individualId=?", arrayOf(id)).use {
            check(it.moveToFirst()); if (it.isNull(0)) null else it.getLong(0)
        }, 0, gson.toJson(UniversalProgressState()), 0, System.currentTimeMillis())

    suspend fun synchronizeAppChanges(id: String): UniversalJourneyAccount = atomic {
        var account = account(id)
        var state = gson.fromJson(account.stateJson, UniversalProgressState::class.java)
        require(state.version == 1) { "Unsupported canonical progress unit version" }
        for (delta in dao.pendingAppDeltas(id, account.lastAppDeltaId)) {
            val vector = ProgressVector.of(mapOf("training.hp" to delta.hp.toLong(), "training.ap" to delta.ap.toLong(),
                "training.bp" to delta.bp.toLong(), "vitals" to delta.vitals.toLong(), "battles.won" to delta.wins.toLong(),
                "battles.lost" to delta.losses.toLong(), "trophies" to delta.trophies.toLong()))
            if (dao.progressReceipt(UniversalProgressReceipt("app:${delta.id}", id, "vbhelper-storage", "app-state-v1",
                    gson.toJson(vector), delta.recordedAt)) != -1L) {
                val app = state.checkpoints["vbhelper-storage"]
                state = if (app == null) state.copy(total = state.total + vector) else {
                    val observed = app.lastObserved + vector
                    UniversalProgress.observe(state, "vbhelper-storage", app.revision, app.normalizationPolicy,
                        gson.toJson(observed).toByteArray(Charsets.UTF_8), observed, allowReviewedRegression = true).state
                }
            }
            account = account.copy(lastAppDeltaId = delta.id, revision = account.revision + 1)
        }
        account.copy(stateJson = gson.toJson(state), updatedAt = System.currentTimeMillis()).also { dao.saveJourney(it) }
    }

    /** Called only after a verified adapter has encoded/read back a representation, never for UI/DIM selection. */
    internal suspend fun acceptVerifiedEntry(id: String, view: String, policy: String, nativeRecord: ByteArray, normalized: ProgressVector) =
        atomic {
            val account = synchronizeAppChanges(id)
            val state = UniversalProgress.acceptVerifiedEntry(gson.fromJson(account.stateJson, UniversalProgressState::class.java), view, policy, nativeRecord, normalized)
            dao.saveJourney(account.copy(stateJson = gson.toJson(state), revision = account.revision + 1, updatedAt = System.currentTimeMillis()))
        }

    internal suspend fun observeVerifiedProgress(id: String, eventId: String, view: String, policy: String, expectedRevision: Long,
        nativeRecord: ByteArray, normalized: ProgressVector, reviewedRegression: Boolean = false): ProgressVector = atomic {
        val parts = listOf(id, view, eventId).joinToString("") { "${it.length}:$it" }
        val receiptId = "game:" + java.security.MessageDigest.getInstance("SHA-256").digest(parts.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        if (dao.progressSeen(receiptId)) return@atomic ProgressVector.of()
        val account = synchronizeAppChanges(id)
        val observation = UniversalProgress.observe(gson.fromJson(account.stateJson, UniversalProgressState::class.java), view, expectedRevision,
            policy, nativeRecord, normalized, reviewedRegression)
        dao.progressReceipt(UniversalProgressReceipt(receiptId, id, view, policy, gson.toJson(observation.delta), System.currentTimeMillis()))
        dao.saveJourney(account.copy(stateJson = gson.toJson(observation.state), revision = account.revision + 1, updatedAt = System.currentTimeMillis()))
        observation.delta
    }
}
