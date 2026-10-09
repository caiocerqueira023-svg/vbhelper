package com.github.nacabaro.vbhelper.world.ecosystem

import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.source.DigimonScanPolicy
import com.github.nacabaro.vbhelper.source.DigimonScanRepository
import kotlinx.coroutines.flow.first

/**
 * All interaction commands and conflicting World mutations use the same Room transaction
 * contract. Unique claims/SQL guards protect callers outside this class as well. No network
 * work is permitted inside these transactions; commands revalidate after external work.
 */
class WorldInteractionRepository(private val db: AppDatabase,
                                 private val scanPercentagePerDefeat: () -> Int = { DigimonScanPolicy.NORMAL_GAIN },
                                 private val now: () -> Long = System::currentTimeMillis) {
    private val dao = db.worldInteractionDao()

    suspend fun reservePlayerBattle(
        id: String, ownedCharacterId: Long, spawnId: Long, fix: WorldPlayerFix, seed: Long, tick: Long
    ): WorldInteraction = db.withTransaction {
        reconcileLocked()
        validateFix(fix)
        if (dao.getOpenInteractions().any { it.origin != InteractionOrigin.AUTONOMOUS && it.type == InteractionType.BATTLE }) {
            fail(InteractionFailure.BUSY)
        }
        val owned = db.userCharacterDao().getCharacterSync(ownedCharacterId) ?: fail(InteractionFailure.UNAVAILABLE)
        if (!owned.isActive) fail(InteractionFailure.UNAVAILABLE)
        val spawn = eligibleSpawn(spawnId)
        validateDistance(fix, spawn)
        val event = newInteraction(id, InteractionType.BATTLE, InteractionOrigin.DIRECT_PLAYER, seed, tick).copy(
            state = InteractionState.RESERVED, revision = 1,
            reservedFrom = InteractionState.ACTIVE, reservationExpiresAt = now() + WorldInteractionPolicy.RESERVATION_MILLIS
        )
        insertLocked(event, listOf(
            WorldInteractionParticipant(id, owned.individualId, InteractionRole.OWNED, InteractionSide.ALLIED,
                ownedCharacterId = owned.id, cardCharacterId = owned.charId),
            WorldInteractionParticipant(id, spawn.individualId, InteractionRole.WILD, InteractionSide.OPPOSING,
                spawnId = spawn.id, cardCharacterId = spawn.cardCharacterId)
        ))
        event
    }

    suspend fun commitPlayerBattle(id: String, expectedRevision: Long, fix: WorldPlayerFix, activeOwnedCharacterId: Long? = null): Boolean = db.withTransaction {
        reconcileLocked()
        val event = dao.getInteraction(id) ?: return@withTransaction false
        if (event.state != InteractionState.RESERVED || event.revision != expectedRevision ||
            event.origin == InteractionOrigin.AUTONOMOUS || event.type != InteractionType.BATTLE) return@withTransaction false
        validateFix(fix)
        val participants = dao.getParticipants(id)
        if(activeOwnedCharacterId!=null) {
            if(participants.none { it.role==InteractionRole.OWNED && it.ownedCharacterId==activeOwnedCharacterId } ||
                db.userCharacterDao().getCharacterSync(activeOwnedCharacterId)?.isActive!=true) fail(InteractionFailure.UNAVAILABLE)
        }
        participants.forEach { participant ->
            if (participant.role == InteractionRole.WILD) {
                requireClaimLocked(id, participant.individualId)
                val spawn = db.worldSpawnDao().getByIndividualId(participant.individualId) ?: fail(InteractionFailure.UNAVAILABLE)
                validateDistance(fix, spawn)
            } else {
                val character = participant.ownedCharacterId?.let { db.userCharacterDao().getCharacterSync(it) }
                    ?: fail(InteractionFailure.UNAVAILABLE)
                if (character.individualId != participant.individualId || character.charId != participant.cardCharacterId ||
                    (event.origin == InteractionOrigin.DIRECT_PLAYER && !character.isActive)) fail(InteractionFailure.UNAVAILABLE)
            }
        }
        transitionLocked(event, InteractionState.PLAYER_CONTROLLED)
        com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).reserveLocked(id)
        if (event.origin == InteractionOrigin.JOINED_PLAYER) event.parentInteractionId?.let { parentId ->
            dao.getInteraction(parentId)?.let { parent -> closeLocked(parent, InteractionState.CANCELLED, "PLAYER_JOINED") }
        }
        true
    }

    /** Per-request claim; a remote contact without a live spawn retains its existing 1:1 API. */
    suspend fun beginPrivateChat(id: String, individualId: String): WorldInteraction? = db.withTransaction {
        reconcileLocked()
        WorldChatMemoryRepository(db,now).adoptAvailablePlayerConversation(individualId)
        val spawn = db.worldSpawnDao().getByIndividualId(individualId) ?: return@withTransaction null
        assertUnclaimedLocked(individualId)
        if (spawn.expiresAt <= now() || spawn.recruitmentState != RecruitmentState.WILD) return@withTransaction null
        val event = newInteraction(id, InteractionType.CHAT, InteractionOrigin.DIRECT_PLAYER, 0, 0).copy(state = InteractionState.ACTIVE)
        insertLocked(event, listOf(WorldInteractionParticipant(id, individualId, InteractionRole.WILD, InteractionSide.NEUTRAL,
            spawnId = spawn.id, cardCharacterId = spawn.cardCharacterId)))
        event
    }

    suspend fun renewPrivateChat(id: String, individualId: String) = db.withTransaction {
        requirePrivateChatLocked(id,individualId)
        val event=dao.getInteraction(id)!!
        dao.updateInteraction(event.copy(expiresAt=now()+WorldInteractionPolicy.MAX_CHAT_MILLIS))
    }

    /** Only proposals are exposed to the future planner: approach/activation remains a later step. */
    suspend fun proposeNpcInteraction(id: String, type: InteractionType, spawnIds: List<Long>, seed: Long, tick: Long,
        socialDecision: SocialEncounterDecision? = null): WorldInteraction = db.withTransaction {
        reconcileLocked()
        if (db.worldEcosystemDao().getSession()?.pauseReason == WorldPauseReason.PLAYER_BATTLE) fail(InteractionFailure.BUSY)
        require(spawnIds.size == 2 && spawnIds.distinct().size == 2)
        val cap = if (type == InteractionType.CHAT) 2 else 1
        if (dao.getOpenInteractions().count { it.origin == InteractionOrigin.AUTONOMOUS && it.type == type } >= cap) fail(InteractionFailure.BUSY)
        val spawns = spawnIds.map { eligibleSpawn(it) }.sortedBy { it.individualId }
        socialDecision?.let { require(it.initiatorId in spawns.map { s -> s.individualId } &&
            it.targetId in spawns.map { s -> s.individualId } && it.initiatorId != it.targetId && it.type == type) }
        val event = newInteraction(id, type, InteractionOrigin.AUTONOMOUS, seed, tick).copy(
            publicReason = socialDecision?.description(),
            socialContextJson = socialDecision?.let { WorldSocialRepository(db).encode(it) })
        insertLocked(event, spawns.mapIndexed { index, spawn -> WorldInteractionParticipant(
            id, spawn.individualId, InteractionRole.WILD,
            if (type == InteractionType.CHAT) InteractionSide.NEUTRAL else if (index == 0) InteractionSide.ALLIED else InteractionSide.OPPOSING,
            spawnId = spawn.id, cardCharacterId = spawn.cardCharacterId
        ) })
        event
    }

    /** Proposals activate only after their actual positions meet; no renderer can teleport them. */
    suspend fun initiateWildPlayerInteraction(id: String, type: InteractionType, spawnId: Long, fix: WorldPlayerFix,
        seed: Long, tick: Long, socialDecision: SocialEncounterDecision? = null, allowApproach: Boolean = false): WorldInteraction = db.withTransaction {
        reconcileLocked()
        validateFix(fix)
        if(db.worldEcosystemDao().getSession()?.pauseReason==WorldPauseReason.PLAYER_BATTLE ||
            dao.getOpenInteractions().any { it.isPlayerDirected }) fail(InteractionFailure.BUSY)
        val cap=if(type==InteractionType.CHAT) 2 else 1
        if(dao.getOpenInteractions().count { it.origin==InteractionOrigin.AUTONOMOUS && it.type==type }>=cap) fail(InteractionFailure.BUSY)
        val spawn=eligibleSpawn(spawnId)
        val inRange = RadarWorldGeometry.relative(fix.position, com.github.nacabaro.vbhelper.world.GeoPoint(spawn.latitude, spawn.longitude)).withinInteractionRange
        if (!inRange) {
            if (!allowApproach || !WorldWildInitiationPolicy.eligible(spawn, fix, now(), true, false, false, true)) fail(InteractionFailure.UNAVAILABLE)
        }
        socialDecision?.let { require(it.initiatorId == spawn.individualId && it.targetId == "trainer" && it.type == type) }
        val event=newInteraction(id,type,InteractionOrigin.AUTONOMOUS,seed,tick).copy(
            state=if (inRange) InteractionState.ACTIVE else InteractionState.PROPOSED,
            nextActionTick=tick+if(type==InteractionType.BATTLE)4 else 1,
            deadlineTick = tick + if (!inRange) 60 else if (type == InteractionType.CHAT) WorldSocialPlanner.IGNORED_GREETING_LIFETIME_TICKS else 200,
            publicReason=(if(type==InteractionType.BATTLE) "WILD_ATTACK:" else "WILD_CHAT:") + (socialDecision?.description() ?: "Wild encounter"),
            socialContextJson = socialDecision?.let { WorldSocialRepository(db).encode(it) })
        insertLocked(event,listOf(WorldInteractionParticipant(id,spawn.individualId,InteractionRole.WILD,
            if(type==InteractionType.BATTLE)InteractionSide.OPPOSING else InteractionSide.NEUTRAL,spawnId=spawn.id,cardCharacterId=spawn.cardCharacterId)))
        event
    }

    suspend fun activateWildPlayerInteraction(id: String, expectedRevision: Long, tick: Long, fix: WorldPlayerFix): Boolean = db.withTransaction {
        val event = dao.getInteraction(id) ?: return@withTransaction false
        if (!event.isPlayerDirected || event.state != InteractionState.PROPOSED || event.revision != expectedRevision ||
            !fix.isFresh(now()) || db.worldEcosystemDao().getSession()?.pauseReason == WorldPauseReason.PLAYER_BATTLE) return@withTransaction false
        val participant = dao.getParticipants(id).singleOrNull { it.role == InteractionRole.WILD } ?: return@withTransaction false
        requireClaimLocked(id, participant.individualId)
        val spawn = db.worldSpawnDao().getByIndividualId(participant.individualId) ?: return@withTransaction false
        if (!RadarWorldGeometry.relative(fix.position, com.github.nacabaro.vbhelper.world.GeoPoint(spawn.latitude, spawn.longitude)).withinInteractionRange) return@withTransaction false
        if (event.type == InteractionType.BATTLE && db.userCharacterDao().getActiveCharacter().first() == null) return@withTransaction false
        transitionLocked(event.copy(nextActionTick = tick + if (event.type == InteractionType.BATTLE) 4 else 1,
            deadlineTick = tick + if (event.type == InteractionType.CHAT) WorldSocialPlanner.IGNORED_GREETING_LIFETIME_TICKS else 200), InteractionState.ACTIVE)
        true
    }

    /** The attack transfers into the ordinary 1v1 battle owner without an Accept prompt. */
    suspend fun reserveWildAttack(id: String, sourceId: String, expectedRevision: Long, ownedCharacterId: Long,
        fix: WorldPlayerFix, tick: Long): WorldInteraction = db.withTransaction {
        reconcileLocked()
        validateFix(fix)
        val source=dao.getInteraction(sourceId) ?: fail(InteractionFailure.UNAVAILABLE)
        if(!source.isPlayerBattle || source.origin!=InteractionOrigin.AUTONOMOUS || source.type!=InteractionType.BATTLE ||
            source.state!=InteractionState.ACTIVE || source.revision!=expectedRevision || tick<source.nextActionTick) fail(InteractionFailure.UNAVAILABLE)
        if(dao.getOpenInteractions().any { it.origin!=InteractionOrigin.AUTONOMOUS && it.type==InteractionType.BATTLE }) fail(InteractionFailure.BUSY)
        val wild=dao.getParticipants(sourceId).singleOrNull { it.role==InteractionRole.WILD } ?: fail(InteractionFailure.UNAVAILABLE)
        requireClaimLocked(sourceId,wild.individualId)
        val spawn=db.worldSpawnDao().getByIndividualId(wild.individualId) ?: fail(InteractionFailure.UNAVAILABLE)
        validateDistance(fix,spawn)
        val owned=db.userCharacterDao().getCharacterSync(ownedCharacterId) ?: fail(InteractionFailure.UNAVAILABLE)
        if(!owned.isActive) fail(InteractionFailure.UNAVAILABLE)
        val event=newInteraction(id,InteractionType.BATTLE,InteractionOrigin.JOINED_PLAYER,
            EcosystemSeed.mix(source.seed,id,"wild-attack-handoff",tick),tick).copy(state=InteractionState.RESERVED,revision=1,
            parentInteractionId=source.id,publicReason=source.publicReason,reservedFrom=InteractionState.ACTIVE,
            reservationExpiresAt=now()+WorldInteractionPolicy.RESERVATION_MILLIS)
        dao.releaseClaims(source.id)
        dao.updateInteraction(source.copy(state=InteractionState.RESERVED,reservedFrom=InteractionState.ACTIVE,
            reservationExpiresAt=event.reservationExpiresAt,revision=source.revision+1))
        db.worldChatMemoryDao().getContext(source.id)?.let { db.worldChatMemoryDao().saveContext(it.copy(interactionId=id)) }
        insertLocked(event,listOf(WorldInteractionParticipant(id,owned.individualId,InteractionRole.OWNED,InteractionSide.ALLIED,
            ownedCharacterId=owned.id,cardCharacterId=owned.charId),wild.copy(interactionId=id,side=InteractionSide.OPPOSING)))
        event
    }

    suspend fun activateNpcInteraction(id: String, expectedRevision: Long, tick: Long): Boolean = db.withTransaction {
        reconcileLocked()
        if (db.worldEcosystemDao().getSession()?.pauseReason == WorldPauseReason.PLAYER_BATTLE) return@withTransaction false
        val event = dao.getInteraction(id) ?: return@withTransaction false
        if (event.state != InteractionState.PROPOSED || event.origin != InteractionOrigin.AUTONOMOUS ||
            event.revision != expectedRevision || tick < event.startTick) return@withTransaction false
        val participants = dao.getParticipants(id)
        if (participants.size != 2 || participants.any { it.role != InteractionRole.WILD }) return@withTransaction false
        val positions = participants.map { participant ->
            requireClaimLocked(id, participant.individualId)
            val spawn = db.worldSpawnDao().getByIndividualId(participant.individualId) ?: fail(InteractionFailure.UNAVAILABLE)
            val position = com.github.nacabaro.vbhelper.world.GeoPoint.fromOrNull(spawn.latitude, spawn.longitude) ?: fail(InteractionFailure.UNAVAILABLE)
            val home = com.github.nacabaro.vbhelper.world.GeoPoint.fromOrNull(spawn.homeLatitude, spawn.homeLongitude) ?: fail(InteractionFailure.UNAVAILABLE)
            if (!RadarWorldGeometry.relative(home, position).withinRadius(spawn.anchorRadiusMeters)) fail(InteractionFailure.UNAVAILABLE)
            position
        }
        if (!RadarWorldGeometry.relative(positions[0], positions[1]).withinRadius(3.0)) return@withTransaction false
        transitionLocked(event.copy(nextActionTick = tick), InteractionState.ACTIVE)
        true
    }

    /** Apply one terminal result, including all participant effects, or nothing at all. */
    suspend fun completeBattle(id: String, outcome: BattleOutcome): Boolean = db.withTransaction {
        val recorded = db.questDao().battleReport(id)
        if (recorded == null) reconcileLocked() else require(recorded.outcome == outcome) { "The terminal battle outcome is already recorded." }
        val event = dao.getInteraction(id) ?: return@withTransaction false
        if (dao.getResult(id) != null || event.type != InteractionType.BATTLE ||
            event.state !in listOf(InteractionState.ACTIVE, InteractionState.PLAYER_CONTROLLED)) return@withTransaction false
        if (event.origin != InteractionOrigin.AUTONOMOUS && event.state != InteractionState.PLAYER_CONTROLLED) return@withTransaction false
        val participants = dao.getParticipants(id)
        participants.filter { it.role == InteractionRole.WILD }.forEach { requireClaimLocked(id, it.individualId) }
        val timestamp = now()
        val context=db.worldChatMemoryDao().getContext(id)
        val result = WorldInteractionResult(id, outcome, timestamp)
        dao.insertResult(result)
        val resolving = transitionLocked(event, InteractionState.RESOLVING)
        val friendly = context?.friendly == true || event.isFriendlyBattle
        val effects = WorldInteractionPolicy.battleEffects(event.origin, outcome, friendly)
        WorldChatMemoryRepository(db,now).recordBattle(event,participants,outcome,context)
        if (effects.ownedWon != null) {
            participants.filter { it.role == InteractionRole.OWNED }.forEach { participant ->
                val characterId = participant.ownedCharacterId ?: fail(InteractionFailure.UNAVAILABLE)
                val character = db.userCharacterDao().getCharacterSync(characterId) ?: fail(InteractionFailure.UNAVAILABLE)
                if (character.individualId != participant.individualId) fail(InteractionFailure.UNAVAILABLE)
                check(db.userCharacterDao().recordBattleResult(characterId, effects.ownedWon) == 1)
            }
        }
        DigimonScanRepository(db, now).awardBattleLocked(id,
            DigimonScanPolicy.awards(event.origin, outcome, friendly, participants,
                percentagePerDefeat = scanPercentagePerDefeat()), timestamp)
        com.github.nacabaro.vbhelper.quests.QuestProgress(db).battleLocked(event, participants, outcome, timestamp)
        dao.releaseClaims(id)
        applySocialResultLocked(event, participants, outcome)
        if (effects.removeOpposingWilds) {
            participants.filter { it.role == InteractionRole.WILD && it.side == InteractionSide.OPPOSING }.forEach { participant ->
                val spawnId = participant.spawnId ?: fail(InteractionFailure.UNAVAILABLE)
                if (db.questDao().unfinishedTargetCount(participant.individualId) == 0) {
                    check(db.worldSpawnDao().deleteById(spawnId) == 1)
                }
            }
        }
        transitionLocked(resolving, InteractionState.ENDED)
        check(dao.updateResult(result.copy(appliedAt = timestamp)) == 1)
        com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).settleLocked(id)
        true
    }

    suspend fun cancel(id: String): Boolean = db.withTransaction {
        val event = dao.getInteraction(id) ?: return@withTransaction false
        if (event.state.terminal || dao.getResult(id) != null) return@withTransaction false
        if(event.type==InteractionType.BATTLE && event.state==InteractionState.PLAYER_CONTROLLED && db.worldChatMemoryDao().getContext(id)?.chatIndividualId!=null)
            return@withTransaction completeBattle(id,BattleOutcome.ABANDONED)
        if (event.origin == InteractionOrigin.JOINED_PLAYER && event.state == InteractionState.RESERVED) restoreParentLocked(event)
        com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).settleLocked(id)
        closeLocked(event, if (event.state == InteractionState.RESOLVING) InteractionState.INTERRUPTED else InteractionState.CANCELLED, "CANCELLED")
        true
    }

    suspend fun finishPrivateChat(id: String, individualId: String) = db.withTransaction {
        reconcileLocked()
        val event = dao.getInteraction(id) ?: return@withTransaction
        if (event.state.terminal) return@withTransaction
        requirePrivateChatLocked(id, individualId)
        transitionLocked(transitionLocked(event, InteractionState.RESOLVING), InteractionState.ENDED)
        dao.releaseClaims(id)
    }

    /** Called inside a caller's transaction before player-specific mood/recruit/remove effects. */
    internal suspend fun requirePrivateChatLocked(id: String, individualId: String) {
        val event = dao.getInteraction(id) ?: fail(InteractionFailure.UNAVAILABLE)
        if (event.origin != InteractionOrigin.DIRECT_PLAYER || event.type != InteractionType.CHAT ||
            event.state != InteractionState.ACTIVE || event.expiresAt <= now()) fail(InteractionFailure.UNAVAILABLE)
        requireClaimLocked(id, individualId)
    }

    internal suspend fun assertUnclaimedLocked(individualId: String) {
        if (dao.getClaim(individualId) != null) fail(InteractionFailure.BUSY)
    }

    suspend fun reconcile() = db.withTransaction { reconcileLocked() }

    internal suspend fun reconcileLocked() {
        val timestamp = now()
        val checkpoint = db.worldEcosystemDao().getSession()
        dao.getOpenInteractions().forEach { event ->
            if (event.rulesVersion != WorldEcosystemClock.RULES_VERSION) {
                closeLocked(event, InteractionState.CANCELLED, "UNSUPPORTED_RULES")
            } else if (event.expiresAt <= timestamp && event.origin != InteractionOrigin.AUTONOMOUS) {
                if (event.origin == InteractionOrigin.JOINED_PLAYER && event.state == InteractionState.RESERVED) restoreParentLocked(event)
                closeLocked(event, if (event.state == InteractionState.PLAYER_CONTROLLED || event.state == InteractionState.RESOLVING)
                    InteractionState.INTERRUPTED else InteractionState.CANCELLED, "EXPIRED")
            } else if (event.origin == InteractionOrigin.AUTONOMOUS && checkpoint?.pauseReason != WorldPauseReason.PLAYER_BATTLE &&
                event.deadlineTick != null && (checkpoint?.tickIndex ?: 0) >= event.deadlineTick) {
                closeLocked(event, InteractionState.CANCELLED, "LOGICAL_EXPIRY")
            } else if (event.state == InteractionState.RESERVED && (event.reservationExpiresAt ?: 0) <= timestamp && event.origin != InteractionOrigin.AUTONOMOUS) {
                if (event.origin == InteractionOrigin.JOINED_PLAYER) restoreParentLocked(event)
                closeLocked(event, InteractionState.CANCELLED, "RESERVATION_EXPIRED")
            }
        }
        dao.pruneEndedInteractions(timestamp - 7 * 24 * 60 * 60 * 1_000L)
    }

    suspend fun recoverInterruptedPlayerBattles() = db.withTransaction {
        dao.getOpenInteractions().filter { it.origin != InteractionOrigin.AUTONOMOUS }.forEach { event ->
            val recorded = db.questDao().battleReport(event.id)
            if (recorded != null && event.state == InteractionState.PLAYER_CONTROLLED && event.type == InteractionType.BATTLE) {
                val participants = dao.getParticipants(event.id)
                val ownedValid = participants.filter { it.role == InteractionRole.OWNED }.all { participant ->
                    participant.ownedCharacterId?.let { db.userCharacterDao().getCharacterSync(it) }?.let { character ->
                        character.individualId == participant.individualId && character.charId == participant.cardCharacterId
                    } == true
                }
                if (ownedValid && completeBattle(event.id, recorded.outcome)) return@forEach
            }
            if (event.origin == InteractionOrigin.JOINED_PLAYER && event.state == InteractionState.RESERVED) restoreParentLocked(event)
            com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).settleLocked(event.id, interrupted = true)
            closeLocked(event, if (event.state == InteractionState.PLAYER_CONTROLLED || event.state == InteractionState.RESOLVING)
                InteractionState.INTERRUPTED else InteractionState.CANCELLED, "PROCESS_INTERRUPTED")
        }
    }

    private fun newInteraction(id: String, type: InteractionType, origin: InteractionOrigin, seed: Long, tick: Long): WorldInteraction {
        require(id.isNotBlank() && tick >= 0)
        val timestamp = now()
        return WorldInteraction(id, type, origin, InteractionState.PROPOSED, seed,
            startTick = tick, nextActionTick = tick, createdAt = timestamp,
            expiresAt = timestamp + if (type == InteractionType.BATTLE && origin != InteractionOrigin.AUTONOMOUS)
                WorldInteractionPolicy.MAX_BATTLE_MILLIS else WorldInteractionPolicy.MAX_CHAT_MILLIS,
            deadlineTick = if (origin == InteractionOrigin.AUTONOMOUS) tick + 200 else null)
    }

    private suspend fun eligibleSpawn(id: Long): WorldSpawn {
        val details = db.worldSpawnDao().getSpawnById(id) ?: fail(InteractionFailure.UNAVAILABLE)
        val spawn = db.worldSpawnDao().getByIndividualId(details.individualId) ?: fail(InteractionFailure.UNAVAILABLE)
        if (spawn.expiresAt <= now() || spawn.recruitmentState != RecruitmentState.WILD) fail(InteractionFailure.UNAVAILABLE)
        assertUnclaimedLocked(spawn.individualId)
        return spawn
    }

    internal suspend fun insertLocked(event: WorldInteraction, participants: List<WorldInteractionParticipant>) {
        if (dao.getInteraction(event.id) != null) fail(InteractionFailure.UNAVAILABLE)
        require(participants.map { it.individualId }.distinct().size == participants.size)
        participants.filter { it.role == InteractionRole.WILD }.forEach { assertUnclaimedLocked(it.individualId) }
        dao.insertInteraction(event)
        dao.insertParticipants(participants)
        dao.insertClaims(participants.filter { it.role == InteractionRole.WILD }.map {
            WorldParticipationClaim(it.individualId, event.id, requireNotNull(it.spawnId))
        })
    }

    private suspend fun requireClaimLocked(id: String, individualId: String) {
        if (dao.getClaim(individualId)?.interactionId != id) fail(InteractionFailure.UNAVAILABLE)
    }

    private suspend fun transitionLocked(event: WorldInteraction, next: InteractionState): WorldInteraction {
        check(WorldInteractionPolicy.canTransition(event.state, next))
        val updated = event.copy(state = next, revision = event.revision + 1,
            reservationExpiresAt = null, reservedFrom = null, endedAt = if (next.terminal) now() else null)
        check(dao.updateInteraction(updated) == 1)
        return updated
    }

    private suspend fun closeLocked(event: WorldInteraction, state: InteractionState, reason: String) {
        check(state.terminal)
        com.github.nacabaro.vbhelper.quests.QuestBattleInventory(db).settleLocked(event.id,
            interrupted = event.state == InteractionState.PLAYER_CONTROLLED)
        check(dao.updateInteraction(event.copy(state = state, revision = event.revision + 1,
            reservationExpiresAt = null, reservedFrom = null, endedAt = now(), terminalReason = reason)) == 1)
        dao.releaseClaims(event.id)
    }

    private fun validateFix(fix: WorldPlayerFix) { if (!fix.isFresh(now())) fail(InteractionFailure.STALE_LOCATION) }
    private fun validateDistance(fix: WorldPlayerFix, spawn: WorldSpawn) {
        if (com.github.nacabaro.vbhelper.world.RadarDebugInteraction.allowAnyDistance) return
        val position = com.github.nacabaro.vbhelper.world.GeoPoint.fromOrNull(spawn.latitude, spawn.longitude) ?: fail(InteractionFailure.UNAVAILABLE)
        if (!RadarWorldGeometry.relative(fix.position, position).withinInteractionRange) fail(InteractionFailure.UNAVAILABLE)
    }
    private fun fail(reason: InteractionFailure): Nothing = throw WorldInteractionException(reason)

    internal suspend fun endConversationLocked(event: WorldInteraction, reason: String) {
        closeLocked(event, InteractionState.ENDED, reason)
    }

    internal suspend fun transferNpcBattleLocked(source: WorldInteraction, reason: String, tick: Long): WorldInteraction? {
        if(source.type!=InteractionType.CHAT || source.state !in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) return null
        if (dao.getOpenInteractions().any { it.type == InteractionType.BATTLE && it.origin == InteractionOrigin.AUTONOMOUS && !it.state.terminal }) return null
        val participants = dao.getParticipants(source.id).filter { it.role == InteractionRole.WILD }.sortedBy { it.individualId }
        if (participants.size != 2) return null
        val id = "battle:${source.id}:$tick"
        dao.releaseClaims(source.id)
        endConversationLocked(source, "CHALLENGE_ACCEPTED")
        val battle = newInteraction(id, InteractionType.BATTLE, InteractionOrigin.AUTONOMOUS,
            EcosystemSeed.mix(source.seed, id, "context-battle", tick), tick).copy(parentInteractionId = source.id, publicReason = reason,
                socialContextJson = source.socialContextJson)
        insertLocked(battle, participants.mapIndexed { index, p -> p.copy(interactionId = id,
            side = if (index == 0) InteractionSide.ALLIED else InteractionSide.OPPOSING) })
        return battle
    }

    internal suspend fun transferWildAttackLocked(source: WorldInteraction, initiatorId: String, reason: String, tick: Long, sparring: Boolean = false): WorldInteraction? {
        if(source.type!=InteractionType.CHAT || source.state !in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) return null
        if (db.userCharacterDao().getActiveCharacter().first() == null) return null
        if(dao.getOpenInteractions().any { it.type==InteractionType.BATTLE && it.origin==InteractionOrigin.AUTONOMOUS }) return null
        val attacker=dao.getParticipants(source.id).singleOrNull { it.individualId==initiatorId && it.role==InteractionRole.WILD } ?: return null
        requireClaimLocked(source.id,initiatorId)
        val id="attack:${source.id}:$tick"
        dao.releaseClaims(source.id)
        endConversationLocked(source,"WILD_ATTACK")
        val attack=newInteraction(id,InteractionType.BATTLE,InteractionOrigin.AUTONOMOUS,
            EcosystemSeed.mix(source.seed,id,"hostile-player-attack",tick),tick).copy(state=InteractionState.ACTIVE,nextActionTick=tick+4,
            parentInteractionId=source.id,publicReason=(if(sparring) "WILD_SPARRING:" else "WILD_ATTACK:")+reason,
            socialContextJson = source.socialContextJson)
        insertLocked(attack,listOf(attacker.copy(interactionId=id,side=InteractionSide.OPPOSING)))
        return attack
    }

    suspend fun reserveJoinedBattle(id: String, sourceId: String, expectedRevision: Long, activeOwned: Long,
        secondOwned: Long?, alliedWild: String?, fix: WorldPlayerFix, tick: Long): WorldInteraction = db.withTransaction {
        reconcileLocked(); validateFix(fix)
        if (dao.getOpenInteractions().any { it.origin != InteractionOrigin.AUTONOMOUS && it.type == InteractionType.BATTLE }) fail(InteractionFailure.BUSY)
        val source = dao.getInteraction(sourceId) ?: fail(InteractionFailure.UNAVAILABLE)
        if (source.type != InteractionType.BATTLE || source.origin != InteractionOrigin.AUTONOMOUS ||
            source.state != InteractionState.ACTIVE || source.revision != expectedRevision) fail(InteractionFailure.UNAVAILABLE)
        val wilds = dao.getParticipants(sourceId).filter { it.role == InteractionRole.WILD }
        require(wilds.size == 2)
        wilds.forEach { p -> requireClaimLocked(sourceId, p.individualId)
            validateDistance(fix, db.worldSpawnDao().getByIndividualId(p.individualId) ?: fail(InteractionFailure.UNAVAILABLE)) }
        val npc = dao.getNpcBattle(sourceId) ?: fail(InteractionFailure.UNAVAILABLE)
        val snapshot = NpcBattleAdapter.recover(source,npc).snapshot()
        if ((snapshot.alliedMembers + snapshot.opposingMembers).any { it.health <= 0 } || snapshot.result != null) fail(InteractionFailure.UNAVAILABLE)
        val ownedIds = if (alliedWild != null) listOf(activeOwned) else listOf(activeOwned, secondOwned ?: fail(InteractionFailure.UNAVAILABLE))
        require(ownedIds.distinct().size == ownedIds.size && (alliedWild == null || alliedWild in wilds.map { it.individualId }))
        val owned = ownedIds.map { db.userCharacterDao().getCharacterSync(it) ?: fail(InteractionFailure.UNAVAILABLE) }
        if (!owned.first().isActive) fail(InteractionFailure.UNAVAILABLE)
        val event = newInteraction(id, InteractionType.BATTLE, InteractionOrigin.JOINED_PLAYER,
            EcosystemSeed.mix(source.seed, id, "team-formation", tick), tick).copy(state = InteractionState.RESERVED,
            revision = 1, parentInteractionId = sourceId, reservedFrom = InteractionState.ACTIVE,
            reservationExpiresAt = now() + WorldInteractionPolicy.RESERVATION_MILLIS, publicReason = source.publicReason)
        dao.releaseClaims(sourceId)
        dao.updateInteraction(source.copy(state = InteractionState.RESERVED, reservedFrom = InteractionState.ACTIVE,
            reservationExpiresAt = event.reservationExpiresAt, revision = source.revision + 1))
        insertLocked(event, owned.map { p -> WorldInteractionParticipant(id, p.individualId, InteractionRole.OWNED, InteractionSide.ALLIED,
            ownedCharacterId = p.id, cardCharacterId = p.charId) } + wilds.map { p -> p.copy(interactionId = id,
            side = if (p.individualId == alliedWild) InteractionSide.ALLIED else InteractionSide.OPPOSING) })
        event
    }

    private suspend fun restoreParentLocked(event: WorldInteraction) {
        val parent = event.parentInteractionId?.let { dao.getInteraction(it) } ?: return
        if (parent.state != InteractionState.RESERVED || parent.origin != InteractionOrigin.AUTONOMOUS) return
        dao.releaseClaims(event.id)
        dao.updateInteraction(parent.copy(state = InteractionState.ACTIVE, reservationExpiresAt = null, reservedFrom = null, revision = parent.revision + 1))
        dao.insertClaims(dao.getParticipants(parent.id).filter { it.role == InteractionRole.WILD }.map { WorldParticipationClaim(it.individualId, parent.id, it.spawnId!!) })
    }

    private suspend fun applySocialResultLocked(event: WorldInteraction, participants: List<WorldInteractionParticipant>, outcome: BattleOutcome) {
        val wilds = participants.filter { it.role == InteractionRole.WILD }.sortedBy { it.individualId }
        val tick = db.worldEcosystemDao().getSession()?.tickIndex ?: event.nextActionTick
        val friendly = event.isFriendlyBattle || db.worldChatMemoryDao().getContext(event.id)?.friendly==true
        if (wilds.size == 2) {
            val pair = WildPairBond.create(wilds[0].individualId, wilds[1].individualId)
            val old = db.worldEcosystemDao().getBond(pair.individualA, pair.individualB) ?: pair
            val aAllied = wilds[0].side == InteractionSide.ALLIED
            val aWon = (outcome == BattleOutcome.ALLIED_VICTORY && aAllied) || (outcome == BattleOutcome.OPPOSING_VICTORY && !aAllied)
            val opposed = wilds[0].side != wilds[1].side
            val decisive = (outcome == BattleOutcome.ALLIED_VICTORY || outcome == BattleOutcome.OPPOSING_VICTORY) && opposed
            val diminishing = (5 / (1 + old.aWins + old.bWins)).coerceAtLeast(1)
            db.worldEcosystemDao().saveBond(old.copy(affinity = (old.affinity + if (friendly || !opposed) 2 else -diminishing).coerceIn(-100,100),
                aWins = old.aWins + if (decisive && aWon) 1 else 0,
                bWins = old.bWins + if (decisive && !aWon) 1 else 0, draws = old.draws + if (outcome == BattleOutcome.DRAW) 1 else 0,
                lastInteractionAt = now(), lastInteractionTick = tick, cooldownUntilTick = tick + 80))
        }
        wilds.forEach { p -> db.worldSpawnDao().getByIndividualId(p.individualId)?.let { spawn ->
            val profile = WorldSocialRepository(db).profile(p.individualId)
            val won = outcome == if (p.side == InteractionSide.ALLIED) BattleOutcome.ALLIED_VICTORY else BattleOutcome.OPPOSING_VICTORY
            val emotionDelta = when {
                outcome == BattleOutcome.DRAW -> 0
                outcome == BattleOutcome.ABANDONED -> -1
                won -> (1 + profile.challenge * 3).toInt()
                friendly && profile.training > .7 -> 1
                else -> -(1 + profile.boundarySensitivity * 2).toInt()
            }
            db.worldSpawnDao().adjustEcosystemEmotion(p.individualId, emotionDelta)
            val other = wilds.firstOrNull { it.individualId != p.individualId }
            val partnerId = other?.individualId ?: "trainer"
            val name = if (other == null) "Tamer" else other.spawnId?.let { db.worldSpawnDao().getSpawnById(it)?.speciesName } ?: "Digimon"
            val perspective = when { outcome == BattleOutcome.DRAW -> "DRAW"; outcome == BattleOutcome.ABANDONED -> "ABANDONED"; won -> "WON"; else -> "LOST" }
            val cooperation = other != null && other.side == p.side
            db.worldSocialDao().saveMemory(WorldSocialMemory(p.individualId,event.id,partnerId,name,
                if (friendly) SocialMotive.CHALLENGE_SPARRING.name else SocialMotive.HOSTILE_ATTACK.name,
                perspective,"Public battle ${if (cooperation) "alongside" else "against"} $name: $perspective; friendly=$friendly.","",tick,now()))
            db.worldSocialDao().prune(p.individualId)
            db.worldSpawnDao().checkpointMovement(p.individualId, spawn.latitude, spawn.longitude, spawn.homeLatitude, spawn.homeLongitude,
                com.github.nacabaro.vbhelper.domain.world.WorldMovementState.RETURNING, tick + 1, tick + 80)
        } }
    }
}
