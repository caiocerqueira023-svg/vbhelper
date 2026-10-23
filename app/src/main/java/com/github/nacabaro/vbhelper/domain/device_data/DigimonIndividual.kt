package com.github.nacabaro.vbhelper.domain.device_data

import androidx.room.ColumnInfo
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
    val jogressResultSpecies: String? = null,
    /**
     * Source-independent species reference for this individual. Resolves to a
     * canonical identity through the bundled catalog (aliases and forms included);
     * never a card slot, so it survives card deletion.
     */
    @ColumnInfo(defaultValue = "NULL") val speciesName: String? = null,
    /** How this individual entered the app. See [IndividualOrigin]. */
    @ColumnInfo(defaultValue = "CARD") val originKind: String = IndividualOrigin.CARD,
    /** Soft reference to the originating card slot; null once the card is gone. */
    @ColumnInfo(defaultValue = "NULL") val originCardCharacterId: Long? = null,
)

/** Where a partner originally came from. Display/metadata only; identity uses [DigimonIndividual.speciesName]. */
object IndividualOrigin {
    const val CARD = "CARD"       // Read/imported from a Vital Bracelet or card.
    const val EXPANDED = "EXPANDED" // Recruited in expanded mode without a card.
    const val IMPORTED = "IMPORTED" // Brought in from another game or V-Pet.

    fun isValid(value: String?): Boolean = value == CARD || value == EXPANDED || value == IMPORTED
}

/** Solo Blast Evolution slot kinds stored on [DigimonIndividual.blastMode]. */
object BlastEvolutionSlot {
    const val NONE = "NONE"
    const val POWER = "POWER"
    const val FORM = "FORM"

    fun isValid(mode: String?): Boolean = mode == NONE || mode == POWER || mode == FORM
}
