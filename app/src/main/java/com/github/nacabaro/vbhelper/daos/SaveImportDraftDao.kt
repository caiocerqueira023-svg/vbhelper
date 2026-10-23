package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.github.nacabaro.vbhelper.domain.transfer.GameImportItemReceipt
import com.github.nacabaro.vbhelper.domain.transfer.SaveImportDraft
import com.github.nacabaro.vbhelper.domain.transfer.SaveImportDraftItem
import com.github.nacabaro.vbhelper.domain.transfer.SaveImportDraftRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface SaveImportDraftDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraft(draft: SaveImportDraft): Long

    @Insert suspend fun insertItems(items: List<SaveImportDraftItem>)

    @Transaction @Query("SELECT * FROM SaveImportDraft WHERE draftId = :id")
    suspend fun get(id: String): SaveImportDraftRecord?

    @Query("SELECT * FROM SaveImportDraft d WHERE NOT EXISTS (SELECT 1 FROM SaveImportDraft n WHERE n.sourceId = d.sourceId AND (n.createdAt > d.createdAt OR (n.createdAt = d.createdAt AND n.draftId > d.draftId))) ORDER BY createdAt DESC")
    fun latestBySource(): Flow<List<SaveImportDraft>>

    @Query("SELECT recordKey FROM GameImportItemReceipt WHERE sourceId = :sourceId AND contentRevision = :revision AND formatProfile = :profile AND codecVersion = :codec")
    suspend fun importedKeys(sourceId: String, revision: String, profile: String, codec: Int): List<String>

    @Query("SELECT * FROM GameImportItemReceipt WHERE sourceId = :sourceId AND contentRevision = :revision AND formatProfile = :profile AND codecVersion = :codec")
    suspend fun receipts(sourceId: String, revision: String, profile: String, codec: Int): List<GameImportItemReceipt>

    /** Future acquisition producers must insert this inside the same transaction as permanent identity. */
    @Insert suspend fun insertReceipt(receipt: GameImportItemReceipt)
}
