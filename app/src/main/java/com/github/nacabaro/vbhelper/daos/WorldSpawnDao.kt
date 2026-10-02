package com.github.nacabaro.vbhelper.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import kotlinx.coroutines.flow.Flow
import com.github.nacabaro.vbhelper.domain.world.WorldMovementState

@Dao
@RewriteQueriesToDropUnusedColumns
interface WorldSpawnDao {
    @Insert
    suspend fun insert(spawn: WorldSpawn): Long

    @Query("SELECT COUNT(*) FROM WorldSpawn WHERE expiresAt > :now OR EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = WorldSpawn.id)")
    suspend fun countActive(now: Long): Int

    @Query("SELECT * FROM WorldSpawn WHERE expiresAt > :now OR EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = WorldSpawn.id) ORDER BY spawnedAt DESC")
    suspend fun getActiveSpawnsSync(now: Long): List<WorldSpawn>

    @Query("SELECT * FROM WorldSpawn WHERE individualId = :individualId LIMIT 1")
    suspend fun getByIndividualId(individualId: String): WorldSpawn?

    @Query("UPDATE WorldSpawn SET interacted = 1 WHERE id = :id")
    suspend fun markInteracted(id: Long)

    @Query("UPDATE WorldSpawn SET interacted = 1 WHERE individualId = :individualId")
    suspend fun markInteractedByIndividual(individualId: String)

    @Query("UPDATE WorldSpawn SET mood = :mood WHERE individualId = :individualId")
    suspend fun updateMood(individualId: String, mood: Int)

    @Query("""
        UPDATE WorldSpawn SET latitude=:latitude, longitude=:longitude,
            wanderTargetLatitude=:targetLatitude, wanderTargetLongitude=:targetLongitude,
            movementState=:state, movementTick=:tick, nextDecisionTick=:nextDecisionTick
        WHERE individualId=:id AND recruitmentState='WILD' AND movementTick <= :tick
    """)
    suspend fun checkpointMovement(id: String, latitude: Double, longitude: Double, targetLatitude: Double?, targetLongitude: Double?,
        state: WorldMovementState, tick: Long, nextDecisionTick: Long)

    @Query("UPDATE WorldSpawn SET ecosystemEmotion=MAX(-100,MIN(100,ecosystemEmotion+:delta)) WHERE individualId=:id")
    suspend fun adjustEcosystemEmotion(id: String, delta: Int)

    @Query("UPDATE WorldSpawn SET denId=:denId WHERE individualId=:id") suspend fun assignDen(id: String, denId: String)

    @Query("SELECT mood FROM WorldSpawn WHERE individualId = :individualId LIMIT 1")
    fun observeMoodByIndividualId(individualId: String): Flow<Int?>

    @Query("UPDATE WorldSpawn SET recruitmentState = :state, expiresAt = :expiresAt WHERE id = :id")
    suspend fun updateRecruitmentState(id: Long, state: String, expiresAt: Long)

    @Query("DELETE FROM WorldSpawn WHERE id = :id AND NOT EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = WorldSpawn.id)")
    fun deleteById(id: Long): Int

    @Query("DELETE FROM WorldSpawn WHERE expiresAt <= :now AND NOT EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = WorldSpawn.id)")
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
            cc.stage AS stage,
            cc.cardId AS cardId,
            'dim' || printf('%03d', ca.cardId) || '_mon' || printf('%02d', cc.charaIndex + 1) AS externalCharacterId,
            cc.attribute AS attribute,
            cc.baseHp AS baseHp,
            cc.baseBp AS baseBp,
            cc.baseAp AS baseAp,
            ca.isBEm AS isBemCard,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.spriteWalk1 AS spriteWalk,
            s.spriteWalk2 AS spriteWalk2,
            s.spriteRun1 AS spriteRun,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Card ca ON ca.id = cc.cardId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE (ws.expiresAt > :now OR EXISTS(SELECT 1 FROM WorldParticipationClaim WHERE spawnId = ws.id))
            AND ws.recruitmentState = 'WILD'
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
            cc.stage AS stage,
            cc.cardId AS cardId,
            'dim' || printf('%03d', ca.cardId) || '_mon' || printf('%02d', cc.charaIndex + 1) AS externalCharacterId,
            cc.attribute AS attribute,
            cc.baseHp AS baseHp,
            cc.baseBp AS baseBp,
            cc.baseAp AS baseAp,
            ca.isBEm AS isBemCard,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.spriteWalk1 AS spriteWalk,
            s.spriteWalk2 AS spriteWalk2,
            s.spriteRun1 AS spriteRun,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Card ca ON ca.id = cc.cardId
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
            cc.stage AS stage,
            cc.cardId AS cardId,
            'dim' || printf('%03d', ca.cardId) || '_mon' || printf('%02d', cc.charaIndex + 1) AS externalCharacterId,
            cc.attribute AS attribute,
            cc.baseHp AS baseHp,
            cc.baseBp AS baseBp,
            cc.baseAp AS baseAp,
            ca.isBEm AS isBemCard,
            s.spriteIdle1 AS spriteIdle,
            s.spriteIdle2 AS spriteIdle2,
            s.spriteWalk1 AS spriteWalk,
            s.spriteWalk2 AS spriteWalk2,
            s.spriteRun1 AS spriteRun,
            s.spriteRun2 AS spriteRun2,
            s.width AS spriteWidth,
            s.height AS spriteHeight,
            sp.speciesName AS speciesName
        FROM WorldSpawn ws
        JOIN CardCharacter cc ON cc.id = ws.cardCharacterId
        JOIN Card ca ON ca.id = cc.cardId
        JOIN Sprite s ON s.id = cc.spriteId
        LEFT JOIN SpeciesProfile sp ON sp.cardCharacterId = cc.id
        WHERE ws.id = :spawnId
        """
    )
    suspend fun getSpawnById(spawnId: Long): WorldDtos.SpawnWithDetails?

}
