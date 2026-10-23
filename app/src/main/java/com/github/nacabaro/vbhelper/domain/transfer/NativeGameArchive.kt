package com.github.nacabaro.vbhelper.domain.transfer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

/** Native uniqueness is scoped to a verified scheme in one logical source, never globally to a hash. */
@Entity(indices = [Index(value = ["sourceId", "gameId", "formatProfile", "identityScheme", "nativeIdentity"], unique = true),
    Index(value = ["documentKey", "gameId", "formatProfile", "identityScheme", "nativeIdentity"]),
    Index(value = ["individualId"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.RESTRICT)])
data class ExternalIndividualBinding(
    @PrimaryKey val bindingId: String,
    val sourceId: String,
    val documentKey: String,
    val gameId: String,
    val formatProfile: String,
    val identityScheme: String,
    val nativeIdentity: String,
    val individualId: String,
    val boundAt: Long,
    @ColumnInfo(defaultValue = "'PROVISIONAL'") val identityConfidence: String = "PROVISIONAL",
)

/** Immutable source-native representation. No watch rows, invented game stats, or gameplay eligibility. */
@Entity(indices = [Index(value = ["bindingId"]), Index(value = ["individualId", "id"])], foreignKeys = [
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = ExternalIndividualBinding::class, parentColumns = ["bindingId"], childColumns = ["bindingId"], onDelete = ForeignKey.RESTRICT)])
data class NativeGameSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val individualId: String,
    val bindingId: String,
    val sourceLabel: String,
    val contentRevision: String,
    val codecVersion: Int,
    val recordKey: String,
    val recordJson: String,
    val nativePersonalityId: Int?,
    val mappedPersonality: String,
    val importedAt: Long,
    /** App review metadata, separate from exact source-native record data. */
    @ColumnInfo(defaultValue = "NULL") val reviewAuditJson: String? = null,
)
