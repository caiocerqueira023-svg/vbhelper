package com.github.nacabaro.vbhelper.daos

import androidx.room.*
import com.github.nacabaro.vbhelper.battle.offline.tamers.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ArenaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRun(run: ArenaRunEntity): Long

    @Query("SELECT * FROM ArenaRun WHERE id = :id")
    suspend fun run(id: String): ArenaRunEntity?

    @Query("SELECT * FROM ArenaRun ORDER BY updatedAt DESC LIMIT 20")
    fun runs(): Flow<List<ArenaRunEntity>>

    @Query("UPDATE ArenaRun SET stateJson = :json, revision = revision + 1, updatedAt = :now WHERE id = :id AND revision = :revision")
    suspend fun updateRun(id: String, revision: Int, json: String, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatch(match: ArenaMatchEntity): Long

    @Query("SELECT * FROM ArenaMatch WHERE id = :id")
    suspend fun match(id: String): ArenaMatchEntity?

    @Query("SELECT * FROM ArenaMatch WHERE tournamentId = :id AND completedAt IS NULL")
    suspend fun pendingTournamentMatches(id: String): List<ArenaMatchEntity>

    @Query("UPDATE ArenaMatch SET specJson = :json WHERE id = :id AND completedAt IS NULL")
    suspend fun rewritePendingSpec(id: String, json: String): Int

    @Query("SELECT * FROM ArenaMatch WHERE completedAt IS NOT NULL ORDER BY completedAt DESC LIMIT 50")
    fun recentMatches(): Flow<List<ArenaMatchEntity>>

    @Query("SELECT tamerId, SUM(CASE WHEN outcome = 'ALLIED_VICTORY' THEN 1 ELSE 0 END) AS victories, SUM(CASE WHEN outcome = 'OPPOSING_VICTORY' THEN 1 ELSE 0 END) AS defeats, COUNT(*) AS fights FROM ArenaMatch WHERE completedAt IS NOT NULL AND outcome != 'ABANDONED' GROUP BY tamerId")
    fun records(): Flow<List<ArenaTamerRecord>>

    @Query("SELECT COUNT(*) FROM ArenaMatch WHERE tamerId = :tamerId AND format = :format AND outcome = 'ALLIED_VICTORY'")
    suspend fun victories(tamerId: String, format: Int): Int

    @Query("UPDATE ArenaMatch SET outcome = :outcome, rewardBits = :reward, alliedItemsUsed = :alliedItems, opposingItemsUsed = :opposingItems, completedAt = :now WHERE id = :id AND completedAt IS NULL")
    suspend fun completeMatch(id: String, outcome: String, reward: Int, alliedItems: Int, opposingItems: Int, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReceipt(receipt: ArenaRewardReceipt): Long
}
