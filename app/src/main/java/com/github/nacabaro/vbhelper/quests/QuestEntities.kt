package com.github.nacabaro.vbhelper.quests

import androidx.room.Embedded
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class QuestCategory { NORMAL, RECRUITMENT }
enum class QuestState { OFFERED, ACTIVE, READY, COMPLETED, DECLINED, ABANDONED }
enum class QuestObjectiveType {
    DELIVER_ITEM, RADAR_VICTORIES, DEFEAT_TARGET, WATCH_BATTLES, WATCH_WINS,
    WATCH_TROPHIES, REACH_VITALS, PARTNER_STAGE, USE_BATTLE_ITEM,
    MEET_TARGET, RECOVER_PROPERTY, COLLECT_TOKEN, DELIVER_TOKEN,
    EQUIP_TECHNIQUE, WIN_BATTLE_CONDITION, WATCH_PP_EARN, WATCH_PP_REACH, WATCH_ADVENTURE_ADVANCE
}

/** Terms are frozen at offer time; storage IDs and transient radar IDs are never identities. */
@Entity(indices = [Index(value = ["giverId", "category", "state"]), Index(value = ["partnerId"]),
    Index(value = ["parentQuestId"], unique = true)])
data class QuestInstance(
    @PrimaryKey val id: String,
    val giverId: String,
    val giverCardCharacterId: Long,
    val giverName: String,
    val category: QuestCategory,
    val templateId: String,
    val templateVersion: Int,
    val seed: Long,
    val stage: Int,
    val state: QuestState = QuestState.OFFERED,
    val revision: Long = 0,
    val partnerId: String? = null,
    val rewardBits: Int = 0,
    val rewardTrust: Int = 0,
    val rewardItemId: Long? = null,
    val rewardItemName: String? = null,
    val rewardItemQuantity: Int = 0,
    val rewardBattleItemId: String? = null,
    val rewardBattleItemQuantity: Int = 0,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val finishedAt: Long? = null,
    val lastWatchSyncAt: Long? = null,
    val watchNotice: String? = null,
    val offerMessageId: Long? = null,
    @ColumnInfo(defaultValue = "0") val currentPhase: Int = 0,
    val phaseStartedAt: Long? = null,
    val partnerName: String? = null,
    val parentQuestId: String? = null,
    @ColumnInfo(defaultValue = "0") val chainDepth: Int = 0,
    val followUpTemplateId: String? = null,
    val followUpQuestId: String? = null,
    val followUpBlock: QuestFollowUpBlock? = null,
    @ColumnInfo(defaultValue = "0") val followUpClosed: Boolean = false,
    val partnerDeviceType: com.github.nacabaro.vbhelper.utils.DeviceType? = null,
    val availabilityIssue: QuestAvailabilityIssue? = null
)

enum class QuestAvailabilityIssue { GIVER_CARD_MISSING, TARGET_CARD_MISSING, TARGET_JOINED, PARTNER_MISSING, WATCH_CARD_MISSING, WATCH_AREAS_EXHAUSTED }

enum class QuestFollowUpBlock {
    NEEDS_ACTIVE_PARTNER, NEEDS_TARGET_CARDS, NEEDS_SUPPLIES, NEEDS_RECOVERY_STOCK,
    WATCH_COUNTER_CAPACITY, ANOTHER_REQUEST_OPEN, GIVER_JOINED, RELATED_TARGET_JOINED,
    RELATED_TARGET_UNAVAILABLE, DECLINED, PARENT_NOT_VERIFIED
}

