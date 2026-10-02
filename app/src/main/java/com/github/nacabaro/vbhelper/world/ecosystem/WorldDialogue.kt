package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DialogueTextSource { MODEL, AUTHORED, PLAYER, SYSTEM, RECAP }
enum class DialogueIntentType { NONE, CHALLENGE_BATTLE, ACCEPT_CHALLENGE, DECLINE_CHALLENGE, DEESCALATE }
enum class DialogueIntentStatus { PENDING, ACCEPTED, DECLINED, EXPIRED }

@Entity(foreignKeys = [ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["interactionId", "sequence"], unique = true), Index(value = ["requestId", "speakerId"], unique = true)])
data class WorldInteractionMessage(
    @PrimaryKey val id: String, val interactionId: String, val sequence: Long, val speakerId: String,
    val speakerName: String, val body: String, val source: DialogueTextSource, val requestId: String,
    val tick: Long, val createdAt: Long
)

@Entity(foreignKeys = [ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["interactionId"]), Index(value = ["status", "expiresAtTick"])])
data class WorldDialogueIntent(
    @PrimaryKey val id: String, val interactionId: String, val sourceRevision: Long, val initiatorId: String,
    val targetIdsJson: String, val evidenceIdsJson: String, val type: DialogueIntentType,
    val status: DialogueIntentStatus, val reason: String, val sparring: Boolean,
    val expiresAtTick: Long, val linkedBattleId: String? = null
)

@Entity(foreignKeys = [ForeignKey(entity = WorldInteraction::class, parentColumns = ["id"], childColumns = ["interactionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["interactionId"])])
data class WorldNpcBattle(
    @PrimaryKey val interactionId: String, val definitionsJson: String, val elapsedMillis: Long = 0,
    val startTick: Long, val nextRoundTick: Long, val snapshotJson: String = "{}"
)

data class DialogueLine(val speakerId: String, val text: String)
data class DialogueProposal(val type: DialogueIntentType, val speakerId: String, val targetIds: List<String>,
    val evidenceIds: List<String>, val reason: String, val sparring: Boolean = false)
data class DialogueExchange(val lines: List<DialogueLine>, val intent: DialogueProposal? = null, val source: DialogueTextSource = DialogueTextSource.MODEL)

data class NpcCombatantSummary(val combatantId:String,val displayName:String,val health:Int,val maxHealth:Int,val energy:Int,val maxEnergy:Int)
data class NpcBattleSummary(val elapsedMillis:Long,val alliedMembers:List<NpcCombatantSummary>,val opposingMembers:List<NpcCombatantSummary>,
    val result:com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome?) {
    companion object {
        fun from(state:com.github.nacabaro.vbhelper.battle.offline.core.BattleSnapshot)=NpcBattleSummary(state.elapsedMillis,
            state.alliedMembers.map { NpcCombatantSummary(it.combatantId,it.displayName,it.health,it.maxHealth,it.energy,it.maxEnergy) },
            state.opposingMembers.map { NpcCombatantSummary(it.combatantId,it.displayName,it.health,it.maxHealth,it.energy,it.maxEnergy) },state.result?.outcome)
    }
}
