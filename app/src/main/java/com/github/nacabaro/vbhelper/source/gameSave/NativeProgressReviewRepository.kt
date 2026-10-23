package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.gamesave.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class NativeProgressReviewEntry(val snapshotId: Long, val sourceLabel: String, val report: NativeProgressReport?)
data class NativeProgressHistory(val pendingCount: Int, val entries: List<NativeProgressReviewEntry>)

/** Immutable archive review: does not classify ledger gains, acknowledge pending rows or change source state. */
class NativeProgressReviewRepository(private val db: AppDatabase) {
    suspend fun load(individualId: String): NativeProgressHistory = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dao = db.transferContinuityDao()
            val native = db.nativeMaterializationDao()
            val gson = Gson()
            val entries = dao.recentPendingNative(individualId).map { pending ->
                val before = native.snapshot(pending.previousSnapshotId)
                val after = native.snapshot(pending.snapshotId)
                val compatibleOwner = before?.individualId == individualId && after?.individualId == individualId
                val previousBinding = before?.let { native.binding(it.bindingId) }
                val binding = after?.let { native.binding(it.bindingId) }
                val a = before?.let { runCatching { gson.fromJson(it.recordJson, ObservedDigimon::class.java) }.getOrNull() }
                val b = after?.let { runCatching { gson.fromJson(it.recordJson, ObservedDigimon::class.java) }.getOrNull() }
                val compatible = previousBinding?.gameId == binding?.gameId && previousBinding?.formatProfile == binding?.formatProfile &&
                    before?.codecVersion == after?.codecVersion
                val report = if (compatibleOwner && a != null && b != null && binding != null && after != null)
                    NativeProgressReview.review(binding.gameId, binding.formatProfile, if (compatible) after.codecVersion else 0, a, b) else null
                NativeProgressReviewEntry(pending.snapshotId, if (compatibleOwner) after?.sourceLabel.orEmpty() else "", report)
            }
            NativeProgressHistory(dao.pendingNativeCount(individualId), entries)
        }
    }
}
