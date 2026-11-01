package com.tamerslab.domain.transfer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Embedded
import androidx.room.Relation

/** A user-authorized document. This ID identifies a linked source, not a playthrough or individual. */
@Entity(indices = [Index(value = ["documentKey"], unique = true)])
data class LinkedGameSave(
    @PrimaryKey val sourceId: String,
    val documentKey: String,
    val documentUri: String,
    val grantUri: String,
    val displayName: String,
    val label: String,
    val linkedAt: Long,
    val lastAttemptAt: Long? = null,
    val lastSuccessAt: Long? = null,
    val lastError: String? = null,
    val reviewedCopyRevision: String? = null,
    val reviewedSaveCopy: Int? = null,
)

/** Latest good observation; failed refreshes update source health without erasing this snapshot. */
@Entity(foreignKeys = [ForeignKey(entity = LinkedGameSave::class, parentColumns = ["sourceId"],
    childColumns = ["sourceId"], onDelete = ForeignKey.CASCADE)])
data class LinkedSaveObservation(
    @PrimaryKey val sourceId: String,
    val contentRevision: String,
    val observedAt: Long,
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val snapshotJson: String,
)

data class LinkedSaveRecord(
    @Embedded val source: LinkedGameSave,
    @Relation(parentColumn = "sourceId", entityColumn = "sourceId") val observation: LinkedSaveObservation?,
)
