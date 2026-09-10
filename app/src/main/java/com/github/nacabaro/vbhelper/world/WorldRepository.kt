package com.github.nacabaro.vbhelper.world

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.daos.WorldSpawnDao
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.device_data.DigimonIndividual
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.domain.device_data.SpecialMissions
import com.github.cfogrady.vbnfc.vb.SpecialMission
import com.github.nacabaro.vbhelper.domain.device_data.VBCharacterData
import com.github.nacabaro.vbhelper.domain.device_data.TransformationHistory
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityGenerator
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.utils.DeviceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()

    companion object {
        private const val NEARBY_RADIUS_METERS = 350.0
        private const val TARGET_NEARBY_COUNT = 8
        private const val MAX_SPAWN_PER_CALL = 3
        private const val GLOBAL_ACTIVE_CAP = 60

        private val STAGE_WEIGHTS = listOf(
            0 to 0.15, // Baby I
            1 to 0.15, // Baby II
            2 to 0.40, // Child
            3 to 0.24, // Adult
            4 to 0.05, // Perfect
            5 to 0.01  // Ultimate
        )

        /** Placeholder: recrutamento exige o Digimon ativo com 5000+ vitais. */
        const val RECRUIT_VITALS_REQUIREMENT = 5000
    }

    fun observeSpawns(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getActiveSpawnsWithDetails(System.currentTimeMillis())

    fun observePendingRecruits(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getPendingRecruitsWithDetails()

    fun observeMood(individualId: String): Flow<Int?> =
        spawnDao.observeMoodByIndividualId(individualId)

    suspend fun ensureSpawns(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        spawnDao.deleteExpired(now)

        if (spawnDao.countActive(now) >= GLOBAL_ACTIVE_CAP) return

        val activeSpawns = spawnDao.getActiveSpawnsSync(now)
        val nearbyCount = activeSpawns.count {
            it.recruitmentState == RecruitmentState.WILD &&
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
                    expiresAt = now + 30 * 60 * 1000,
                    mood = 50,
                    recruitmentState = RecruitmentState.WILD
                )
            )
        }
    }

    suspend fun getSpawnEntityByIndividualId(individualId: String): WorldSpawn? =
        spawnDao.getByIndividualId(individualId)

    /**
     * Aplica o delta de mood emitido pelo LLM, mas com peso assimétrico:
     * cai mais rápido do que sobe (mais fácil desagradar do que agradar).
     */
    suspend fun applyWildMoodDelta(individualId: String, rawDelta: Int): Int? {
        val spawn = spawnDao.getByIndividualId(individualId) ?: return null
        val scaledDelta = WildMoodAnalyzer.scaleDelta(rawDelta)
        val newMood = (spawn.mood + scaledDelta).coerceIn(0, 100)
        spawnDao.updateMood(individualId, newMood)
        return newMood
    }

    suspend fun meetsRecruitmentRequirements(): Boolean {
        val active = db.userCharacterDao().getActiveCharacter().first() ?: return false
        return active.vitalPoints >= RECRUIT_VITALS_REQUIREMENT
    }

    suspend fun markPendingRecruitment(spawnId: Long) {
        // expiresAt bem no futuro para não ser limpo pela rotina de expiração.
        spawnDao.updateRecruitmentState(spawnId, RecruitmentState.PENDING_RECRUITMENT.name, Long.MAX_VALUE)
    }

    suspend fun removeSpawn(spawnId: Long) {
        spawnDao.deleteById(spawnId)
    }

    /** Converte o spawn selvagem em um UserCharacter real, no Storage. */
    suspend fun recruitSpawn(spawnId: Long): Result<Long> = runCatching {
        check(meetsRecruitmentRequirements()) { "Requirements not met" }
        val details = spawnDao.getSpawnById(spawnId) ?: error("Spawn not found")
        val cardCharacter = db.characterDao().getById(details.cardCharacterId) ?: error("Species not found")

        val userCharacter = UserCharacter(
            individualId = details.individualId,
            charId = details.cardCharacterId,
            ageInDays = 0,
            mood = 80,
            vitalPoints = 0,
            transformationCountdown = 0,
            injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0,
            currentPhaseBattlesWon = 0,
            currentPhaseBattlesLost = 0,
            totalBattlesWon = 0,
            totalBattlesLost = 0,
            activityLevel = 0,
            heartRateCurrent = 0,
            characterType = DeviceType.VBDevice,
            isActive = false
        )
        val now = System.currentTimeMillis()

        // Character creation is one logical operation. In particular, HomeScreen and
        // watch export both depend on the auxiliary VB rows being present.
        var characterId = 0L
        db.runInTransaction {
            characterId = db.userCharacterDao().insertCharacterData(userCharacter)

            db.userCharacterDao().insertVBCharacterData(
                VBCharacterData(id = characterId, generation = 0, totalTrophies = 0)
            )

            // Use the CardCharacter primary key directly. The previous implementation
            // looked it up again by (charaIndex, cardId), which could leave a character
            // without TransformationHistory after a partial failure.
            db.userCharacterDao().insertTransformationHistory(
                TransformationHistory(
                    monId = characterId,
                    stageId = cardCharacter.id,
                    transformationDate = now
                )
            )

            // NFC-created VB characters always have four mission slots. World recruits
            // must have the same complete data shape so they can be exported later.
            val missions = (0 until 4).map { slot ->
                SpecialMissions(
                    characterId = characterId,
                    goal = 0,
                    watchId = ((characterId * 4 + slot) % 65535L).toInt().coerceAtLeast(1),
                    progress = 0,
                    status = SpecialMission.Status.UNAVAILABLE,
                    timeElapsedInMinutes = 0,
                    timeLimitInMinutes = 0,
                    missionType = SpecialMission.Type.NONE
                )
            }
            db.userCharacterDao().insertSpecialMissions(*missions.toTypedArray())

            db.dexDao().insertCharacter(cardCharacter.charaIndex, cardCharacter.cardId, now)
            spawnDao.deleteById(spawnId)

        }

        characterId
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
