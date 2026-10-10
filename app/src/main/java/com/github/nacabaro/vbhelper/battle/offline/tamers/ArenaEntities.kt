package com.github.nacabaro.vbhelper.battle.offline.tamers

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ArenaRun")
data class ArenaRunEntity(@PrimaryKey val id: String, val stateJson: String, val revision: Int,
    val updatedAt: Long)

@Entity(tableName = "ArenaMatch", indices = [Index("tamerId"), Index("tournamentId")])
data class ArenaMatchEntity(@PrimaryKey val id: String, val tournamentId: String?, val tamerId: String,
    val format: Int, val stage: Int, val difficulty: String, val specJson: String, val outcome: String?,
    val rewardBits: Int, val alliedItemsUsed: Int, val opposingItemsUsed: Int,
    val createdAt: Long, val completedAt: Long?)

@Entity(tableName = "ArenaRewardReceipt")
data class ArenaRewardReceipt(@PrimaryKey val id: String, val bits: Int, val createdAt: Long)

data class ArenaTamerRecord(val tamerId: String, val victories: Int, val defeats: Int, val fights: Int)
data class ArenaSettlement(val outcome: com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome,
    val rewardBits: Int, val tiebreak: Boolean = false)
