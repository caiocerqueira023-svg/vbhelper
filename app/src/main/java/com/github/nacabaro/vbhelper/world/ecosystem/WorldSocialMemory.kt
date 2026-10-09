package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

/** Only recorded public encounter facts and speech. Private dialogue is never copied here. */
@Entity(primaryKeys = ["observerId", "eventId"], foreignKeys = [ForeignKey(
    entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["observerId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["observerId", "partnerId"]), Index(value = ["createdAt"])])
data class WorldSocialMemory(
    val observerId: String,
    val eventId: String,
    val partnerId: String,
    val partnerName: String,
    val motive: String,
    val outcome: String,
    val summary: String,
    val openingKey: String,
    val tick: Long,
    val createdAt: Long
)

@Entity(foreignKeys = [ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"],
    childColumns = ["individualId"], onDelete = ForeignKey.CASCADE)])
data class IndividualSocialState(
    @PrimaryKey val individualId: String,
    val sessionSeed: Long,
    val lastPlayerInitiationTick: Long = -1_000,
    val lastPeerInitiationTick: Long = -1_000,
    val lastPartnerId: String? = null
)
