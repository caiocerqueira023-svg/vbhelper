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
    val lastCelebratedTrophyMilestone: Int = 0,
    /** Solo Blast Evolution slot: NONE, POWER or FORM. */
    val blastMode: String = BlastEvolutionSlot.NONE,
    /** Target species for a FORM Blast; null unless blastMode is FORM. */
    val blastTargetSpecies: String? = null,
    /** Duo Jogress slot: result species chosen by this individual as fusion lead. */
    val jogressResultSpecies: String? = null
)

/** Solo Blast Evolution slot kinds stored on [DigimonIndividual.blastMode]. */
object BlastEvolutionSlot {
    const val NONE = "NONE"
    const val POWER = "POWER"
    const val FORM = "FORM"

    fun isValid(mode: String?): Boolean = mode == NONE || mode == POWER || mode == FORM
}
