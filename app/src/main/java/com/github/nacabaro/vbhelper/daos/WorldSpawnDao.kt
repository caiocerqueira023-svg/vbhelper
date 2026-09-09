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

    @Query("UPDATE WorldSpawn SET interacted = 1 WHERE id = :id")
    suspend fun markInteracted(id: Long)

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
        WHERE ws.expiresAt > :now
        ORDER BY ws.spawnedAt DESC
        """
    )
    fun getActiveSpawnsWithDetails(now: Long): Flow<List<WorldDtos.SpawnWithDetails>>
}
