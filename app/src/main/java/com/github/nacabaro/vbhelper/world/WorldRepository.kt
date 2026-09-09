package com.github.nacabaro.vbhelper.world

import com.github.nacabaro.vbhelper.daos.WorldSpawnDao
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import kotlinx.coroutines.flow.Flow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()

    fun observeSpawns(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getActiveSpawnsWithDetails(System.currentTimeMillis())

    suspend fun ensureSpawns(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        spawnDao.deleteExpired(now)
        if (spawnDao.countActive(now) >= 8) return
        repeat(3) {
            val character = db.characterDao().getRandomCharacter() ?: return@repeat
            val individualId = IndividualIdentity.generate()
            db.digimonIndividualDao().insert(DigimonIndividual(individualId, now))
            db.digimonIndividualDao().insertPersonality(
                DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now)
            )
            val distance = Random.nextDouble(40.0, 300.0)
            val bearing = Random.nextDouble(0.0, Math.PI * 2)
            val latOffset = distance * cos(bearing) / 111_320.0
            val lonOffset = distance * sin(bearing) / (111_320.0 * cos(Math.toRadians(latitude)).coerceAtLeast(0.1))
            spawnDao.insert(
                WorldSpawn(
                    cardCharacterId = character.id,
                    individualId = individualId,
                    latitude = latitude + latOffset,
                    longitude = longitude + lonOffset,
                    spawnedAt = now,
                    expiresAt = now + 30 * 60 * 1000
                )
            )
        }
    }

    suspend fun markInteracted(id: Long) = spawnDao.markInteracted(id)

    suspend fun getSpawn(spawnId: Long): WorldDtos.SpawnWithDetails? = spawnDao.getSpawnById(spawnId)
}
