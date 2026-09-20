package com.github.nacabaro.vbhelper.digifarm

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.digifarm.map.BirdFarmMap
import com.github.nacabaro.vbhelper.domain.digifarm.Farm
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessage
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMessageRecipient
import com.github.nacabaro.vbhelper.domain.digifarm.FarmReadState
import com.github.nacabaro.vbhelper.domain.digifarm.FarmMemory
import com.github.nacabaro.vbhelper.domain.digifarm.FarmResident
import com.github.nacabaro.vbhelper.dtos.FarmResidentWithDetails
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.math.hypot
import kotlin.random.Random

class DigifarmRepository(private val db: AppDatabase) {
    private val dao = db.digifarmDao()

    fun observeFarms(): Flow<List<Farm>> = dao.observeFarms()
    fun observeResidents(farmId: String): Flow<List<FarmResidentWithDetails>> = dao.observeResidents(farmId)
    fun observeResidentIds(): Flow<List<String>> = dao.observeResidentIds()
    fun observeResidentCharacterIds(): Flow<List<Long>> = dao.observeResidentCharacterIds()
    fun observeAssignments() = dao.observeAssignments()
    fun observeMessages(farmId: String, limit: Int = 50): Flow<List<FarmMessage>> = dao.observeMessages(farmId, limit)
    fun observeStorageThreads() = dao.observeStorageThreads()
    fun observeFarmThreads() = dao.observeFarmThreads()
    fun observeWildThreads() = db.wildRelationshipDao().observeUnlocked()
    suspend fun residentsSnapshot(farmId: String) = observeResidents(farmId).first()
    suspend fun messagesSnapshot(farmId: String, limit: Int = 8) = observeMessages(farmId, limit).first().asReversed()
    suspend fun recipientIds(messageId: String) = dao.recipientIds(messageId)
    suspend fun getFarm(farmId: String) = dao.getFarm(farmId)

    suspend fun createFarm(name: String): Farm {
        val now = System.currentTimeMillis()
        val farm = Farm(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Bird Digi-Farm" },
            createdAt = now,
            lastSimulatedAt = now,
            randomSeed = Random.nextLong()
        )
        dao.insertFarm(farm)
        return farm
    }

