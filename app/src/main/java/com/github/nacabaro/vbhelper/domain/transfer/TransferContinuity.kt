package com.github.nacabaro.vbhelper.domain.transfer

import androidx.room.*
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity(indices = [Index(value = ["serverId"], unique = true)])
data class CompanionConnection(@PrimaryKey val connectionId: String, val serverId: String, val label: String,
    val host: String, val port: Int, val certificatePin: String, val encryptedToken: String, val pairedAt: Long)

@Entity(indices = [Index(value = ["kind", "locator"], unique = true)])
data class LinkedSaveLocation(@PrimaryKey val locationId: String, val kind: String, val locator: String, val label: String,
    val linkedAt: Long, val lastScanAt: Long? = null, val lastError: String? = null, val truncated: Boolean = false)

@Entity(primaryKeys = ["locationId", "documentKey"], foreignKeys = [ForeignKey(entity = LinkedSaveLocation::class,
    parentColumns = ["locationId"], childColumns = ["locationId"], onDelete = ForeignKey.CASCADE)])
data class SaveLocationExclusion(val locationId: String, val documentKey: String)

/** Lifetime events are independent of transient UserCharacter/TransformationHistory rows. */
@Entity(indices = [Index(value = ["individualId", "occurredAt"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class IndividualLifeEvent(@PrimaryKey val eventId: String, val individualId: String, val kind: String,
    val occurredAt: Long, val nativeSnapshotId: Long? = null, val representationId: Long? = null, val definitionId: Long? = null,
    val speciesId: String? = null)

/** Observed app changes, not asserted native-game gains or physical bracelet measurements. */
@Entity(indices = [Index(value = ["individualId", "id"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class AppProgressDelta(@PrimaryKey(autoGenerate = true) val id: Long = 0, val individualId: String, val kind: String,
    val hp: Int, val ap: Int, val bp: Int, val vitals: Int, val wins: Int, val losses: Int, val recordedAt: Long, val version: Int,
    @ColumnInfo(defaultValue = "0") val trophies: Int = 0)

@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"],
    childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class NativeAppProjection(@PrimaryKey val individualId: String, val nativeSnapshotId: Long, val projectionVersion: Int,
    val baselineKind: String, val stage: Int, val baseHp: Int, val baseBp: Int, val baseAp: Int, val trainingHp: Int, val trainingAp: Int, val trainingBp: Int,
    val vitalPoints: Int, val wins: Int, val losses: Int, val baselineDeltaId: Long, val recordedAt: Long,
    val originBasisPoints: Int = 10000)
