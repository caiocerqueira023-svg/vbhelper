package com.tamerslab.domain.characters

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.tamerslab.domain.card.CardCharacter

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = CardCharacter::class,
            parentColumns = ["id"],
            childColumns = ["id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Dex(
    @PrimaryKey val id: Long = 0,
    val discoveredOn: Long
)