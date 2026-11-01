package com.tamerslab.domain.device_data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Durable source-mode suspension. Recorded progress and assignments stay in their original tables. */
@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class ParticipationPause(@PrimaryKey val individualId: String, val pausedAt: Long)
