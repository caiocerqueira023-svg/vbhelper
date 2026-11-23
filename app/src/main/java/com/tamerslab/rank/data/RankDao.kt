package com.tamerslab.rank.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RankDao {
    @Query("SELECT * FROM tamer_profile WHERE id = 0")
    fun observeProfile(): Flow<TamerProfileEntity?>

    @Query("SELECT * FROM tamer_profile WHERE id = 0")
    suspend fun profile(): TamerProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: TamerProfileEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: TamerTxpEventEntity): Long

    @Query("UPDATE tamer_txp_event SET amount = :amount WHERE id = :id")
    suspend fun updateEventAmount(id: Long, amount: Int)

    @Query("SELECT * FROM tamer_daily_cap WHERE day = :day AND source = :source")
    suspend fun dailyCap(day: String, source: String): TamerDailyCapEntity?

    @Query("SELECT * FROM tamer_daily_cap WHERE day = :day")
    fun observeDailyCaps(day: String): Flow<List<TamerDailyCapEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDailyCap(cap: TamerDailyCapEntity)

    @Query("SELECT * FROM tamer_reward ORDER BY rank ASC, slot ASC")
    fun observeRewards(): Flow<List<TamerRankRewardEntity>>

    @Query("SELECT * FROM tamer_reward WHERE rank = :rank AND slot = :slot")
    suspend fun reward(rank: Int, slot: Int): TamerRankRewardEntity?

    @Query("SELECT * FROM tamer_reward WHERE rank = :rank ORDER BY slot ASC")
    suspend fun rewardsForRank(rank: Int): List<TamerRankRewardEntity>

    @Query("SELECT * FROM tamer_reward WHERE claimed_at IS NULL ORDER BY rank ASC, slot ASC")
    suspend fun unclaimedRewards(): List<TamerRankRewardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRewards(rewards: List<TamerRankRewardEntity>)

    @Query("UPDATE tamer_reward SET claimed_at = :claimedAt WHERE rank = :rank AND slot = :slot AND claimed_at IS NULL")
    suspend fun claimReward(rank: Int, slot: Int, claimedAt: Long): Int

    @Query("UPDATE tamer_reward SET claimed_at = :claimedAt WHERE claimed_at IS NULL")
    suspend fun claimAllRewards(claimedAt: Long): Int

    @Insert
    suspend fun insertActivity(activity: TamerActivityEntity): Long

    @Query("SELECT * FROM tamer_activity ORDER BY occurred_at DESC, id DESC LIMIT 40")
    fun observeActivity(): Flow<List<TamerActivityEntity>>
}
