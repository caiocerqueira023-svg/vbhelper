package com.github.nacabaro.vbhelper.world

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
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
import com.github.nacabaro.vbhelper.domain.digifarm.WildRelationship
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.domain.identity.IndividualIdentity
import com.github.nacabaro.vbhelper.utils.DeviceType
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionRepository
import com.github.nacabaro.vbhelper.world.ecosystem.WorldDen
import com.github.nacabaro.vbhelper.world.ecosystem.WildPairBond
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()
    private val biomeDetector = OpenStreetMapBiomeDetector()
    val interactions = WorldInteractionRepository(db, clock)
    private val _currentBiome = MutableStateFlow(WorldBiome.NULL)
    val currentBiome = _currentBiome.asStateFlow()

    companion object {
        private const val NEARBY_RADIUS_METERS = 350.0
        private const val TARGET_NEARBY_COUNT = 8
        // WorldScreen shows a 1 km radius at its furthest (50%) zoom level.
        private const val WORLD_VISIBLE_RADIUS_METERS = 1_000.0
        private const val TARGET_OUTER_VISIBLE_COUNT = 12
        private const val MAX_SPAWN_PER_CALL = 3
        private const val MAX_OUTER_SPAWN_PER_CALL = TARGET_OUTER_VISIBLE_COUNT
        private const val GLOBAL_ACTIVE_CAP = 60
        private const val DEBUG_SPAWN_RADIUS_METERS = 20.0

        /** Placeholder: recrutamento exige o Digimon ativo com 5000+ vitais. */
        const val RECRUIT_VITALS_REQUIREMENT = 5000
    }

    fun observeSpawns(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getActiveSpawnsWithDetails(clock())

    fun observePendingRecruits(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getPendingRecruitsWithDetails()

    fun observeMood(individualId: String): Flow<Int?> =
        db.wildRelationshipDao().observeTrust(individualId)

    suspend fun ensureSpawns(latitude: Double, longitude: Double) {
        if (GeoPoint.fromOrNull(latitude, longitude) == null) return
        val biome = resolveSpawnBiome(GeoPoint(latitude, longitude))
        ensureSpawnsForBiome(latitude, longitude, biome)
        publishSpawnBiome(biome)
    }

    /** External input resolution is deliberately separate from the coordinator's mutation gate. */
    suspend fun resolveSpawnBiome(position: GeoPoint): WorldBiome = biomeDetector.biomeAt(position.latitude, position.longitude)

    suspend fun ensureSpawnsForBiome(latitude: Double, longitude: Double, biome: WorldBiome) {
        if (GeoPoint.fromOrNull(latitude, longitude) == null) return
        db.withTransaction { ensureSpawnsLocked(latitude, longitude, biome) }
    }

    fun publishSpawnBiome(biome: WorldBiome) { _currentBiome.value = biome }

    /** Creates one short-lived wild encounter near the current player location for debug builds. */
    suspend fun spawnDebugDigimon(latitude: Double, longitude: Double): Long? =
        db.withTransaction { spawnDebugDigimonLocked(latitude, longitude) }

    private suspend fun spawnDebugDigimonLocked(latitude: Double, longitude: Double): Long? {
        val player = GeoPoint.fromOrNull(latitude, longitude) ?: return null
        val now = clock()
        interactions.reconcileLocked()
        spawnDao.deleteExpired(now)

        val characters = db.characterDao().getCharactersForWorldSpawns()
        if (characters.isEmpty()) return null
        val speciesNames = db.speciesProfileDao().getAll().associate { profile ->
            profile.cardCharacterId to (profile.matchedName ?: profile.speciesName)
        }
        val character = WorldSpawnSelector.selectCharacter(characters = characters, speciesNames = speciesNames) ?: return null
        val activeSpawns = spawnDao.getActiveSpawnsSync(now)
        val claimed = db.worldInteractionDao().getClaimedIndividuals().toSet()
        val evictionsNeeded = (activeSpawns.size + 1 - GLOBAL_ACTIVE_CAP).coerceAtLeast(0)
        if (evictionsNeeded > 0) {
            val evictions = activeSpawns.asSequence()
                .filter { it.recruitmentState == RecruitmentState.WILD && !it.interacted && it.individualId !in claimed }
                .sortedByDescending { distanceMeters(latitude, longitude, it.latitude, it.longitude) }
                .take(evictionsNeeded).toList()
            if (evictions.size < evictionsNeeded) return null
            evictions.forEach { spawnDao.deleteById(it.id) }
        }

        val individualId = IndividualIdentity.generate()
        val distance = randomDistanceInArea(0.0, DEBUG_SPAWN_RADIUS_METERS)
        val bearing = Random.nextDouble(0.0, Math.PI * 2)
        val position = RadarWorldGeometry.offset(player, distance * cos(bearing), distance * sin(bearing))
        db.digimonIndividualDao().insert(DigimonIndividual(individualId, now))
        db.digimonIndividualDao().upsertPersonality(
            DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now)
        )
        val spawnId = spawnDao.insert(WorldSpawn(
            cardCharacterId = character.id, individualId = individualId,
            latitude = position.latitude, longitude = position.longitude,
            spawnedAt = now, expiresAt = now + 30 * 60 * 1000, mood = 50,
            recruitmentState = RecruitmentState.WILD
        ))
        db.wildRelationshipDao().insert(WildRelationship(
            individualId = individualId, cardCharacterId = character.id,
            speciesNameSnapshot = speciesNames[character.id], trust = 50,
            createdAt = now, updatedAt = now
        ))
        return spawnId
    }

    private suspend fun ensureSpawnsLocked(latitude: Double, longitude: Double, biome: WorldBiome) {
        val player = GeoPoint.fromOrNull(latitude, longitude) ?: return
        val now = clock()
        interactions.reconcileLocked()
        spawnDao.deleteExpired(now)
        val activeSpawns = spawnDao.getActiveSpawnsSync(now)
        val claimed = db.worldInteractionDao().getClaimedIndividuals().toSet()
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
        val requestedSpawns = nearbyToSpawn + outerToSpawn
        if (requestedSpawns <= 0) return

        // Resolve the pool before evicting anything. If the user has disabled every
        // DiM, the map should preserve its current encounters rather than removing
        // them without being able to replace them.
        val loadedCharacters = db.characterDao().getCharactersForWorldSpawns()
        if (loadedCharacters.isEmpty()) return
        val speciesNames = db.speciesProfileDao().getAll().associate { profile ->
            profile.cardCharacterId to (profile.matchedName ?: profile.speciesName)
        }

        // Keep the cap, but make room for fresh nearby encounters by removing the
        // farthest untouched wild spawns first. A spawn that has been chatted with
        // is protected and remains until its normal expiry (or recruitment flow).
        val evictionsNeeded = (activeSpawns.size + requestedSpawns - GLOBAL_ACTIVE_CAP)
            .coerceAtLeast(0)
        val evictions = activeSpawns
            .asSequence()
            .filter { it.recruitmentState == RecruitmentState.WILD && !it.interacted && it.individualId !in claimed }
            .sortedByDescending {
                distanceMeters(latitude, longitude, it.latitude, it.longitude)
            }
            .take(evictionsNeeded)
            .toList()
        evictions.forEach { spawnDao.deleteById(it.id) }

        val availableSlots = GLOBAL_ACTIVE_CAP - (activeSpawns.size - evictions.size)
        val toSpawn = requestedSpawns.coerceAtMost(availableSlots.coerceAtLeast(0))
        if (toSpawn <= 0) return

        val newNeighbors = mutableListOf<WorldSpawn>()
        val denCenters = mutableMapOf<String, GeoPoint>()
        val denIds = mutableMapOf<String, String>()
        repeat(toSpawn) { spawnIndex ->
            val character = WorldSpawnSelector.selectCharacter(
                characters = loadedCharacters,
                speciesNames = speciesNames,
                favoredAttribute = biome.favoredAttribute
            )
                ?: return@repeat

            val individualId = IndividualIdentity.generate()
            // Preserve the existing eight nearby spawns. Once they are accounted for,
            // fill the outer ring visible at 50% zoom (350 m to 1 km).
            val isNearbySpawn = spawnIndex < nearbyToSpawn
            val distance = if (isNearbySpawn) {
                randomDistanceInArea(45.0, NEARBY_RADIUS_METERS - 15.0)
            } else {
                randomDistanceInArea(NEARBY_RADIUS_METERS + 15.0, WORLD_VISIBLE_RADIUS_METERS - 15.0)
            }
            val bearing = Random.nextDouble(0.0, Math.PI * 2)
            val group = "${if (isNearbySpawn) "near" else "outer"}:${character.attribute}:${if (isNearbySpawn) spawnIndex / 4 else (spawnIndex - nearbyToSpawn) / 4}"
            val center = denCenters.getOrPut(group) { RadarWorldGeometry.offset(player, distance * cos(bearing), distance * sin(bearing)) }
            val denId = denIds.getOrPut(group) { "den:${IndividualIdentity.generate()}" }
            db.worldEcosystemDao().insertDen(WorldDen(denId, center.latitude, center.longitude, now))
            val position = RadarWorldGeometry.offset(center, Random.nextDouble(-8.0, 8.0), Random.nextDouble(-8.0, 8.0))
            val spawn = WorldSpawn(cardCharacterId = character.id, individualId = individualId,
                latitude = position.latitude, longitude = position.longitude, spawnedAt = now,
                expiresAt = now + 30 * 60 * 1000, denId = denId,
                anchorRadiusMeters = Random.nextDouble(15.0, 40.0))

            db.withTransaction {
                db.digimonIndividualDao().insert(DigimonIndividual(individualId, now))
                db.digimonIndividualDao().upsertPersonality(
                    DigimonPersonalityGenerator.generate(individualId, character.attribute, character.stage, now)
                )
                val id = spawnDao.insert(spawn)
                newNeighbors += spawn.copy(id = id)
                db.wildRelationshipDao().insert(
                    WildRelationship(
                        individualId = individualId,
                        cardCharacterId = character.id,
                        speciesNameSnapshot = speciesNames[character.id],
                        trust = 50,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
        val neighbors = (activeSpawns + newNeighbors).filter { it.id !in evictions.map { old -> old.id } }.sortedBy { it.individualId }
        newNeighbors.forEach { actor ->
            neighbors.filter { it.individualId != actor.individualId &&
                distanceMeters(actor.homeLatitude, actor.homeLongitude, it.homeLatitude, it.homeLongitude) <= actor.anchorRadiusMeters + it.anchorRadiusMeters }
                .forEach { neighbor -> db.worldEcosystemDao().insertBond(WildPairBond.create(actor.individualId, neighbor.individualId,
                    if (actor.denId != null && actor.denId == neighbor.denId) 35 else 10)) }
        }
    }

    suspend fun getSpawnEntityByIndividualId(individualId: String): WorldSpawn? =
        spawnDao.getByIndividualId(individualId)

    /**
     * Aplica o delta de mood emitido pelo LLM, mas com peso assimétrico:
     * cai mais rápido do que sobe (mais fácil desagradar do que agradar).
     */
    suspend fun applyWildMoodDelta(individualId: String, rawDelta: Int, interactionId: String? = null): Int? = db.withTransaction {
        validatePlayerChatMutationLocked(individualId, interactionId)
        val spawn = spawnDao.getByIndividualId(individualId)
        val relationship = db.wildRelationshipDao().get(individualId) ?: return@withTransaction null
        val scaledDelta = WildMoodAnalyzer.scaleDelta(rawDelta)
        val newMood = (relationship.trust + scaledDelta).coerceIn(0, 100)
        db.wildRelationshipDao().updateTrust(individualId, newMood, clock())
        if (spawn != null) spawnDao.updateMood(individualId, newMood)
        newMood
    }

    suspend fun meetsRecruitmentRequirements(): Boolean {
        val active = db.userCharacterDao().getActiveCharacter().first() ?: return false
        return active.vitalPoints >= RECRUIT_VITALS_REQUIREMENT
    }

    suspend fun markPendingRecruitment(spawnId: Long) {
        db.withTransaction {
            spawnDao.getSpawnById(spawnId)?.let { markPendingRecruitment(it.individualId) }
        }
    }

    suspend fun markPendingRecruitment(individualId: String, interactionId: String? = null) = db.withTransaction {
        releasePlayerChatForMutationLocked(individualId, interactionId)
        db.wildRelationshipDao().updateRecruitmentState(
            individualId, RecruitmentState.PENDING_RECRUITMENT.name, clock()
        )
        spawnDao.getByIndividualId(individualId)?.let {
            spawnDao.updateRecruitmentState(it.id, RecruitmentState.PENDING_RECRUITMENT.name, Long.MAX_VALUE)
        }
    }

    suspend fun removeSpawn(spawnId: Long, interactionId: String? = null): Boolean = db.withTransaction {
        val spawn = spawnDao.getSpawnById(spawnId) ?: return@withTransaction false
        releasePlayerChatForMutationLocked(spawn.individualId, interactionId)
        spawnDao.deleteById(spawnId) == 1
    }

    /** Participant roles and the unique result ledger are authoritative, including losses. */
    suspend fun recordRadarBattleResult(interactionId: String, outcome: BattleOutcome): Boolean =
        interactions.completeBattle(interactionId, outcome)

    /** Converte o spawn selvagem em um UserCharacter real, no Storage. */
    suspend fun recruitSpawn(spawnId: Long, interactionId: String? = null): Result<Long> {
        val details = spawnDao.getSpawnById(spawnId)
            ?: return Result.failure(IllegalStateException("Spawn not found"))
        // Spawn-based recruitment carries its own species reference; a missing
        // WildRelationship (legacy data) must not block it.
        val relationship = db.wildRelationshipDao().get(details.individualId)
        return doRecruit(details.individualId, relationship?.cardCharacterId ?: details.cardCharacterId, interactionId)
    }

    /** Recruitment also works from an unlocked Digiline contact after its map spawn expires. */
    suspend fun recruitIndividual(individualId: String): Result<Long> {
        val relationship = db.wildRelationshipDao().get(individualId)
            ?: return Result.failure(IllegalStateException("Wild contact not found"))
        return doRecruit(individualId, relationship.cardCharacterId)
    }

    private suspend fun doRecruit(individualId: String, cardCharacterId: Long, interactionId: String? = null): Result<Long> = runCatching {
        check(meetsRecruitmentRequirements()) { "Requirements not met" }
        db.wildRelationshipDao().get(individualId)?.let {
            check(it.recruitmentState != RecruitmentState.RECRUITED.name) { "Already recruited" }
        }
        check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty()) { "Already in Storage" }
        val cardCharacter = db.characterDao().getById(cardCharacterId) ?: error("Species not found")

        val userCharacter = UserCharacter(
            individualId = individualId,
            charId = cardCharacterId,
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
        val now = clock()

        // Character creation is one logical operation. In particular, HomeScreen and
        // watch export both depend on the auxiliary VB rows being present.
        var characterId = 0L
        db.withTransaction {
            releasePlayerChatForMutationLocked(individualId, interactionId)
            check((db.userCharacterDao().getActiveVitalPoints() ?: 0) >= RECRUIT_VITALS_REQUIREMENT) { "Requirements not met" }
            check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty()) { "Already in Storage" }
            // Atomically claim the recruit. A double tap/concurrent recruitment
            // cannot clone it: the spawn row, or the wild contact, is claimed
            // exactly once. World/Storage exclusivity is additionally guarded
            // by the database trigger.
            val liveSpawn = spawnDao.getByIndividualId(individualId)
            if (liveSpawn != null) {
                check(spawnDao.deleteById(liveSpawn.id) == 1) { "This Digimon is no longer available." }
            } else if (db.wildRelationshipDao().get(individualId) != null) {
                check(db.wildRelationshipDao().claimForRecruitment(individualId, now) == 1) { "Already recruited" }
            } else {
                check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty()) { "Already in Storage" }
            }
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
            db.wildRelationshipDao().updateRecruitmentState(
                individualId, RecruitmentState.RECRUITED.name, now
            )
        }
        characterId
    }

    private suspend fun validatePlayerChatMutationLocked(individualId: String, interactionId: String?) {
        interactions.reconcileLocked()
        if (interactionId != null) interactions.requirePrivateChatLocked(interactionId, individualId)
        else interactions.assertUnclaimedLocked(individualId)
    }

    private suspend fun releasePlayerChatForMutationLocked(individualId: String, interactionId: String?) {
        validatePlayerChatMutationLocked(individualId, interactionId)
        if (interactionId != null) interactions.finishPrivateChat(interactionId, individualId)
        interactions.assertUnclaimedLocked(individualId)
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val player = GeoPoint.fromOrNull(lat1, lon1) ?: return Double.POSITIVE_INFINITY
        val target = GeoPoint.fromOrNull(lat2, lon2) ?: return Double.POSITIVE_INFINITY
        return RadarWorldGeometry.relative(player, target).distanceMeters
    }

    /** Uniform distribution over a circular area/ring, not merely over its radius. */
    private fun randomDistanceInArea(minDistance: Double, maxDistance: Double): Double = sqrt(
        minDistance * minDistance +
            Random.nextDouble() * (maxDistance * maxDistance - minDistance * minDistance)
    )

    suspend fun markInteracted(id: Long) = spawnDao.markInteracted(id)

    suspend fun markInteractedByIndividual(individualId: String) =
        spawnDao.markInteractedByIndividual(individualId)

    suspend fun getSpawn(spawnId: Long): WorldDtos.SpawnWithDetails? = spawnDao.getSpawnById(spawnId)
}
