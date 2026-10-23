package com.github.nacabaro.vbhelper.domain.transfer

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.ColumnInfo
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

/** A persisted review, never a Storage acquisition. Soft source references survive unlinking. */
@Entity(indices = [Index(value = ["sourceId", "createdAt"])])
data class SaveImportDraft(
    @PrimaryKey val draftId: String,
    val sourceId: String,
    val sourceLabel: String,
    val documentKey: String,
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val contentRevision: String,
    val plannerVersion: Int,
    val fingerprint: String,
    val mappingPending: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val identityReviewRequired: Boolean = false,
)

/** Exact decoded native record + interpreted values and restrictions, individually bounded per row. */
@Entity(primaryKeys = ["draftId", "recordKey"], foreignKeys = [ForeignKey(
    entity = SaveImportDraft::class, parentColumns = ["draftId"], childColumns = ["draftId"], onDelete = ForeignKey.CASCADE)])
data class SaveImportDraftItem(val draftId: String, val recordKey: String, val itemJson: String)

data class SaveImportDraftRecord(
    @Embedded val draft: SaveImportDraft,
    @Relation(parentColumn = "draftId", entityColumn = "draftId") val items: List<SaveImportDraftItem>,
)

/** Successful acquisition receipts only. Preparing or saving a preview never inserts one. */
@Entity(indices = [Index(value = ["sourceId", "contentRevision", "formatProfile", "codecVersion", "recordKey"], unique = true),
    Index(value = ["individualId"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.RESTRICT)])
data class GameImportItemReceipt(
    @PrimaryKey val receiptId: String,
    val sourceId: String,
    val contentRevision: String,
    val formatProfile: String,
    val codecVersion: Int,
    val recordKey: String,
    val individualId: String,
    val importedAt: Long,
)
