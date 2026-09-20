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
    fun observeMessages(farmId: String, limit: Int = 100): Flow<List<FarmMessage>> = dao.observeMessages(farmId, limit)
    fun observeStorageThreads() = dao.observeStorageThreads()
    fun observeFarmThreads() = dao.observeFarmThreads()
    fun observeWildThreads() = db.wildRelationshipDao().observeUnlocked()
    suspend fun residentsSnapshot(farmId: String) = observeResidents(farmId).first()
    suspend fun messagesSnapshot(farmId: String, limit: Int = 20) = observeMessages(farmId, limit).first().asReversed()
    suspend fun recipientIds(messageId: String) = dao.recipientIds(messageId)

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

    suspend fun setActivity(individualId: String, farmId: String, activity: String) {
        val resident = dao.getResidentEntities(farmId).firstOrNull { it.individualId == individualId } ?: return
        val now = System.currentTimeMillis()
        dao.updateResident(resident.copy(activity = activity, activityStartedAt = now, updatedAt = now))
    }

    suspend fun saveCamera(farmId: String, scale: Float, x: Float, y: Float) =
        dao.updateCamera(farmId, scale, x, y)

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
            var target = BirdFarmMap.closestWalkable(com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.targetX, resident.targetY))
            val distance = hypot((target.x - resident.positionX).toDouble(), (target.y - resident.positionY).toDouble()).toFloat()
            if (distance < 5f || now - resident.activityStartedAt > 25_000L) {
                val choice = Random(farm.randomSeed xor now / 10_000L xor index.toLong())
                target = BirdFarmMap.safeSpawns[choice.nextInt(BirdFarmMap.safeSpawns.size)]
            }
            val path = BirdFarmMap.findPath(
                com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.positionX, resident.positionY), target
            )
            val next = path.firstOrNull()
                ?: com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.positionX, resident.positionY)
            val dx = next.x - resident.positionX
            val dy = next.y - resident.positionY
            val segment = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
            val travel = (24f * elapsedSeconds).coerceAtMost(segment)
            val candidate = if (resident.activity in MOVING_ACTIVITIES) {
                com.github.nacabaro.vbhelper.digifarm.map.MapPoint(
                    resident.positionX + dx / segment * travel,
                    resident.positionY + dy / segment * travel
                )
            } else {
                com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.positionX, resident.positionY)
            }
            val isReserved = reservedPositions.any { (otherId, position) ->
                otherId != resident.individualId &&
                    hypot(
                        (position.x - candidate.x).toDouble(),
                        (position.y - candidate.y).toDouble()
                    ) < MIN_RESIDENT_DISTANCE
            }
            val position = if (isReserved) {
                com.github.nacabaro.vbhelper.digifarm.map.MapPoint(resident.positionX, resident.positionY)
            } else {
                candidate
            }
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
