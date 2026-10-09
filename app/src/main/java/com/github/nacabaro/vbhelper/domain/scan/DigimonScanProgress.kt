package com.github.nacabaro.vbhelper.domain.scan

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteraction

/** Data belongs to an imported species entry, not to an expiring wild individual. */
@Entity(foreignKeys = [ForeignKey(entity = CardCharacter::class, parentColumns = ["id"],
    childColumns = ["cardCharacterId"], onDelete = ForeignKey.CASCADE)])
data class DigimonScanProgress(
    @PrimaryKey val cardCharacterId: Long,
    val percentage: Int,
    val updatedAt: Long,
)

/** Committed reward receipt: UI retries display the same before/after values. */
@Entity(primaryKeys = ["interactionId", "cardCharacterId"], foreignKeys = [
    ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = CardCharacter::class, parentColumns = ["id"], childColumns = ["cardCharacterId"], onDelete = ForeignKey.CASCADE),
], indices = [Index(value = ["cardCharacterId"])])
data class DigimonScanReward(
    val interactionId: String,
    val cardCharacterId: Long,
    val percentageBefore: Int,
    val percentageAfter: Int,
)
