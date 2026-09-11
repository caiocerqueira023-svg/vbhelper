package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface WorldSpawnDao {
    @Insert
    suspend fun insert(spawn: WorldSpawn): Long

    @Query("SELECT COUNT(*) FROM WorldSpawn WHERE expiresAt > :now")
    suspend fun countActive(now: Long): Int

    @Query("SELECT * FROM WorldSpawn WHERE expiresAt > :now ORDER BY spawnedAt DESC")
    suspend fun getActiveSpawnsSync(now: Long): List<WorldSpawn>

    @Query("SELECT * FROM WorldSpawn WHERE individualId = :individualId LIMIT 1")
    suspend fun getByIndividualId(individualId: String): WorldSpawn?

    @Query("UPDATE WorldSpawn SET interacted = 1 WHERE id = :id")
    suspend fun markInteracted(id: Long)

    @Query("UPDATE WorldSpawn SET mood = :mood WHERE individualId = :individualId")
    suspend fun updateMood(individualId: String, mood: Int)

    @Query("SELECT mood FROM WorldSpawn WHERE individualId = :individualId LIMIT 1")
    fun observeMoodByIndividualId(individualId: String): Flow<Int?>

    @Query("UPDATE WorldSpawn SET recruitmentState = :state, expiresAt = :expiresAt WHERE id = :id")
    suspend fun updateRecruitmentState(id: Long, state: String, expiresAt: Long)

    @Query("DELETE FROM WorldSpawn WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM WorldSpawn WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long)

    @Query(
        """
        SELECT
            ws.id AS id,
            ws.cardCharacterId AS cardCharacterId,
            ws.individualId AS individualId,
            ws.latitude AS latitude,
            ws.longitude AS longitude,
            ws.spawnedAt AS spawnedAt,
            ws.expiresAt AS expiresAt,
            ws.interacted AS interacted,
            ws.mood AS mood,
            ws.recruitmentState AS recruitmentState,
            ws.isFollowing AS isFollowing,
            cc.charaIndex AS charaIndex,
            cc.cardId AS cardId,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE ws.expiresAt > :now AND ws.recruitmentState = 'WILD'
        ORDER BY ws.spawnedAt DESC
        """
    )
    fun getActiveSpawnsWithDetails(now: Long): Flow<List<WorldDtos.SpawnWithDetails>>

    @Query(
        """
        SELECT
            ws.id AS id,
            ws.cardCharacterId AS cardCharacterId,
            ws.individualId AS individualId,
            ws.latitude AS latitude,
            ws.longitude AS longitude,
            ws.spawnedAt AS spawnedAt,
            ws.expiresAt AS expiresAt,
            ws.interacted AS interacted,
            ws.mood AS mood,
            ws.recruitmentState AS recruitmentState,
            ws.isFollowing AS isFollowing,
            cc.charaIndex AS charaIndex,
            cc.cardId AS cardId,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE ws.recruitmentState = 'PENDING_RECRUITMENT'
        ORDER BY ws.spawnedAt DESC
        """
    )
    fun getPendingRecruitsWithDetails(): Flow<List<WorldDtos.SpawnWithDetails>>

    @Query(
        """
        SELECT
            ws.id AS id,
            ws.cardCharacterId AS cardCharacterId,
            ws.individualId AS individualId,
            ws.latitude AS latitude,
            ws.longitude AS longitude,
            ws.spawnedAt AS spawnedAt,
            ws.expiresAt AS expiresAt,
            ws.interacted AS interacted,
            ws.mood AS mood,
            ws.recruitmentState AS recruitmentState,
            ws.isFollowing AS isFollowing,
            cc.charaIndex AS charaIndex,
            cc.cardId AS cardId,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE ws.id = :spawnId
        """
    )
    suspend fun getSpawnById(spawnId: Long): WorldDtos.SpawnWithDetails?

    @Query(
        """
        SELECT * FROM WorldSpawn
        WHERE isFollowing = 1
          AND recruitmentState = 'WILD'
          AND expiresAt > :now
        """
    )
    suspend fun getFollowingSpawns(now: Long): List<WorldSpawn>

    @Query(
        """
        UPDATE WorldSpawn SET
            isFollowing = :isFollowing,
            followLastLat = :followLastLat,
            followLastLon = :followLastLon,
            expiresAt = :expiresAt
        WHERE individualId = :individualId
        """
    )
    suspend fun updateFollowingState(
        individualId: String,
        isFollowing: Boolean,
        followLastLat: Double?,
        followLastLon: Double?,
        expiresAt: Long
    )

    @Query(
        """
        UPDATE WorldSpawn SET
            latitude = :latitude,
            longitude = :longitude,
            mood = :mood,
            isFollowing = :isFollowing,
            followLastLat = :followLastLat,
            followLastLon = :followLastLon,
            expiresAt = :expiresAt
        WHERE id = :id
        """
    )
    suspend fun updateFollowProgress(
        id: Long,
        latitude: Double,
        longitude: Double,
        mood: Int,
        isFollowing: Boolean,
        followLastLat: Double?,
        followLastLon: Double?,
        expiresAt: Long
    )

    @Query("SELECT isFollowing FROM WorldSpawn WHERE individualId = :individualId LIMIT 1")
    fun observeIsFollowing(individualId: String): Flow<Boolean?>
}
