package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity
data class WorldDen(@PrimaryKey val id: String, val latitude: Double, val longitude: Double, val createdAt: Long)

@Entity(primaryKeys = ["individualA", "individualB"], foreignKeys = [
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualA"], onDelete = ForeignKey.CASCADE),
    ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualB"], onDelete = ForeignKey.CASCADE)
], indices = [Index(value = ["individualA"]), Index(value = ["individualB"])])
data class WildPairBond(
    val individualA: String, val individualB: String, val affinity: Int = 0,
    val chatCount: Int = 0, val aWins: Int = 0, val bWins: Int = 0, val draws: Int = 0,
    val lastInteractionTick: Long = 0, val lastInteractionAt: Long = 0, val cooldownUntilTick: Long = 0
) {
    init { require(individualA < individualB && affinity in -100..100) }
    companion object {
        fun create(a: String, b: String, affinity: Int = 0): WildPairBond {
            require(a != b)
            return if (a < b) WildPairBond(a, b, affinity.coerceIn(-100, 100)) else WildPairBond(b, a, affinity.coerceIn(-100, 100))
        }
    }
}

@Entity(indices = [Index(value = ["tick"]), Index(value = ["createdAt"])])
data class WorldEcosystemInput(
    @PrimaryKey val id: String, val tick: Long, val kind: String, val latitude: Double? = null,
    val longitude: Double? = null, val payload: String? = null, val createdAt: Long
)
