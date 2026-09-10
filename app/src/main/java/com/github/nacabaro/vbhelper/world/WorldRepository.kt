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
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()

    companion object {
        /** Raio, em metros, dentro do qual contamos os spawns como "próximos ao jogador". */
        private const val NEARBY_RADIUS_METERS = 350.0
        /** Quantos Digimon devem existir dentro do raio próximo ao jogador. */
        private const val TARGET_NEARBY_COUNT = 8
        /** Limite de novos spawns por chamada, para não gerar tudo de uma vez. */
        private const val MAX_SPAWN_PER_CALL = 3
        /** Teto global de spawns ativos no banco, independente de distância. */
        private const val GLOBAL_ACTIVE_CAP = 60

        /** Probabilidade de spawn por estágio (0=Baby I ... 5=Ultimate). Soma = 100%. */
        private val STAGE_WEIGHTS = listOf(
            0 to 0.15, // Baby I
            1 to 0.15, // Baby II
            2 to 0.40, // Child
            3 to 0.24, // Adult
            4 to 0.05, // Perfect
            5 to 0.01  // Ultimate
        )
    }

    fun observeSpawns(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getActiveSpawnsWithDetails(System.currentTimeMillis())

    suspend fun ensureSpawns(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        spawnDao.deleteExpired(now)

        if (spawnDao.countActive(now) >= GLOBAL_ACTIVE_CAP) return

        val activeSpawns = spawnDao.getActiveSpawnsSync(now)
        val nearbyCount = activeSpawns.count {
            distanceMeters(latitude, longitude, it.latitude, it.longitude) <= NEARBY_RADIUS_METERS
        }
        val toSpawn = (TARGET_NEARBY_COUNT - nearbyCount).coerceIn(0, MAX_SPAWN_PER_CALL)
        if (toSpawn <= 0) return

        repeat(toSpawn) {
            val stage = pickWeightedStage()
            val character = db.characterDao().getRandomCharacterForStage(stage)
                ?: db.characterDao().getRandomCharacter()
                ?: return@repeat

            val individualId = IndividualIdentity.generate()
            db.digimonIndividualDao().insert(DigimonIndividual(individualId, now))
            db.digimonIndividualDao().insertPersonality(
                DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now)
            )

            val distance = Random.nextDouble(30.0, NEARBY_RADIUS_METERS)
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

    private fun pickWeightedStage(random: Random = Random.Default): Int {
        val roll = random.nextDouble()
        var cumulative = 0.0
        for ((stage, weight) in STAGE_WEIGHTS) {
            cumulative += weight
            if (roll < cumulative) return stage
        }
        return STAGE_WEIGHTS.last().first
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val northMeters = (lat2 - lat1) * 111_320.0
        val eastMeters = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians(lat1))
        return sqrt(northMeters * northMeters + eastMeters * eastMeters)
    }

    suspend fun markInteracted(id: Long) = spawnDao.markInteracted(id)

    suspend fun getSpawn(spawnId: Long): WorldDtos.SpawnWithDetails? = spawnDao.getSpawnById(spawnId)
}
