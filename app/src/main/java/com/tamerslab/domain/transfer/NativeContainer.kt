package com.tamerslab.domain.transfer

import androidx.room.*

@Entity
data class NativeSaveContainer(@PrimaryKey val revision: String, val byteCount: Int, val archivedAt: Long)

@Entity(primaryKeys = ["revision", "chunkIndex"], foreignKeys = [ForeignKey(entity = NativeSaveContainer::class,
    parentColumns = ["revision"], childColumns = ["revision"], onDelete = ForeignKey.CASCADE)])
data class NativeSaveContainerChunk(val revision: String, val chunkIndex: Int, val bytes: ByteArray)

@Entity(indices = [Index(value = ["revision"])], foreignKeys = [
    ForeignKey(entity = NativeGameSnapshot::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = NativeSaveContainer::class, parentColumns = ["revision"], childColumns = ["revision"], onDelete = ForeignKey.RESTRICT)])
data class NativeSnapshotContainer(@PrimaryKey val snapshotId: Long, val revision: String)