@Entity(
    foreignKeys = [ForeignKey(entity = QuestInstance::class, parentColumns = ["id"], childColumns = ["questId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["questId"]), Index(value = ["targetIndividualId"])]
)
data class QuestObjective(
    @PrimaryKey val id: String,
    val questId: String,
    val position: Int,
    val type: QuestObjectiveType,
    val required: Int,
    val progress: Int = 0,
    val itemId: Long? = null,
    val itemName: String? = null,
    val battleItemId: String? = null,
    val targetIndividualId: String? = null,
    val targetCardCharacterId: Long? = null,
    val targetName: String? = null,
    @ColumnInfo(defaultValue = "0") val phase: Int = 0,
    val tokenId: String? = null,
    val producesTokenId: String? = null,
    val techniqueId: String? = null,
    val techniqueName: String? = null,
    val maxBattleMillis: Long? = null,
    val maxBattleItems: Int? = null,
    val minHealthPercent: Int? = null,
    val minTechniqueHits: Int? = null,
    val alliedTeamSize: Int? = null,
    val opposingTeamSize: Int? = null,
    val watchCardId: Long? = null,
    val watchCardName: String? = null
)

data class QuestWithObjectives(
    @Embedded val quest: QuestInstance,
    @Relation(parentColumn = "id", entityColumn = "questId") val objectives: List<QuestObjective>,
    @Relation(parentColumn = "id", entityColumn = "questId") val tokens: List<QuestToken> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "questId") val battleAttempts: List<QuestBattleAttempt> = emptyList()
)

enum class QuestTokenKind { LOST_PROPERTY, LETTER, REPLY }
enum class QuestTokenState { LOCKED, HELD, DELIVERED, VOID }

/** Quest-only property is neither shop stock nor a transferable battle consumable. */
@Entity(foreignKeys = [ForeignKey(entity = QuestInstance::class, parentColumns = ["id"], childColumns = ["questId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["questId"])])
data class QuestToken(@PrimaryKey val id: String, val questId: String, val kind: QuestTokenKind,
                      val state: QuestTokenState = QuestTokenState.LOCKED, val acquiredAt: Long? = null,
                      val deliveredAt: Long? = null)

/** One source event belongs to one quest phase, including when it completes that phase. */
@Entity(primaryKeys = ["questId", "sourceId"])
data class QuestPhaseReceipt(val questId: String, val sourceId: String, val phase: Int, val recordedAt: Long)

data class QuestTargetTask(val questId: String, val objectiveId: String, val revision: Long,
                           val type: QuestObjectiveType, val giverId: String, val giverName: String,
                           val targetIndividualId: String, val targetName: String?, val phase: Int,
                           val canInteract: Boolean, val itemName: String? = null, val itemQuantity: Int = 0)

data class QuestBattleTargetTask(@Embedded val objective: QuestObjective, val giverName: String)

data class QuestPartnerOption(val individualId: String, val characterId: Long, val displayName: String,
                              val stage: Int, val deviceType: com.github.nacabaro.vbhelper.utils.DeviceType,
                              val isActive: Boolean, val cardId: Long? = null)

@Entity
data class QuestBattleReport(@PrimaryKey val interactionId: String,
    val outcome: com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome,
    val elapsedMillis: Long, val itemsUsed: Int, val alliedTeamSize: Int, val opposingTeamSize: Int,
    val rulesetVersion: Int, val recordedAt: Long)

@Entity(primaryKeys = ["interactionId", "individualId"])
data class QuestBattleMember(val interactionId: String, val individualId: String, val allied: Boolean,
    val health: Int, val maxHealth: Int)

@Entity(primaryKeys = ["interactionId", "actorId", "targetId", "techniqueId"])
data class QuestBattleTechniqueHit(val interactionId: String, val actorId: String, val targetId: String,
    val techniqueId: String, val hits: Int)

enum class QuestBattleFailure { NO_VICTORY, REPORT_MISSING, TEAM_SIZE, TIME_LIMIT, ITEM_LIMIT, HEALTH_LIMIT, TECHNIQUE_HITS }

@Entity(primaryKeys = ["questId", "objectiveId", "interactionId"], indices = [Index(value = ["questId"])])
data class QuestBattleAttempt(val questId: String, val objectiveId: String, val interactionId: String,
    val failure: QuestBattleFailure?, val recordedAt: Long)

/** Independent of prunable chat and interaction history. */
@Entity(primaryKeys = ["questId", "objectiveId", "sourceId"], indices = [Index(value = ["sourceId"])])
data class QuestEvidence(val questId: String, val objectiveId: String, val sourceId: String, val amount: Int, val recordedAt: Long)

@Entity
data class QuestRewardReceipt(@PrimaryKey val questId: String, val claimedAt: Long)

/** Exact outgoing wire values, tied to the one-time transfer token. */
@Entity(indices = [Index(value = ["individualId"])])
data class QuestWatchBaseline(
    @PrimaryKey val token: String,
    val individualId: String,
    val charIndex: Int,
    val history: String,
    val won: Int,
    val lost: Int,
    val trophies: Int,
    val lifetimeTrophies: Int?,
    val capturedAt: Long,
    val family: com.github.nacabaro.vbhelper.utils.DeviceType? = null,
    val cardId: Long? = null,
    val adventureNext: Int? = null,
    val adventureLimit: Int? = null
)

@Entity
data class QuestWallet(@PrimaryKey val id: Int = 1, val balance: Int)

@Entity
data class QuestBattleStock(@PrimaryKey val itemId: String, val quantity: Int)

/** Stock is withdrawn at battle commitment; interrupted sessions cannot replenish used stock. */
@Entity(primaryKeys = ["interactionId", "itemId"])
data class QuestBattleReservation(val interactionId: String, val itemId: String, val quantity: Int, val used: Int = 0)
