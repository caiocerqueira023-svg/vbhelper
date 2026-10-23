package com.github.nacabaro.vbhelper.domain.species

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
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
    /** Name returned by catalog recognition; matching does not independently verify publication provenance. */
    val matchedName: String?,
    val level: String? = null,
    val type: String? = null,
    val profileDescription: String? = null,
    val specialMoves: List<String> = emptyList(),
    val source: SpeciesSource,
    @ColumnInfo(defaultValue = "''") val appearanceKey: String =
        com.github.nacabaro.vbhelper.battle.offline.core.BattleSpeciesIdentity.normalize(
            speciesName?.takeIf(String::isNotBlank) ?: matchedName.orEmpty()),
)
