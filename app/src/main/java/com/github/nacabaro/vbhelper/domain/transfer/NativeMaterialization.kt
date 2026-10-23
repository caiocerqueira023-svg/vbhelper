package com.github.nacabaro.vbhelper.domain.transfer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

/** Explicit local review of this exact native representation, not a verified global numeric crosswalk. */
@Entity(foreignKeys = [ForeignKey(entity = NativeGameSnapshot::class, parentColumns = ["id"],
    childColumns = ["snapshotId"], onDelete = ForeignKey.RESTRICT)])
data class NativeCanonicalMapping(
    @PrimaryKey val snapshotId: Long,
    val nativeSpeciesId: Long,
    val canonicalSpeciesId: String,
    val authority: String,
    val reviewedAt: Long,
)

/** Survives representation export/deletion, preventing first-materialization replay. */
@Entity(indices = [Index(value = ["snapshotId"])], foreignKeys = [
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = NativeGameSnapshot::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.RESTRICT)])
data class NativeMaterializationReceipt(
    @PrimaryKey val individualId: String,
    val snapshotId: Long,
    val canonicalSpeciesId: String,
    val definitionId: Long,
    val characterId: Long,
    val projectionVersion: Int,
    val createdAt: Long,
)
