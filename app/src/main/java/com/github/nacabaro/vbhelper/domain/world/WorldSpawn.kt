package com.github.nacabaro.vbhelper.domain.world

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = CardCharacter::class,
            parentColumns = ["id"],
            childColumns = ["cardCharacterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["cardCharacterId"]),
        Index(value = ["individualId"], unique = true)
    ]
)
data class WorldSpawn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardCharacterId: Long,
    val individualId: String,
    val latitude: Double,
    val longitude: Double,
    val spawnedAt: Long,
    val expiresAt: Long,
    val interacted: Boolean = false
)
