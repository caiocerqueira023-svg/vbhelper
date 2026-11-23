package com.tamerslab.rank.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tamer_profile")
data class TamerProfileEntity(
    @PrimaryKey val id: Int = 0,
    val totalTxp: Long = 0L,
    val level: Int = 1,
    val rank: Int = 0,
    val lastLevelUpAt: Long? = null,
    val lastRankUpAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

@Entity(
    tableName = "tamer_txp_event",
    indices = [Index(value = ["idempotency_key"], unique = true)],
)
data class TamerTxpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val source: String,
    val amount: Int,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    val digimonRef: String? = null,
    val origin: String? = null,
    val metadata: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
)

@Entity(tableName = "tamer_daily_cap", primaryKeys = ["day", "source"])
data class TamerDailyCapEntity(
    val day: String,
    val source: String,
    val total: Int = 0,
)

@Entity(tableName = "tamer_reward", primaryKeys = ["rank", "slot"])
data class TamerRankRewardEntity(
    val rank: Int,
    val slot: Int,
    val kind: String,
    val amount: Int,
    val labelKey: String,
    @ColumnInfo(name = "claimed_at") val claimedAt: Long? = null,
)

@Entity(tableName = "tamer_activity")
data class TamerActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val source: String,
    val delta: Int,
    val summary: String,
)
