package com.tamerslab.domain.transfer

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tamerslab.domain.device_data.DigimonIndividual

/**
 * App-owned location ledger for a verified send into a linked game endpoint. Copies that stay in a
 * game never mark presence; only a completed, readback-verified send does, and only a verified
 * native operation (a completed move into Storage) clears it again.
 */
@Entity(indices = [Index(value = ["sourceId"])], foreignKeys = [ForeignKey(entity = DigimonIndividual::class,
    parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class GameEndpointPresence(
    @PrimaryKey val individualId: String,
    val sourceId: String,
    val documentKey: String,
    val gameId: String,
    val nativeIdentity: String,
    val operationId: String,
    val sentAt: Long,
)
