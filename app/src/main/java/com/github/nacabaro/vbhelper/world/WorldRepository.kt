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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()
    private val biomeDetector = OpenStreetMapBiomeDetector()
    private val _currentBiome = MutableStateFlow(WorldBiome.NULL)
    val currentBiome = _currentBiome.asStateFlow()

    /** Last GPS fix observed by the world screen; used when starting follow from chat. */
    @Volatile
    var lastKnownLatitude: Double? = null
        private set
    @Volatile
    var lastKnownLongitude: Double? = null
        private set

    fun updateLastKnownLocation(latitude: Double, longitude: Double) {
        lastKnownLatitude = latitude
        lastKnownLongitude = longitude
    }

    companion object {
        private const val NEARBY_RADIUS_METERS = 350.0
        private const val TARGET_NEARBY_COUNT = 8
        // WorldScreen shows a 1 km radius at its furthest (50%) zoom level.
        private const val WORLD_VISIBLE_RADIUS_METERS = 1_000.0
        private const val TARGET_OUTER_VISIBLE_COUNT = 12
        private const val MAX_SPAWN_PER_CALL = 3
        private const val MAX_OUTER_SPAWN_PER_CALL = TARGET_OUTER_VISIBLE_COUNT
        private const val GLOBAL_ACTIVE_CAP = 60

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
        val biome = biomeDetector.biomeAt(latitude, longitude)
        _currentBiome.value = biome

        if (spawnDao.countActive(now) >= GLOBAL_ACTIVE_CAP) return

        val activeSpawns = spawnDao.getActiveSpawnsSync(now)
        val nearbyCount = activeSpawns.count {
            it.recruitmentState == RecruitmentState.WILD &&
                distanceMeters(latitude, longitude, it.latitude, it.longitude) <= NEARBY_RADIUS_METERS
        }
        val outerVisibleCount = activeSpawns.count {
            it.recruitmentState == RecruitmentState.WILD &&
                distanceMeters(latitude, longitude, it.latitude, it.longitude) > NEARBY_RADIUS_METERS &&
                distanceMeters(latitude, longitude, it.latitude, it.longitude) <= WORLD_VISIBLE_RADIUS_METERS
        }
        val nearbyNeeded = (TARGET_NEARBY_COUNT - nearbyCount).coerceAtLeast(0)
        val outerNeeded = (TARGET_OUTER_VISIBLE_COUNT - outerVisibleCount).coerceAtLeast(0)
        val nearbyToSpawn = nearbyNeeded.coerceAtMost(MAX_SPAWN_PER_CALL)
        // Outer spawns are additional: the existing nearby spawn rate/count is untouched.
        val outerToSpawn = outerNeeded.coerceAtMost(MAX_OUTER_SPAWN_PER_CALL)
        val toSpawn = (nearbyToSpawn + outerToSpawn)
            .coerceAtMost(GLOBAL_ACTIVE_CAP - activeSpawns.size)
        if (toSpawn <= 0) return

        val loadedCharacters = db.characterDao().getCharactersForWorldSpawns()
        if (loadedCharacters.isEmpty()) return
        val speciesNames = db.speciesProfileDao().getAll().associate { profile ->
            profile.cardCharacterId to (profile.matchedName ?: profile.speciesName)
        }

        repeat(toSpawn) { spawnIndex ->
            val character = WorldSpawnSelector.selectCharacter(
                characters = loadedCharacters,
                speciesNames = speciesNames,
                favoredAttribute = biome.favoredAttribute
            )
                ?: return@repeat

            val individualId = IndividualIdentity.generate()
            db.digimonIndividualDao().insert(DigimonIndividual(individualId, now))
            db.digimonIndividualDao().insertPersonality(
                DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now)
            )

            // Preserve the existing eight nearby spawns. Once they are accounted for,
            // fill the outer ring visible at 50% zoom (350 m to 1 km).
            val isNearbySpawn = spawnIndex < nearbyToSpawn
            val distance = if (isNearbySpawn) {
                randomDistanceInArea(30.0, NEARBY_RADIUS_METERS)
            } else {
                randomDistanceInArea(NEARBY_RADIUS_METERS, WORLD_VISIBLE_RADIUS_METERS)
            }
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
        // If mood fell below the follow threshold while following, stop immediately.
        if (spawn.isFollowing && newMood < WorldSpawn.FOLLOW_STOP_MOOD) {
            stopFollowing(individualId)
        }
        return newMood
    }

    /**
     * Starts temporary following after the first chat message raised the mood.
     * The Digimon will track the player so conversation can continue while walking.
     * Mood decays ~[WorldSpawn.FOLLOW_MOOD_LOSS_PER_SEGMENT] points every
     * ~[WorldSpawn.FOLLOW_SEGMENT_METERS] meters moved; following ends below
     * [WorldSpawn.FOLLOW_STOP_MOOD].
     */
    suspend fun startFollowing(
        individualId: String,
        playerLatitude: Double,
        playerLongitude: Double
    ): Boolean {
        val spawn = spawnDao.getByIndividualId(individualId) ?: return false
        if (spawn.recruitmentState != RecruitmentState.WILD) return false
        if (spawn.isFollowing) return true
        if (spawn.mood < WorldSpawn.FOLLOW_STOP_MOOD) return false

        val now = System.currentTimeMillis()
        // Keep the spawn alive while following (30 more minutes from now).
        val newExpires = maxOf(spawn.expiresAt, now + 30 * 60 * 1000L)
        spawnDao.updateFollowingState(
            individualId = individualId,
            isFollowing = true,
            followLastLat = playerLatitude,
            followLastLon = playerLongitude,
            expiresAt = newExpires
        )
        return true
    }

    suspend fun stopFollowing(individualId: String) {
        val spawn = spawnDao.getByIndividualId(individualId) ?: return
        if (!spawn.isFollowing) return
        spawnDao.updateFollowingState(
            individualId = individualId,
            isFollowing = false,
            followLastLat = null,
            followLastLon = null,
            expiresAt = spawn.expiresAt
        )
    }

    fun observeIsFollowing(individualId: String): Flow<Boolean?> =
        spawnDao.observeIsFollowing(individualId)

    /**
     * Called on every meaningful GPS update while the world screen is open.
     * For each Digimon currently following the player:
     *  - measures meters walked since the last deduction point
     *  - deducts mood in segments of [WorldSpawn.FOLLOW_SEGMENT_METERS]
     *  - relocates the Digimon near the player so it stays in interaction range
     *  - stops following if mood falls below [WorldSpawn.FOLLOW_STOP_MOOD]
     *
     * @return list of individualIds that stopped following on this tick (for UI toasts)
     */
    suspend fun processFollowMovement(
        playerLatitude: Double,
        playerLongitude: Double
    ): List<String> {
        val now = System.currentTimeMillis()
        val following = spawnDao.getFollowingSpawns(now)
        if (following.isEmpty()) return emptyList()

        val stopped = mutableListOf<String>()
        for (spawn in following) {
            val lastLat = spawn.followLastLat ?: playerLatitude
            val lastLon = spawn.followLastLon ?: playerLongitude
            val walked = distanceMeters(lastLat, lastLon, playerLatitude, playerLongitude)

            // How many full 2 m segments were walked since last checkpoint
            val segments = floor(walked / WorldSpawn.FOLLOW_SEGMENT_METERS).toInt()
            var newMood = spawn.mood
            var stillFollowing = true
            var newLastLat = lastLat
            var newLastLon = lastLon

            if (segments > 0) {
                val moodLoss = segments * WorldSpawn.FOLLOW_MOOD_LOSS_PER_SEGMENT
                newMood = (spawn.mood - moodLoss).coerceIn(0, 100)
                // Advance the checkpoint by the exact segments consumed so residual meters carry over.
                val consumedMeters = segments * WorldSpawn.FOLLOW_SEGMENT_METERS
                val fraction = (consumedMeters / walked).coerceIn(0.0, 1.0)
                newLastLat = lastLat + (playerLatitude - lastLat) * fraction
                newLastLon = lastLon + (playerLongitude - lastLon) * fraction

                if (newMood < WorldSpawn.FOLLOW_STOP_MOOD) {
                    stillFollowing = false
                    stopped.add(spawn.individualId)
                }
            }

            // Keep the Digimon near the player with a stable offset derived from its id
            // so the radar marker does not jitter on every GPS tick.
            val digimonLat: Double
            val digimonLon: Double
            if (stillFollowing) {
                val seed = spawn.id * 31L + 17L
                val offsetDistance = 6.0 + (seed % 5) // 6–10 m
                val bearing = ((seed % 360) / 360.0) * Math.PI * 2
                val latOffset = offsetDistance * cos(bearing) / 111_320.0
                val lonOffset = offsetDistance * sin(bearing) /
                    (111_320.0 * cos(Math.toRadians(playerLatitude)).coerceAtLeast(0.1))
                digimonLat = playerLatitude + latOffset
                digimonLon = playerLongitude + lonOffset
            } else {
                digimonLat = spawn.latitude
                digimonLon = spawn.longitude
            }

            val newExpires = if (stillFollowing) {
                maxOf(spawn.expiresAt, now + 30 * 60 * 1000L)
            } else {
                spawn.expiresAt
            }

            spawnDao.updateFollowProgress(
                id = spawn.id,
                latitude = digimonLat,
                longitude = digimonLon,
                mood = newMood,
                isFollowing = stillFollowing,
                followLastLat = if (stillFollowing) newLastLat else null,
                followLastLon = if (stillFollowing) newLastLon else null,
                expiresAt = newExpires
            )
        }
        return stopped
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
            // Atomically claim the spawn. A double tap/concurrent recruitment cannot clone it.
            check(spawnDao.deleteById(spawnId) == 1) { "This Digimon is no longer available in World." }
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
            com.github.nacabaro.vbhelper.source.EvolutionHistoryRepository(db).repairCharacter(characterId)

        }

        characterId
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val northMeters = (lat2 - lat1) * 111_320.0
        val eastMeters = (lon2 - lon1) * 111_320.0 * cos(Math.toRadians(lat1))
        return sqrt(northMeters * northMeters + eastMeters * eastMeters)
    }

    /** Uniform distribution over a circular area/ring, not merely over its radius. */
    private fun randomDistanceInArea(minDistance: Double, maxDistance: Double): Double = sqrt(
        minDistance * minDistance +
            Random.nextDouble() * (maxDistance * maxDistance - minDistance * minDistance)
    )

    suspend fun markInteracted(id: Long) = spawnDao.markInteracted(id)

    suspend fun getSpawn(spawnId: Long): WorldDtos.SpawnWithDetails? = spawnDao.getSpawnById(spawnId)
}
