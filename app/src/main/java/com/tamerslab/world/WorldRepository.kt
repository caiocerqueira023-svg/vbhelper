package com.tamerslab.world

import androidx.room.withTransaction
import com.tamerslab.battle.offline.core.BattleOutcome
import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.world.data.WorldSpawnDao
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.device_data.DigimonIndividual
import com.tamerslab.domain.device_data.UserCharacter
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.hardware.nfc.vb.SpecialMission
import com.tamerslab.domain.device_data.VBCharacterData
import com.tamerslab.domain.device_data.TransformationHistory
import com.tamerslab.domain.personality.DigimonPersonalityGenerator
import com.tamerslab.domain.world.RecruitmentState
import com.tamerslab.domain.world.WorldSpawn
import com.tamerslab.domain.digifarm.WildRelationship
import com.tamerslab.dtos.WorldDtos
import com.tamerslab.dtos.DebugSpawnCharacter
import com.tamerslab.domain.identity.IndividualIdentity
import com.tamerslab.utils.DeviceType
import com.tamerslab.world.ecosystem.WorldInteractionRepository
import com.tamerslab.world.ecosystem.WorldDen
import com.tamerslab.world.ecosystem.WildPairBond
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class WorldRepository(private val db: AppDatabase,
                      private val scanPercentagePerDefeat: () -> Int = { com.tamerslab.species.data.DigimonScanPolicy.NORMAL_GAIN },
                       private val clock: () -> Long = System::currentTimeMillis,
                       private val debugEnabled: () -> Boolean = { false },
                       private val allowAnyDistance: () -> Boolean = { false }) {
    private val spawnDao: WorldSpawnDao = db.worldSpawnDao()
    private val biomeDetector = OpenStreetMapBiomeDetector()
    val interactions = WorldInteractionRepository(db, scanPercentagePerDefeat, allowAnyDistance, clock)
    private val _currentBiome = MutableStateFlow(WorldBiome.NULL)
    val currentBiome = _currentBiome.asStateFlow()

    companion object {
        /** Chat trust gains slow down at/above this value so quests stay the compelling path to maximum. */
        const val CONTACT_SOFT_CAP = 75
        private const val NEARBY_RADIUS_METERS = 350.0
        private const val TARGET_NEARBY_COUNT = 8
        // WorldScreen shows a 1 km radius at its furthest (50%) zoom level.
        private const val WORLD_VISIBLE_RADIUS_METERS = 1_000.0
        private const val TARGET_OUTER_VISIBLE_COUNT = 12
        private const val MAX_SPAWN_PER_CALL = 3
        private const val MAX_OUTER_SPAWN_PER_CALL = TARGET_OUTER_VISIBLE_COUNT
        private const val GLOBAL_ACTIVE_CAP = 60
        private const val DEBUG_SPAWN_RADIUS_METERS = 20.0

    }

    fun observeSpawns(): Flow<List<WorldDtos.SpawnWithDetails>> {
        val source = spawnDao.getActiveSpawnsWithDetails(clock())
        return db.participation?.let { gate -> source.combine(gate.observe()) { rows, eligibility ->
            rows.filter { eligibility.card(it.cardCharacterId).usable }
        } } ?: source
    }

    fun observePendingRecruits(): Flow<List<WorldDtos.SpawnWithDetails>> =
        spawnDao.getPendingRecruitsWithDetails()

    fun observeMood(individualId: String): Flow<Int?> =
        db.wildRelationshipDao().observeTrust(individualId)

    suspend fun ensureSpawns(latitude: Double, longitude: Double) {
        if (GeoPoint.fromOrNull(latitude, longitude) == null) return
        prepareSpawns()
        val biome = resolveSpawnBiome(GeoPoint(latitude, longitude))
        ensureSpawnsForBiome(latitude, longitude, biome)
        publishSpawnBiome(biome)
    }

    /** External input resolution is deliberately separate from the coordinator's mutation gate. */
    suspend fun resolveSpawnBiome(position: GeoPoint): WorldBiome = biomeDetector.biomeAt(position.latitude, position.longitude)

    /** Resolve bundled artwork before entering the Radar coordinator's database transaction. */
    suspend fun prepareSpawns() {
        db.expandedCatalog?.ensureReadyDefinitions()
        // Clean encounters that would otherwise render as the placeholder marker forever.
        spawnDao.deletePlaceholderSpawns()
    }

    suspend fun ensureSpawnsForBiome(latitude: Double, longitude: Double, biome: WorldBiome) {
        if (GeoPoint.fromOrNull(latitude, longitude) == null) return
        db.withTransaction {
            materializeQuestTargetsLocked(latitude, longitude)
            ensureSpawnsLocked(latitude, longitude, biome)
        }
        runCatching { ensureFirstPartnerEncounter(latitude, longitude) }
    }

    /**
     * Guaranteed, non-combat first meeting for any partner-less player. Spawns one
     * eligible Child-stage partner with high starting trust so ordinary conversation
     * leads to its recruitment quest; recorded once and never repeated.
     */
    suspend fun ensureFirstPartnerEncounter(latitude: Double, longitude: Double): Long? {
        val player = GeoPoint.fromOrNull(latitude, longitude) ?: return null
        if (db.userCharacterDao().getAllCharacterIds().isNotEmpty()) return null
        val catalog = db.expandedCatalog ?: return null
        if (db.firstPartnerDao().current()?.completedAt != null) return null
        val starter = catalog.starterDefinition()
        if (db.participation?.snapshot()?.card(starter.id)?.usable == false) return null
        val now = clock()
        val individualId = IndividualIdentity.generate()
        val position = RadarWorldGeometry.offset(player, 30.0, 30.0)
        val speciesName = db.speciesProfileDao().getByCardCharacterId(starter.id)
            ?.let { it.speciesName?.takeIf(String::isNotBlank) ?: it.matchedName } ?: "Digimon"
        db.withTransaction {
            if (db.userCharacterDao().getAllCharacterIds().isNotEmpty()) return@withTransaction
            if (db.firstPartnerDao().current() != null) return@withTransaction
            db.digimonIndividualDao().insert(DigimonIndividual(individualId, now, speciesName = speciesName,
                originKind = com.tamerslab.domain.device_data.IndividualOrigin.EXPANDED))
            db.digimonIndividualDao().upsertPersonality(
                DigimonPersonalityGenerator.generate(individualId, starter.attribute, starter.stage, now))
            val spawnId = spawnDao.insert(WorldSpawn(cardCharacterId = starter.id, individualId = individualId,
                latitude = position.latitude, longitude = position.longitude, spawnedAt = now,
                expiresAt = now + 24 * 60 * 60 * 1000, mood = 85))
            db.wildRelationshipDao().insert(WildRelationship(individualId, starter.id, speciesName,
                trust = 85, createdAt = now, updatedAt = now))
            db.firstPartnerDao().record(com.tamerslab.domain.species.FirstPartnerEncounter(
                individualId = individualId, speciesId = starter.speciesId.orEmpty(), step = 1))
            spawnId
        }
        return spawnDao.getByIndividualId(individualId)?.id
    }

    fun publishSpawnBiome(biome: WorldBiome) { _currentBiome.value = biome }

    private suspend fun materializeQuestTargetsLocked(latitude: Double, longitude: Double) {
        val player = GeoPoint.fromOrNull(latitude, longitude) ?: return
        val now = clock()
        val claimed = db.worldInteractionDao().getClaimedIndividuals().toSet()
        for (objective in db.questDao().pendingTargets()) {
            val individual = objective.targetIndividualId ?: continue
            val existing = spawnDao.getByIndividualId(individual)
            if (existing != null && (individual in claimed || distanceMeters(latitude, longitude, existing.latitude, existing.longitude) <= WORLD_VISIBLE_RADIUS_METERS)) continue
            if (db.userCharacterDao().getByIndividualIdSync(individual).isNotEmpty()) continue
            if (existing == null && spawnDao.countActive(now) >= GLOBAL_ACTIVE_CAP) break
            val character = objective.targetCardCharacterId?.let { db.characterDao().getById(it) } ?: continue
            if (db.participation?.snapshot()?.card(character.id)?.usable == false) continue
            val random = Random(individual.hashCode())
            val distance = 30.0 + random.nextDouble() * 90.0
            val angle = random.nextDouble() * Math.PI * 2
            val point = RadarWorldGeometry.offset(player, distance * cos(angle), distance * sin(angle))
            if (existing == null) {
                spawnDao.insert(WorldSpawn(cardCharacterId = character.id, individualId = individual,
                    latitude = point.latitude, longitude = point.longitude, spawnedAt = now,
                    expiresAt = Long.MAX_VALUE, interacted = true))
            } else {
                spawnDao.relocateQuestTarget(existing.id, point.latitude, point.longitude,
                    db.worldEcosystemDao().getSession()?.tickIndex ?: existing.movementTick)
            }
        }
        spawnDao.releaseFinishedQuestTargets(now + 30 * 60 * 1000L)
    }

    suspend fun getDebugSpawnCharacters(): List<DebugSpawnCharacter> {
        if (!debugEnabled()) return emptyList()
        return db.characterDao().getDebugSpawnCharacters().takeIf { debugEnabled() }.orEmpty()
    }

    /** An explicit debug selection may use any loaded card, regardless of automatic spawn settings. */
    suspend fun spawnDebugDigimon(latitude: Double, longitude: Double, cardCharacterId: Long? = null): Long? {
        if (!debugEnabled()) return null
        return db.withTransaction {
            if (!debugEnabled()) return@withTransaction null
            val result = spawnDebugDigimonLocked(latitude, longitude, cardCharacterId)
            check(debugEnabled()) { "Debug access was revoked" }
            result
        }
    }

    private suspend fun spawnDebugDigimonLocked(latitude: Double, longitude: Double, cardCharacterId: Long?): Long? {
        val player = GeoPoint.fromOrNull(latitude, longitude) ?: return null
        val speciesNames = db.speciesProfileDao().getAll().associate { profile ->
            profile.cardCharacterId to (profile.matchedName ?: profile.speciesName)
        }
        val character = if (cardCharacterId != null) {
            db.characterDao().getById(cardCharacterId)
        } else {
            WorldSpawnSelector.selectCharacter(
                characters = db.characterDao().getCharactersForWorldSpawns(), speciesNames = speciesNames)
        } ?: return null
        // Placeholder-art characters are data markers, not Digimon; they must never spawn in the world.
        if (character.spriteId == com.tamerslab.species.PlaceholderSprite.ID.toLong()) return null
        val now = clock()
        interactions.reconcileLocked()
        spawnDao.deleteExpired(now)
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
        val loadedCharacters = (db.characterDao().getCharactersForWorldSpawns() + db.characterDao().getCatalogDefinitions())
            .distinctBy { it.speciesId ?: it.id.toString() }
            .filter { it.spriteId != com.tamerslab.species.PlaceholderSprite.ID.toLong() }
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
     * Acima de 75 de confiança, ganhos positivos caem para um quarto (mínimo +1)
     * para que as quests continuem sendo o caminho atraente até o máximo;
     * perdas nunca são desaceleradas.
     */
    suspend fun applyWildMoodDelta(individualId: String, rawDelta: Int, interactionId: String? = null): Int? = db.withTransaction {
        validatePlayerChatMutationLocked(individualId, interactionId)
        val spawn = spawnDao.getByIndividualId(individualId)
        val relationship = db.wildRelationshipDao().get(individualId) ?: return@withTransaction null
        val scaledDelta = WildMoodAnalyzer.scaleDelta(rawDelta)
        // Fast bonding below the contact threshold, slow grind above it, always moving.
        val pacedDelta = if (scaledDelta > 0 && relationship.trust >= CONTACT_SOFT_CAP) {
            (scaledDelta / 4).coerceAtLeast(1)
        } else scaledDelta
        val newMood = (relationship.trust + pacedDelta).coerceIn(0, 100)
        db.wildRelationshipDao().updateTrust(individualId, newMood, clock())
        if (spawn != null) spawnDao.updateMood(individualId, newMood)
        newMood
    }

    suspend fun meetsRecruitmentRequirements(individualId: String): Boolean =
        db.withTransaction {
            db.participation?.snapshot()?.individual(individualId)?.usable != false &&
                com.tamerslab.quests.QuestRepository(db).recruitmentReadyLocked(individualId)
        }

    suspend fun markPendingRecruitment(spawnId: Long) {
        db.withTransaction {
            spawnDao.getSpawnById(spawnId)?.let { markPendingRecruitment(it.individualId) }
        }
    }

    suspend fun markPendingRecruitment(individualId: String, interactionId: String? = null) = db.withTransaction {
        db.participation?.snapshot()?.requireIndividual(individualId)
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
    suspend fun recordRadarBattleResult(interactionId: String, outcome: BattleOutcome,
        items: List<com.tamerslab.battle.offline.core.BattleItemSnapshot>? = null,
        snapshot: com.tamerslab.battle.offline.core.BattleSnapshot? = null): Boolean = db.withTransaction {
        if (snapshot != null) {
            require(snapshot.result?.outcome == outcome)
            com.tamerslab.quests.QuestBattleFacts(db).recordTerminalLocked(interactionId, snapshot, clock())
        }
        val inventory = snapshot?.trainingItems ?: items
        if (inventory != null) com.tamerslab.quests.QuestBattleInventory(db).checkpointLocked(interactionId, inventory, clock())
        interactions.completeBattle(interactionId, outcome)
    }

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
    suspend fun recruitIndividual(individualId: String, interactionId: String? = null): Result<Long> {
        val relationship = db.wildRelationshipDao().get(individualId)
            ?: return Result.failure(IllegalStateException("Wild contact not found"))
        return doRecruit(individualId, relationship.cardCharacterId, interactionId)
    }

    private suspend fun doRecruit(individualId: String, cardCharacterId: Long, interactionId: String? = null): Result<Long> = runCatching {
        check(meetsRecruitmentRequirements(individualId)) { "Complete this Digimon's recruitment quest first." }
        db.wildRelationshipDao().get(individualId)?.let {
            check(it.recruitmentState != RecruitmentState.RECRUITED.name) { "Already recruited" }
        }
        check(db.userCharacterDao().getByIndividualIdSync(individualId).isEmpty()) { "Already in Storage" }
        val cardCharacter = db.characterDao().getById(cardCharacterId) ?: error("Species not found")
        // Catalog definitions recruit without any physical card; imported slots keep the legacy path.
        val card = cardCharacter.cardId?.let { db.cardDao().getCardById(it) }
        if (cardCharacter.cardId != null && card == null) error("Card not found")
        val quest = db.questDao().forGiver(individualId).singleOrNull { it.category == com.tamerslab.quests.QuestCategory.RECRUITMENT }
            ?: error("Complete this Digimon's recruitment quest first.")
        val partnerFamily = quest.partnerDeviceType ?: quest.partnerId?.let {
            db.userCharacterDao().getByIndividualIdSync(it).singleOrNull()?.characterType
        }
        val appProfile = card == null
        val beProfile = !appProfile && (card.isBEm || partnerFamily == DeviceType.BEDevice)

        val userCharacter = UserCharacter(
            individualId = individualId,
            charId = cardCharacterId,
            ageInDays = 0,
            mood = 80,
            vitalPoints = 0,
            transformationCountdown = if (beProfile) 0 else 1,
            injuryStatus = NfcCharacter.InjuryStatus.None,
            trophies = 0,
            currentPhaseBattlesWon = 0,
            currentPhaseBattlesLost = 0,
            totalBattlesWon = 0,
            totalBattlesLost = 0,
            activityLevel = 0,
            heartRateCurrent = 0,
            characterType = if (appProfile) DeviceType.APP else if (beProfile) DeviceType.BEDevice else DeviceType.VBDevice,
            isActive = false
        )
        val now = clock()

        // Character creation is one logical operation. In particular, HomeScreen and
        // watch export both depend on the auxiliary VB rows being present.
        var characterId = 0L
        db.withTransaction {
            db.participation?.snapshot()?.requireCard(cardCharacterId)
            releasePlayerChatForMutationLocked(individualId, interactionId)
            check(com.tamerslab.quests.QuestRepository(db).recruitmentReadyLocked(individualId)) { "Complete this Digimon's recruitment quest first." }
            check(db.questDao().unfinishedTargetCount(individualId) == 0) { "This Digimon is still needed by an active quest." }
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
            db.openHelper.writableDatabase.execSQL(
                "INSERT OR IGNORE INTO AppPartnerProgress(individualId, trainingHp, trainingAp, trainingBp) VALUES (?, 0, 0, 0)",
                arrayOf<Any?>(individualId))

            if (appProfile) {
                // App partners keep permanent training on the individual; no watch rows are invented.
            } else if (beProfile) {
                db.userCharacterDao().insertBECharacterData(com.tamerslab.domain.device_data.BECharacterData(
                    id = characterId, trainingHp = 0, trainingAp = 0, trainingBp = 0,
                    remainingTrainingTimeInMinutes = 6000, itemEffectMentalStateValue = 0,
                    itemEffectMentalStateMinutesRemaining = 0, itemEffectActivityLevelValue = 0,
                    itemEffectActivityLevelMinutesRemaining = 0, itemEffectVitalPointsChangeValue = 0,
                    itemEffectVitalPointsChangeMinutesRemaining = 0, abilityRarity = NfcCharacter.AbilityRarity.None,
                    abilityType = 0, abilityBranch = 0, abilityReset = 0, rank = 0, itemType = 0,
                    itemMultiplier = 0, itemRemainingTime = 0, otp0 = "", otp1 = "", minorVersion = 0, majorVersion = 0))
            } else {
                db.userCharacterDao().insertVBCharacterData(VBCharacterData(id = characterId, generation = 0, totalTrophies = 0))
            }

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
            if (!beProfile && !appProfile) db.userCharacterDao().insertSpecialMissions(*missions.toTypedArray())

            val definitionCardId = cardCharacter.cardId
            if (definitionCardId != null) db.dexDao().insertCharacter(cardCharacter.charaIndex, definitionCardId, now)
            else db.dexDao().recordDefinition(cardCharacter.id, now)
            com.tamerslab.storage.data.EvolutionHistoryRepository(db).repairCharacter(characterId)
            db.wildRelationshipDao().updateRecruitmentState(
                individualId, RecruitmentState.RECRUITED.name, now
            )
            com.tamerslab.quests.QuestRepository(db).completeRecruitmentLocked(individualId)
            if (db.firstPartnerDao().current()?.individualId == individualId) {
                db.firstPartnerDao().complete(now)
            }
        }
        characterId
    }

    private suspend fun validatePlayerChatMutationLocked(individualId: String, interactionId: String?) {
        db.participation?.snapshot()?.requireIndividual(individualId)
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
