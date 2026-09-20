package com.github.nacabaro.vbhelper.domain.digifarm

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual

@Entity
data class Farm(
    @PrimaryKey val id: String,
    val name: String,
    val mapId: String = "bird_digifarm",
    val mapVersion: Int = 1,
    val capacity: Int = 12,
    val createdAt: Long,
    val lastSimulatedAt: Long,
    val randomSeed: Long,
    val cameraScale: Float = 1f,
    val cameraX: Float = 0f,
    val cameraY: Float = 0f,
    val autonomousDialogueEnabled: Boolean = true,
    val archivedAt: Long? = null
)

@Entity(
    primaryKeys = ["individualId"],
    foreignKeys = [
        ForeignKey(
            entity = Farm::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DigimonIndividual::class,
            parentColumns = ["individualId"],
            childColumns = ["individualId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId"), Index("individualId", unique = true)]
)
data class FarmResident(
    val individualId: String,
    val farmId: String,
    val positionX: Float,
    val positionY: Float,
    val targetX: Float,
    val targetY: Float,
    val facingLeft: Boolean = false,
    val activity: String = "EXPLORE",
    val energy: Int = 80,
    val satiety: Int = 80,
    val social: Int = 70,
    val funLevel: Int = 70,
    val activityStartedAt: Long,
    val updatedAt: Long
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Farm::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["farmId", "sequence"], unique = true), Index("timestamp")]
)
data class FarmMessage(
    @PrimaryKey val id: String,
    val farmId: String,
    val sequence: Long,
    val type: String,
    val authorIndividualId: String?,
    val authorNameSnapshot: String,
    val body: String,
    val audience: String = "ALL",
    val replyToMessageId: String? = null,
    val sessionId: String? = null,
    val timestamp: Long
)

@Entity(
    primaryKeys = ["messageId", "individualId"],
    foreignKeys = [
        ForeignKey(
            entity = FarmMessage::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId"), Index("individualId")]
)
data class FarmMessageRecipient(
    val messageId: String,
    val individualId: String
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Farm::class,
            parentColumns = ["id"],
            childColumns = ["farmId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("farmId")]
)
data class FarmReadState(
    @PrimaryKey val farmId: String,
    val lastReadSequence: Long = 0
)

@Entity(
    primaryKeys = ["observerId", "otherId"],
    indices = [Index("otherId")]
)
data class FarmRelationship(
    val observerId: String,
    val otherId: String,
    val affinity: Int = 50,
    val familiarity: Int = 0,
    val lastInteractionAt: Long = 0
)

@Entity(indices = [Index("observerId"), Index("relatedIndividualId"), Index(value = ["eventId", "observerId"], unique = true)])
data class FarmMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val observerId: String,
    val relatedIndividualId: String?,
    val eventId: String,
    val summary: String,
    val relevance: Int = 50,
    val createdAt: Long
)

@Entity(indices = [Index("contactUnlockedAt"), Index("updatedAt")])
data class WildRelationship(
    @PrimaryKey val individualId: String,
    val cardCharacterId: Long,
    val speciesNameSnapshot: String?,
    val trust: Int = 50,
    val contactUnlockedAt: Long? = null,
    val recruitmentState: String = "WILD",
    val createdAt: Long,
    val updatedAt: Long
)
