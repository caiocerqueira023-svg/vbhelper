package com.github.nacabaro.vbhelper.domain.device_data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Permanent identity for a Digimon. Unlike UserCharacter, this row is not
 * deleted when its current watch/app representation is exported.
 */
@Entity
data class DigimonIndividual(
    @PrimaryKey val individualId: String,
    val createdAt: Long,
    val nickname: String? = null,
    val lastDiaryEntryAt: Long? = null,
    val lastCelebratedWinsMilestone: Int = 0,
    val lastCelebratedTrophyMilestone: Int = 0
)
