package com.github.nacabaro.vbhelper.source.gameSave

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.SaveImportDraft
import com.github.nacabaro.vbhelper.domain.transfer.SaveImportDraftItem
import com.github.nacabaro.vbhelper.gamesave.SnapshotImportItem
import com.github.nacabaro.vbhelper.gamesave.SnapshotImportPlanner
import com.github.nacabaro.vbhelper.gamesave.SnapshotImportPreview
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportSourceChangedException : IllegalStateException("Source revision changed")
data class SavedImportPreview(val draft: SaveImportDraft, val preview: SnapshotImportPreview)

/** Persists review snapshots only. No partner/identity/personality/receipt acquisition producer exists here. */
class SaveImportPreviewRepository(
    private val db: AppDatabase,
    private val sources: LinkedGameSaveRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao get() = db.saveImportDraftDao()
    private val gson = Gson()
    val savedPreviews = dao.latestBySource()

    suspend fun prepare(sourceId: String, expectedRevision: String, selectedKeys: List<String>): SavedImportPreview = withContext(Dispatchers.IO) {
        require(selectedKeys.size in 1..128)
        val fresh = sources.readFresh(sourceId)
        if (fresh.contentRevision != expectedRevision) throw ImportSourceChangedException()
        db.withTransaction {
            val source = requireNotNull(db.linkedGameSaveDao().get(sourceId))
            if (com.github.nacabaro.vbhelper.gamesave.WorldDsCopySelection.apply(fresh,
                    source.reviewedCopyRevision, source.reviewedSaveCopy) != fresh) throw ImportSourceChangedException()
            val importedKeys = dao.importedKeys(sourceId, fresh.contentRevision, fresh.formatProfile, fresh.codecVersion).toSet()
            val preview = SnapshotImportPlanner.preview(fresh, selectedKeys, importedKeys)
            val draft = SaveImportDraft("$sourceId.${preview.fingerprint}", sourceId, source.label,
                source.documentKey, fresh.gameId, fresh.formatProfile, fresh.codecVersion, fresh.contentRevision,
                preview.plannerVersion, preview.fingerprint, preview.mappingPending, clock(), preview.requiresIdentityReview)
            if (dao.insertDraft(draft) != -1L) {
                dao.insertItems(preview.items.map { SaveImportDraftItem(draft.draftId, it.record.recordKey, gson.toJson(it)) })
            }
            requireNotNull(load(draft.draftId))
        }
    }

    /** Historical saved reviews are not refreshed confirmations and cannot authorize acquisition. */
    suspend fun load(id: String): SavedImportPreview? = withContext(Dispatchers.IO) {
        val saved = dao.get(id) ?: return@withContext null
        val draft = saved.draft
        val items = saved.items.sortedBy { it.recordKey }.map { gson.fromJson(it.itemJson, SnapshotImportItem::class.java) }
        SavedImportPreview(draft, SnapshotImportPreview(draft.gameId, draft.formatProfile, draft.codecVersion,
            draft.contentRevision, draft.plannerVersion, draft.fingerprint, draft.mappingPending, items, draft.identityReviewRequired))
    }
}
