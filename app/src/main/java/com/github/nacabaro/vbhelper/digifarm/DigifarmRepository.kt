package com.github.nacabaro.vbhelper.digifarm

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.digifarm.map.DigifarmGround
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
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
import kotlin.random.Random
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.chat.ChatRepository
import com.github.nacabaro.vbhelper.digifarm.social.FarmConversationOrchestrator

class DigifarmRepository(private val db: AppDatabase) {
    private val dao = db.digifarmDao()
    private var conversations: FarmConversationOrchestrator? = null

    @Synchronized
    fun conversationOwner(chat: ChatRepository): FarmConversationOrchestrator = conversations ?:
        FarmConversationOrchestrator(this, chat).also { conversations = it }

    suspend fun socialProfile(id: String) = DigimonSocialProfile.forIndividual(id,
        db.digimonIndividualDao().getPersonality(id)?.personalityType ?: DigimonPersonalityType.FRIENDLY)
    suspend fun residentStates(farmId: String) = dao.getResidentEntities(farmId)
    suspend fun relationships(id: String) = dao.relationships(id)

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
        if (db.digimonIndividualDao().getPersonality(character.individualId) == null) {
            com.github.nacabaro.vbhelper.source.StorageRepository(db).getOrCreatePersonality(characterId)
        }
        val slot = DigifarmGround.safeSpawns[dao.residentCount(farmId) % DigifarmGround.safeSpawns.size]
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
        val slot = DigifarmGround.safeSpawns[dao.residentCount(targetFarmId) % DigifarmGround.safeSpawns.size]
        val now = System.currentTimeMillis()
        dao.updateResident(
            resident.copy(
                farmId = targetFarmId,
                positionX = slot.x,
                positionY = slot.y,
                targetX = slot.x,
                targetY = slot.y,
                activity = "EXPLORE",
                socialTargetId = null,
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

    suspend fun setActivity(individualId: String, farmId: String, activity: String) = db.withTransaction {
        require(activity in setOf("PLAY", "TRAIN", "EAT", "REST"))
        val resident = dao.getResident(individualId)?.takeIf { it.farmId == farmId } ?: return@withTransaction
        val now = System.currentTimeMillis()
        dao.updateResident(resident.copy(
            activity = activity,
            activityStartedAt = now,
            targetX = resident.positionX,
            targetY = resident.positionY,
            updatedAt = now,
            socialTargetId = null,
        ))
    }

    /** Both NPCs have chosen this activity locally; physical availability is revalidated at commit. */
    suspend fun beginSharedActivity(farmId: String, firstId: String, secondId: String, activity: String): Boolean = db.withTransaction {
        require(activity in setOf("PLAY", "TRAIN") && firstId != secondId)
        val first = dao.getResident(firstId)?.takeIf { it.farmId == farmId } ?: return@withTransaction false
        val second = dao.getResident(secondId)?.takeIf { it.farmId == farmId } ?: return@withTransaction false
        val now = System.currentTimeMillis()
        if (!FarmBehaviorPolicy.available(first,socialProfile(firstId),now) ||
            !FarmBehaviorPolicy.available(second,socialProfile(secondId),now) ||
            kotlin.math.hypot((first.positionX-second.positionX).toDouble(),(first.positionY-second.positionY).toDouble()) > 220) return@withTransaction false
        listOf(first,second).forEach { resident -> dao.updateResident(resident.copy(activity = activity,
            activityStartedAt = now, targetX = resident.positionX, targetY = resident.positionY,
            socialTargetId = null, updatedAt = now)) }
        true
    }

    /** Nudges a resident toward a point (approach before talking, §8). */
    suspend fun steerToward(farmId: String, individualId: String, x: Float, y: Float) {
        val resident = dao.getResidentEntities(farmId).firstOrNull { it.individualId == individualId } ?: return
        if (!FarmBehaviorPolicy.available(resident, socialProfile(individualId), System.currentTimeMillis())) return
        val safe = DigifarmGround.clamp(MapPoint(x, y))
        dao.updateResident(
            resident.copy(
                targetX = safe.x,
                targetY = safe.y,
                activity = "SOCIALIZE",
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

    suspend fun simulateStep(farmId: String, now: Long = System.currentTimeMillis()) = db.withTransaction {
        val farm = dao.getFarm(farmId) ?: return@withTransaction
        val residents = dao.getResidentEntities(farmId)
        val reservedPositions = residents.associate { resident ->
            resident.individualId to DigifarmGround.clamp(MapPoint(resident.positionX, resident.positionY))
        }.toMutableMap()
        residents.forEachIndexed { index, resident ->
            val profile = socialProfile(resident.individualId)
            val needs = FarmBehaviorPolicy.advanceNeeds(resident, now)
            // Offline state is accounted for below; position only advances by
            // one visible step so reopening the farm cannot teleport a resident.
            val elapsedSeconds = ((now - resident.updatedAt).coerceAtLeast(0) / 1000f).coerceAtMost(1.2f)
            val current = DigifarmGround.clamp(MapPoint(resident.positionX, resident.positionY))
            var nextActivity = FarmBehaviorPolicy.activity(needs, profile, now, farm.randomSeed)
            val relationships = if (nextActivity == "SOCIALIZE") dao.relationships(resident.individualId).associateBy { it.otherId } else emptyMap()
            val socialPartner = if (nextActivity == "SOCIALIZE") {
                residents.firstOrNull { it.individualId == resident.socialTargetId &&
                    it.activity !in listOf("REST", "EAT") && it.energy >= 25 && it.satiety >= 25 }
                    ?: FarmBehaviorPolicy.partner(resident,residents,profile,relationships,farm.randomSeed,now)
            } else null
            if (nextActivity == "SOCIALIZE" && socialPartner == null) nextActivity = "EXPLORE"
            val moving = nextActivity in DigifarmGround.movingActivities
            var target = if (moving) DigifarmGround.clamp(MapPoint(resident.targetX, resident.targetY))
                else current
            val distance = DigifarmGround.worldDistance(current, target)
            if (socialPartner != null) {
                val offset = if (resident.individualId < socialPartner.individualId) -140f else 140f
                target = DigifarmGround.clamp(MapPoint(socialPartner.positionX + offset, socialPartner.positionY))
            } else if (moving && (nextActivity != resident.activity || distance < 0.018f)) {
                val choice = Random(farm.randomSeed xor now / 10_000L xor index.toLong())
                val spots = DigifarmGround.activityPoints[nextActivity]
                val raw = if (spots != null && choice.nextBoolean()) {
                    spots[choice.nextInt(spots.size)]
                } else {
                    DigifarmGround.randomPoint(choice)
                }
                target = DigifarmGround.clamp(raw)
            }
            val position = if (moving) {
                DigifarmGround.advanceAvoidingResidents(
                    current = current,
                    target = target,
                    elapsedSeconds = elapsedSeconds,
                    obstacles = reservedPositions.filterKeys { it != resident.individualId }.values,
                    preferClockwise = resident.individualId.hashCode() and 1 == 0,
                )
            } else {
                current
            }
            reservedPositions[resident.individualId] = position
            dao.updateResident(
                resident.copy(
                    positionX = position.x,
                    positionY = position.y,
                    targetX = target.x,
                    targetY = target.y,
                    facingLeft = if (position.x != current.x) position.x < current.x else resident.facingLeft,
                    activity = nextActivity,
                    energy = needs.energy,
                    satiety = needs.satiety,
                    social = needs.social,
                    funLevel = needs.funLevel,
                    socialTargetId = socialPartner?.individualId,
                    activityStartedAt = if (nextActivity != resident.activity) now else resident.activityStartedAt,
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
        sessionId: String? = null,
        publicationAllowed: (() -> Boolean)? = null
    ): FarmMessage = db.withTransaction {
        if (publicationAllowed?.invoke() == false) throw kotlinx.coroutines.CancellationException("Conversation surface is no longer active")
        val clean = body.trim().take(600)
        require(clean.isNotEmpty())
        val farm = dao.getFarm(farmId) ?: error("Digifarm not found")
        check(farm.archivedAt == null) { "This farm is archived." }
        if (authorIndividualId != null) check(dao.getResident(authorIndividualId)?.farmId == farmId) { "The speaker is no longer in this farm." }
        val residents = dao.getResidentEntities(farmId).map { it.individualId }.toSet()
        require(recipientIds.all { it in residents }) { "A recipient is no longer in this farm." }
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
        if (publicationAllowed?.invoke() == false) throw kotlinx.coroutines.CancellationException("Conversation surface left before publication")
        message
    }

    suspend fun markRead(farmId: String, sequence: Long) = dao.upsertReadState(FarmReadState(farmId, sequence))

    suspend fun recordInteraction(firstId: String, secondId: String, firstDelta: Int = 1, secondDelta: Int = 1) = db.withTransaction {
        if (firstId == secondId) return@withTransaction
        val now = System.currentTimeMillis()
        dao.applySocialInteraction(firstId, secondId, firstDelta.coerceIn(-3, 3), now)
        dao.applySocialInteraction(secondId, firstId, secondDelta.coerceIn(-3, 3), now)
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

    suspend fun memories(individualId: String, otherId: String? = null) =
        if (otherId == null) dao.getMemories(individualId) else dao.memoriesWith(individualId, otherId)

    private companion object {
        const val STATE_TICK_MILLIS = 30_000L
        const val MAX_OFFLINE_STATE_TICKS = 960L
    }
}