    suspend fun addResident(farmId: String, characterId: Long) = db.withTransaction {
        val farm = dao.getFarm(farmId) ?: error("Digifarm not found")
        check(dao.residentCount(farmId) < farm.capacity) { "This Digifarm is full" }
        val character = db.userCharacterDao().getCharacter(characterId)
        val slot = BirdFarmMap.safeSpawns[dao.residentCount(farmId) % BirdFarmMap.safeSpawns.size]
        val now = System.currentTimeMillis()
        dao.insertResident(
            FarmResident(
                individualId = character.individualId,
                farmId = farmId,
                positionX = slot.x,
                positionY = slot.y,
                targetX = slot.x,
                targetY = slot.y,
                activityStartedAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun removeResident(individualId: String) = dao.removeResident(individualId)

    /**
     * Atomic transfer between farms (§13): capacity + existence checks, cancels the
     * ongoing activity/conversation target and picks a safe spot in the destination.
     * The individual keeps identity, relationships and memories; only position and
     * farm occupancy change.
     */
    suspend fun transferResident(individualId: String, targetFarmId: String) = db.withTransaction {
        val resident = dao.getResident(individualId) ?: error("Resident not found")
        if (resident.farmId == targetFarmId) return@withTransaction
        val target = dao.getFarm(targetFarmId) ?: error("Digifarm not found")
        check(target.archivedAt == null) { "Digifarm is archived" }
        check(dao.residentCount(targetFarmId) < target.capacity) { "This Digifarm is full" }
        val slot = BirdFarmMap.safeSpawns[dao.residentCount(targetFarmId) % BirdFarmMap.safeSpawns.size]
        val now = System.currentTimeMillis()
        dao.updateResident(
            resident.copy(
                farmId = targetFarmId,
                positionX = slot.x,
                positionY = slot.y,
                targetX = slot.x,
                targetY = slot.y,
                activity = "EXPLORE",
                activityStartedAt = now,
                updatedAt = now
            )
        )
    }

    /**
     * Archiving frees residents and stops simulation but keeps the group history
     * readable (§13). Definitive deletion is a separate explicit step.
     */
    suspend fun archiveFarm(farmId: String) = db.withTransaction {
        dao.clearResidents(farmId)
        dao.archiveFarm(farmId, System.currentTimeMillis())
    }

    /**
     * Factual offline summary (§14): coarse needs advancement already happens in
     * [simulateStep]; this describes it without fabricating conversations.
     * Returns null when there is nothing worth announcing.
     */
    suspend fun summarizeReturn(farmId: String, now: Long = System.currentTimeMillis()): String? {
        val farm = dao.getFarm(farmId) ?: return null
        val elapsed = (now - farm.lastSimulatedAt).coerceAtLeast(0)
        if (elapsed < 60_000L) return null
        val clampedHours = (elapsed / 3_600_000L).coerceAtMost(8L)
        val residents = dao.getResidentEntities(farmId)
        if (residents.isEmpty()) return null
        val rested = residents.count { it.activity == "REST" }
        return if (clampedHours <= 0) {
            "While you were away (${elapsed / 60_000L} min), residents kept exploring. No conversations were invented."
        } else {
            "After about $clampedHours h away, ${residents.size} residents kept living locally" +
                (if (rested > 0) " ($rested resting)" else "") +
                ". No conversations were invented while you were gone."
        }
    }

    suspend fun setActivity(individualId: String, farmId: String, activity: String) {
        val resident = dao.getResidentEntities(farmId).firstOrNull { it.individualId == individualId } ?: return
        val now = System.currentTimeMillis()
        dao.updateResident(resident.copy(activity = activity, activityStartedAt = now, updatedAt = now))
    }

    /** Nudges a resident toward a point (approach before talking, §8). */
    suspend fun steerToward(farmId: String, individualId: String, x: Float, y: Float) {
        val resident = dao.getResidentEntities(farmId).firstOrNull { it.individualId == individualId } ?: return
        val safe = BirdFarmMap.closestWalkable(com.github.nacabaro.vbhelper.digifarm.map.MapPoint(x, y))
        dao.updateResident(
            resident.copy(
                targetX = safe.x,
                targetY = safe.y,
                activity = "EXPLORE",
                activityStartedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveCamera(farmId: String, scale: Float, x: Float, y: Float) =
        dao.updateCamera(farmId, scale, x, y)

    suspend fun save3dCamera(
        farmId: String,
        yaw: Float,
        pitch: Float,
        distance: Float,
        targetX: Float,
        targetZ: Float
    ) = dao.update3dCamera(farmId, yaw, pitch, distance, targetX, targetZ)

    suspend fun setAutonomousDialogue(farmId: String, enabled: Boolean) =
        dao.setAutonomousDialogue(farmId, enabled)

    suspend fun simulateStep(farmId: String, now: Long = System.currentTimeMillis()) {
        val farm = dao.getFarm(farmId) ?: return
        val residents = dao.getResidentEntities(farmId)
        val reservedPositions = residents.associate { resident ->
            resident.individualId to com.github.nacabaro.vbhelper.digifarm.map.MapPoint(
                resident.positionX,
                resident.positionY
            )
        }.toMutableMap()
        residents.forEachIndexed { index, resident ->
            val elapsedSeconds = ((now - resident.updatedAt).coerceAtLeast(0) / 1000f).coerceAtMost(30f)
            // Exceptional recovery: feet outside walkable ground return to a safe cell
            // of the same map and emit no teleport across islands (diagnostic via target reset).
            var current = com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.positionX, resident.positionY)
            if (!BirdFarmMap.isWalkable(current)) {
                current = BirdFarmMap.closestWalkable(current)
            }
            var target = BirdFarmMap.closestWalkable(com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.targetX, resident.targetY))
            val distance = hypot((target.x - current.x).toDouble(), (target.y - current.y).toDouble()).toFloat()
            if (distance < 5f || now - resident.activityStartedAt > 25_000L) {
                val choice = Random(farm.randomSeed xor now / 10_000L xor index.toLong())
                val nextActivity = activityFor(resident, now)
                val spots = BirdFarmMap.activityPoints[nextActivity]
                val raw = if (spots != null && choice.nextBoolean()) {
                    spots[choice.nextInt(spots.size)]
                } else {
                    BirdFarmMap.safeSpawns[choice.nextInt(BirdFarmMap.safeSpawns.size)]
                }
                target = BirdFarmMap.closestWalkable(raw)
            }
            val path = BirdFarmMap.findPath(current, target)
            val next = path.firstOrNull() ?: current
            val dx = next.x - current.x
            val dy = next.y - current.y
            val segment = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val travel = (24f * elapsedSeconds).coerceAtMost(segment)
            val candidate = if (resident.activity in MOVING_ACTIVITIES) {
                com.github.nacabaro.vbhelper.digifarm.map.MapPoint(
                    current.x + dx / segment * travel,
                    current.y + dy / segment * travel
                )
            } else {
                current
            }
            val isReserved = reservedPositions.any { (otherId, position) ->
                otherId != resident.individualId &&
                    hypot(
                        (position.x - candidate.x).toDouble(),
                        (position.y - candidate.y).toDouble()
                    ) < MIN_RESIDENT_DISTANCE
            }
            val position = if (isReserved) current else candidate
            reservedPositions[resident.individualId] = position
            // State changes use coarse time buckets so the 900 ms animation loop cannot
            // inflate needs, while a return after time away is still summarized locally.
            val stateTicks = ((now / STATE_TICK_MILLIS) - (resident.updatedAt / STATE_TICK_MILLIS))
                .coerceIn(0L, MAX_OFFLINE_STATE_TICKS)
                .toInt()
            val nextActivity = activityFor(resident, now)
            dao.updateResident(
                resident.copy(
                    positionX = position.x,
                    positionY = position.y,
                    targetX = target.x,
                    targetY = target.y,
                    facingLeft = dx < 0,
                    activity = nextActivity,
                    energy = (resident.energy - stateTicks / 2 + when (resident.activity) {
                        "REST" -> stateTicks * 2
                        "TRAIN" -> -stateTicks
                        else -> 0
                    }).coerceIn(10, 100),
                    satiety = (resident.satiety - stateTicks / 3 + if (resident.activity == "EAT") stateTicks * 3 else 0).coerceIn(10, 100),
                    social = (resident.social - stateTicks / 6 + if (resident.activity in setOf("PLAY", "SOCIALIZE")) stateTicks else 0).coerceIn(10, 100),
                    funLevel = (resident.funLevel - stateTicks / 6 + if (resident.activity == "PLAY") stateTicks * 2 else 0).coerceIn(10, 100),
                    activityStartedAt = if (
                        target.x != resident.targetX || target.y != resident.targetY || nextActivity != resident.activity
                    ) now else resident.activityStartedAt,
                    updatedAt = now
                )
            )
        }
        dao.updateFarm(farm.copy(lastSimulatedAt = now))
    }

    suspend fun postMessage(
        farmId: String,
        authorIndividualId: String?,
        authorName: String,
        body: String,
        recipientIds: List<String> = emptyList(),
        type: String = if (authorIndividualId == null) "USER" else "SPEECH",
        sessionId: String? = null
    ): FarmMessage = db.withTransaction {
        val clean = body.trim().take(600)
        require(clean.isNotEmpty())
        val message = FarmMessage(
            id = UUID.randomUUID().toString(),
            farmId = farmId,
            sequence = dao.nextSequence(farmId),
            type = type,
            authorIndividualId = authorIndividualId,
            authorNameSnapshot = authorName,
            body = clean,
            audience = if (recipientIds.isEmpty()) "ALL" else if (recipientIds.size == 1) "INDIVIDUAL" else "SUBSET",
            sessionId = sessionId,
            timestamp = System.currentTimeMillis()
        )
        dao.insertMessageWithRecipients(message, recipientIds.distinct().map { FarmMessageRecipient(message.id, it) })
        message
    }

    suspend fun markRead(farmId: String, sequence: Long) = dao.upsertReadState(FarmReadState(farmId, sequence))

    suspend fun recordInteraction(firstId: String, secondId: String) {
        if (firstId == secondId) return
        val now = System.currentTimeMillis()
        dao.recordInteraction(firstId, secondId, now)
        dao.recordInteraction(secondId, firstId, now)
    }

    suspend fun rememberConversation(
        observerId: String,
        otherId: String,
        eventId: String,
        summary: String
    ) {
        dao.insertMemory(
            FarmMemory(
                observerId = observerId,
                relatedIndividualId = otherId,
                eventId = eventId,
                summary = summary.take(240),
                relevance = 60,
                createdAt = System.currentTimeMillis()
            )
        )
        dao.pruneMemories(observerId)
    }

    suspend fun memories(individualId: String) = dao.getMemories(individualId)

    private fun activityFor(resident: FarmResident, now: Long): String {
        if (now - resident.activityStartedAt < 20_000L) return resident.activity
        if (resident.energy < 25) return "REST"
        if (resident.satiety < 25) return "EAT"
        val phase = ((now / 20_000L + resident.individualId.hashCode()) % 5).toInt()
        return listOf("EXPLORE", "PLAY", "TRAIN", "SOCIALIZE", "EXPLORE")[kotlin.math.abs(phase)]
    }

    private companion object {
        const val MIN_RESIDENT_DISTANCE = 24.0
        const val STATE_TICK_MILLIS = 30_000L
        const val MAX_OFFLINE_STATE_TICKS = 960L
        val MOVING_ACTIVITIES = setOf("EXPLORE", "PLAY", "SOCIALIZE")
    }
}
