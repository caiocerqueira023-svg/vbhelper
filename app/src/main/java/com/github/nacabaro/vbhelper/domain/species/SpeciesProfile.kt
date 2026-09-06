package com.github.nacabaro.vbhelper.domain.species

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.card.CardCharacter

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = CardCharacter::class,
            parentColumns = ["id"],
            childColumns = ["cardCharacterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cardCharacterId"])]
)
data class SpeciesProfile(
    @PrimaryKey val cardCharacterId: Long,
    val speciesName: String?,
    /** Immutable record of the name returned by the remote official database. */
    val matchedName: String?,
    val level: String? = null,
    val type: String? = null,
    val profileDescription: String? = null,
    val specialMoves: List<String> = emptyList(),
    val source: SpeciesSource
)
