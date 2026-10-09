package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn

enum class InteractionType { CHAT, BATTLE }
enum class InteractionOrigin { AUTONOMOUS, DIRECT_PLAYER, JOINED_PLAYER }
enum class InteractionRole { WILD, OWNED }
enum class InteractionSide { NEUTRAL, ALLIED, OPPOSING }
enum class InteractionState {
    PROPOSED, ACTIVE, RESERVED, PLAYER_CONTROLLED, RESOLVING, ENDED, CANCELLED, INTERRUPTED;
    val terminal: Boolean get() = this == ENDED || this == CANCELLED || this == INTERRUPTED
}

@Entity(indices = [Index(value = ["state", "expiresAt"]), Index(value = ["parentInteractionId"])])
data class WorldInteraction(
    @PrimaryKey val id: String,
    val type: InteractionType,
    val origin: InteractionOrigin,
    val state: InteractionState,
    val seed: Long,
    val rulesVersion: Int = WorldEcosystemClock.RULES_VERSION,
    val startTick: Long,
    val nextActionTick: Long,
    val endTick: Long? = null,
    val revision: Long = 0,
    val createdAt: Long,
    val expiresAt: Long,
    val reservationExpiresAt: Long? = null,
    val reservedFrom: InteractionState? = null,
    val parentInteractionId: String? = null,
    val endedAt: Long? = null,
    val terminalReason: String? = null,
    val deadlineTick: Long? = null,
    @ColumnInfo(defaultValue = "0") val dialogueSequence: Long = 0,
    val publicReason: String? = null,
    val socialContextJson: String? = null
)

/** Identity survives expiry/deletion for archived context; live references become null. */
@Entity(
    primaryKeys = ["interactionId", "individualId"],
    foreignKeys = [
        ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WorldSpawn::class, parentColumns = ["id"], childColumns = ["spawnId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = UserCharacter::class, parentColumns = ["id"], childColumns = ["ownedCharacterId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = CardCharacter::class, parentColumns = ["id"], childColumns = ["cardCharacterId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index(value = ["interactionId"]), Index(value = ["individualId"]), Index(value = ["spawnId"]),
        Index(value = ["ownedCharacterId"]), Index(value = ["cardCharacterId"]), Index(value = ["interactionId", "ownedCharacterId"], unique = true)]
)
data class WorldInteractionParticipant(
    val interactionId: String,
    val individualId: String,
    val role: InteractionRole,
    val side: InteractionSide,
    val spawnId: Long? = null,
    val ownedCharacterId: Long? = null,
    val cardCharacterId: Long? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = DigimonIndividual::class, parentColumns = ["individualId"], childColumns = ["individualId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = WorldSpawn::class, parentColumns = ["id"], childColumns = ["spawnId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["interactionId"]), Index(value = ["spawnId"], unique = true)]
)
data class WorldParticipationClaim(
    @PrimaryKey val individualId: String,
    val interactionId: String,
    val spawnId: Long
)

@Entity(foreignKeys = [ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE)])
data class WorldInteractionResult(
    @PrimaryKey val interactionId: String,
    val outcome: BattleOutcome,
    val recordedAt: Long,
    val appliedAt: Long? = null
)
