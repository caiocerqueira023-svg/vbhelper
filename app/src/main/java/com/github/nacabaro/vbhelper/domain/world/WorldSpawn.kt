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
    val interacted: Boolean = false,
    /** 0-100. Começa em 50. Mais volátil para baixo do que para cima. */
    val mood: Int = 50,
    val recruitmentState: RecruitmentState = RecruitmentState.WILD,
    /**
     * When true, this wild Digimon is temporarily following the player so chat
     * can continue while walking. Activated only if the first message raised mood.
     * Stops automatically when mood drops below [FOLLOW_STOP_MOOD].
     */
    val isFollowing: Boolean = false,
    /**
     * Player latitude recorded at the last follow-distance mood deduction
     * (or when following started). Used to measure meters walked while followed.
     */
    val followLastLat: Double? = null,
    /**
     * Player longitude recorded at the last follow-distance mood deduction
     * (or when following started).
     */
    val followLastLon: Double? = null
) {
    companion object {
        /** Mood below this value ends following. */
        const val FOLLOW_STOP_MOOD = 50
        /** Distance (meters) that triggers one mood-loss segment while following. */
        const val FOLLOW_SEGMENT_METERS = 2.0
        /** Mood points lost per [FOLLOW_SEGMENT_METERS] walked while following. */
        const val FOLLOW_MOOD_LOSS_PER_SEGMENT = 3
    }
}
