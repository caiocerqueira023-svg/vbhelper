package com.github.nacabaro.vbhelper.domain.transfer

import androidx.room.*
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class UniversalJourneyAccount(@PrimaryKey val individualId: String, val originSnapshotId: Long?, val revision: Long,
    val stateJson: String, val lastAppDeltaId: Long, val updatedAt: Long)

@Entity(indices = [Index(value = ["individualId"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class UniversalProgressReceipt(@PrimaryKey val eventId: String, val individualId: String, val viewKey: String,
    val policyId: String, val deltaJson: String, val occurredAt: Long)

@Entity(indices = [Index(value = ["individualId"])], foreignKeys = [
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = NativeGameSnapshot::class, parentColumns = ["id"], childColumns = ["snapshotId"], onDelete = ForeignKey.RESTRICT)])
data class PendingNativeProgress(@PrimaryKey val snapshotId: Long, val individualId: String, val previousSnapshotId: Long,
    val reason: String, val recordedAt: Long)

/** Exists only inside an atomic conversion transaction, excluding projection writes from earned-change logs. */
@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class ProgressProjectionGuard(@PrimaryKey val individualId: String, val operationId: String)
