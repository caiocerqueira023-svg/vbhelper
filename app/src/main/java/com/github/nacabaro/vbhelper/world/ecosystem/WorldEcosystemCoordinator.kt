package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import kotlinx.coroutines.yield

/**
 * Application-scoped, single clock owner. Idempotent surface leases share one loop and seed.
 * Logical movement and event reducers use identical live/replay steps. Network dialogue is
 * submitted separately and never runs during replay or while no resumed lease exists.
 */
class WorldEcosystemCoordinator(
    private val store: WorldEcosystemStore,
    private val externalScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = System::currentTimeMillis,
    private val newSeed: () -> Long = { Random.nextLong() }
) {
    private val mutex = Mutex()
    private val leases = mutableSetOf<String>()
    private val autonomousLeases = mutableSetOf<String>()
    private var loop: Job? = null
    private var session: WorldEcosystemSession? = null
    private var individuals = emptyList<EcosystemIndividual>()
    private var interactions = emptyList<EcosystemInteractionSummary>()
    private var claimedIndividuals = emptySet<String>()
    private var playerFix: WorldPlayerFix? = null
    private var leaseEpoch = 0L
    private var lastPersistedAt = 0L
    private var actors = emptyList<WorldSpawn>()
    private var personalities = emptyMap<String, DigimonPersonalityType>()
    private var previousPositions = emptyMap<String, GeoPoint>()
    private var motionFrameNanos = 0L
    private val _snapshot = MutableStateFlow(EcosystemSnapshot())
    val snapshot = _snapshot.asStateFlow()

    suspend fun acquire(owner: String, autonomous: Boolean = true) = mutex.withLock {
        require(owner.isNotBlank())
        val first = leases.isEmpty()
        leases.add(owner)
        if(autonomous) autonomousLeases.add(owner) else autonomousLeases.remove(owner)
        if (first) leaseEpoch++
        guarded {
            if (!loadLocked()) return@guarded
            if (first || _snapshot.value.status == EcosystemStatus.FAILED) {
                publishLocked(EcosystemStatus.LOADING)
                checkpointLocked {
                    refreshPopulationLocked(session!!.lastCheckpointAt)
                    advanceLocked(replay = true)
                    refreshPopulationLocked()
                    session = session!!.copy(revision = session!!.revision + 1)
                    persistLocked()
                }
            }
            publishLocked()
            startLoopLocked()
        }
    }

    suspend fun release(owner: String) = mutex.withLock {
        if (!leases.remove(owner)) return@withLock
        autonomousLeases.remove(owner)
        if(leases.isNotEmpty()) {
            store.setRadarAutonomousEnabled(autonomousLeases.isNotEmpty() && _snapshot.value.status in listOf(EcosystemStatus.READY,EcosystemStatus.UPDATING))
            return@withLock
        }
        leaseEpoch++
        playerFix = null
        loop?.cancel()
        loop = null
        guarded {
            if (!loadLocked()) return@guarded
            checkpointLocked {
                advanceLocked()
                if (session!!.pauseReason != WorldPauseReason.PLAYER_BATTLE) {
                    session = session!!.copy(pauseReason = WorldPauseReason.RADAR_HIDDEN)
                }
                persistLocked()
            }
            publishLocked()
        }
    }

    /** The existing Radar battle owner is the only caller; true persists before handoff. */
    suspend fun setBattlePaused(paused: Boolean): Boolean = mutex.withLock {
        var committed = false
        guarded {
            if (!loadLocked()) return@guarded
            val wasPaused = session!!.pauseReason == WorldPauseReason.PLAYER_BATTLE
            if (paused == wasPaused && _snapshot.value.status != EcosystemStatus.FAILED) {
                committed = true
                return@guarded
            }
            checkpointLocked {
                advanceLocked() // Consume pre-freeze time, or exclude the entire saved freeze.
                session = session!!.copy(
                    pauseReason = if (paused) WorldPauseReason.PLAYER_BATTLE
                        else if (leases.isEmpty()) WorldPauseReason.RADAR_HIDDEN else null,
                    revision = session!!.revision + 1
                )
                persistLocked()
            }
            publishLocked()
            if (paused) {
                loop?.cancel()
                loop = null
            } else startLoopLocked()
            committed = true
        }
        committed
    }

    /** Called after existing spawn/recruit/result mutations until their command guard is added. */
    suspend fun refreshPopulation() = mutex.withLock {
        guarded {
            if (!loadLocked() || _snapshot.value.status == EcosystemStatus.FAILED) return@guarded
            checkpointLocked { refreshPopulationLocked() }
            publishLocked()
        }
    }

    /** A new resumed lease must obtain its own fix; delayed callbacks carry their original epoch. */
    suspend fun acceptPlayerFix(owner: String, expectedEpoch: Long, fix: WorldPlayerFix): Boolean = mutex.withLock {
        if (owner !in leases || expectedEpoch != leaseEpoch || session?.pauseReason == WorldPauseReason.PLAYER_BATTLE) return@withLock false
        val timestamp = now()
        if (fix.capturedAt < 0 || fix.capturedAt > timestamp) return@withLock false
        // A clock rollback must not let an old, future-dated fix suppress new valid samples.
        if (playerFix != null && playerFix!!.capturedAt <= timestamp && fix.capturedAt <= playerFix!!.capturedAt) return@withLock false
        var accepted = false
        guarded {
            if (!loadLocked()) return@guarded
            if (_snapshot.value.status == EcosystemStatus.FAILED) {
                playerFix = fix // Queue accepted input for explicit reconciliation, without writing world state.
                _snapshot.value = _snapshot.value.copy(playerFix = fix, observedAt = now())
                accepted = true
                return@guarded
            }
            checkpointLocked {
                playerFix = fix
                session = session!!.copy(regionLatitude = fix.position.latitude, regionLongitude = fix.position.longitude, revision = session!!.revision + 1)
                store.recordInput(WorldEcosystemInput("fix:$leaseEpoch:${fix.capturedAt}", session!!.tickIndex, "LOCATION",
                    fix.position.latitude, fix.position.longitude, createdAt = now()))
                refreshPopulationLocked()
                persistLocked()
            }
            publishLocked()
            accepted = true
        }
        accepted
    }

    suspend fun retry(owner: String): Boolean = mutex.withLock {
        if (owner !in leases) return@withLock false
        var recovered = false
        guarded {
            if (!loadLocked()) return@guarded
            publishLocked(EcosystemStatus.LOADING)
            checkpointLocked {
                advanceLocked(replay = true)
                playerFix?.let { fix -> session = session!!.copy(regionLatitude = fix.position.latitude, regionLongitude = fix.position.longitude) }
                refreshPopulationLocked()
                session = session!!.copy(revision = session!!.revision + 1)
                persistLocked()
            }
            publishLocked()
            startLoopLocked()
            recovered = true
        }
        recovered
    }

    /** Local database work only. Resolve network/provider inputs before submitting a command. */
    suspend fun <T> executeCommand(
        owner: String, command: RadarCommand, action: suspend (RadarCommandContext) -> T
    ): RadarCommandResult<T> = mutex.withLock {
        if (owner !in leases) return@withLock RadarCommandResult.Rejected(RadarRejection.INACTIVE)
        _snapshot.value.commandRejection(command, now())?.let { return@withLock RadarCommandResult.Rejected(it) }
        val previous = memory()
        publishLocked(EcosystemStatus.UPDATING)
        try {
            val result: RadarCommandResult<T> = store.transaction {
                refreshPopulationLocked() // Recheck expiry, claims, deletion and coordinates inside the transaction.
                val current = currentSnapshot(if (command.kind == RadarCommandKind.BATTLE_COMMIT) EcosystemStatus.FROZEN else EcosystemStatus.READY)
                val rejection = current.commandRejection(command, now())
                if (rejection != null) {
                    if (session != previous.session) persistLocked()
                    RadarCommandResult.Rejected(rejection)
                } else {
                    if (command.kind == RadarCommandKind.BATTLE_RESERVATION || command.kind == RadarCommandKind.EVENT_RESERVATION) advanceLocked()
                    store.saveMovement(actors)
                    val value = action(RadarCommandContext(currentSnapshot(current.status)))
                    store.recordInput(WorldEcosystemInput("command:$leaseEpoch:${command.stamp.revision}:${command.kind}", session!!.tickIndex,
                        command.kind.name, playerFix?.position?.latitude, playerFix?.position?.longitude, command.individualId, now()))
                    if (command.kind == RadarCommandKind.BATTLE_RESERVATION || command.kind == RadarCommandKind.EVENT_RESERVATION) {
                        session = session!!.copy(pauseReason = WorldPauseReason.PLAYER_BATTLE)
                    }
                    refreshPopulationLocked()
                    session = session!!.copy(revision = session!!.revision + 1)
                    persistLocked()
                    RadarCommandResult.Applied(value)
                }
            }
            publishLocked()
            if (session!!.pauseReason == WorldPauseReason.PLAYER_BATTLE) { loop?.cancel(); loop = null }
            result
        } catch (cancelled: CancellationException) {
            restore(previous)
            publishLocked()
            throw cancelled
        } catch (rejected: RadarCommandException) {
            restore(previous)
            publishLocked()
            RadarCommandResult.Rejected(rejected.rejection ?: RadarRejection.NOT_READY)
        } catch (rejected: WorldInteractionException) {
            restore(previous)
            publishLocked()
            RadarCommandResult.Rejected(when (rejected.reason) {
                InteractionFailure.BUSY -> RadarRejection.CLAIMED
                InteractionFailure.UNAVAILABLE -> RadarRejection.UNAVAILABLE
                InteractionFailure.STALE_LOCATION -> RadarRejection.STALE_LOCATION
            })
        } catch (failure: Exception) {
            restore(previous)
            failStorageLocked()
            RadarCommandResult.Failed(EcosystemIssue.STORAGE)
        }
    }

    /** External replies and player transcript inputs commit against one reconciled clock. */
    internal suspend fun commitExternalInput(action:suspend ()->Boolean):Boolean = mutex.withLock {
        if(leases.isEmpty() || _snapshot.value.status!=EcosystemStatus.READY || session?.pauseReason==WorldPauseReason.PLAYER_BATTLE)return@withLock false
        try {
            val committed=checkpointLocked {
                advanceLocked()
                persistLocked() // Accepted input tick and all movement are one durable baseline.
                val accepted=action()
                refreshPopulationLocked()
                session=session!!.copy(revision=session!!.revision+1)
                persistLocked()
                accepted
            }
            publishLocked()
            committed
        } catch (cancelled: CancellationException) {
            publishLocked()
            throw cancelled
        } catch (rejected: WorldInteractionException) {
            publishLocked()
            throw rejected
        } catch (failure: Exception) {
            failStorageLocked()
            false
        }
    }

    /** Only the existing battle owner may call this with its persisted event/result operation. */
    internal suspend fun <T> finishBattle(action: suspend () -> T): RadarCommandResult<T> = mutex.withLock {
        val previous = memory()
        try {
            if (!loadLocked()) return@withLock RadarCommandResult.Failed(EcosystemIssue.UNSUPPORTED_RULES)
            val value = checkpointLocked {
                val result = action()
                advanceLocked()
                session = session!!.copy(pauseReason = if (leases.isEmpty()) WorldPauseReason.RADAR_HIDDEN else null, revision = session!!.revision + 1)
                refreshPopulationLocked()
                persistLocked()
                result
            }
            publishLocked()
            startLoopLocked()
            RadarCommandResult.Applied(value)
        } catch (cancelled: CancellationException) {
            restore(previous)
            throw cancelled
        } catch (failure: Exception) {
            restore(previous)
            failStorageLocked()
            RadarCommandResult.Failed(EcosystemIssue.STORAGE)
        }
    }

    private suspend fun loadLocked(): Boolean {
        if (session == null) {
            session = store.loadSession() ?: WorldEcosystemSession(seed = newSeed(), lastCheckpointAt = now().coerceAtLeast(0))
            lastPersistedAt = session!!.lastCheckpointAt
            if (session!!.rulesVersion == 1) {
                // v1 had no moving actors or autonomous reducers. Rebase once, preserving
                // identity/seed/history without inventing retroactive movement under new rules.
                session = session!!.copy(rulesVersion = WorldEcosystemClock.RULES_VERSION,
                    lastCheckpointAt = maxOf(now(), session!!.lastCheckpointAt), tickRemainderMillis = 0,
                    revision = session!!.revision + 1)
            }
        }
        if (session!!.rulesVersion != WorldEcosystemClock.RULES_VERSION) {
            _snapshot.value = _snapshot.value.copy(status = EcosystemStatus.FAILED, session = session, issue = EcosystemIssue.UNSUPPORTED_RULES)
            return false
        }
        return true
    }

    private suspend fun advanceLocked(replay: Boolean = false) {
        val previous = session!!
        val advance = WorldEcosystemClock.advance(previous, now())
        for (offset in 1..advance.steps) {
            val tick = previous.tickIndex + offset
            val timestamp = previous.lastCheckpointAt + (WorldEcosystemClock.TICK_MILLIS - previous.tickRemainderMillis) +
                (offset - 1) * WorldEcosystemClock.TICK_MILLIS
            if (!replay) previousPositions = actors.associate { it.individualId to GeoPoint(it.latitude, it.longitude) }
            actors = WorldEcosystemEngine.step(previous.seed, tick, actors, claimedIndividuals, personalities)
                .filter { it.expiresAt > timestamp || it.individualId in claimedIndividuals }
            session = session!!.copy(tickIndex = tick, lastCheckpointAt = timestamp, tickRemainderMillis = 0)
            if (store.advanceInteractions(session!!, actors, replay, if(replay) null else playerFix)) {
                persistLocked()
                refreshPopulationLocked(timestamp)
            }
            if (offset % 32 == 0) yield()
        }
        session = advance.session.copy(
            pauseReason = if (previous.pauseReason == WorldPauseReason.PLAYER_BATTLE) previous.pauseReason
                else if (leases.isEmpty()) WorldPauseReason.RADAR_HIDDEN else null,
            revision = maxOf(session!!.revision, previous.revision) + if (advance.session != previous) 1 else 0
        )
        if (advance.steps > 0) {
            motionFrameNanos = if (replay) 0 else System.nanoTime()
            if (replay) previousPositions = emptyMap()
            rebuildIndividualsLocked()
        }
        if (advance.dormantMillis > 0) {
            refreshPopulationLocked(advance.session.lastCheckpointAt)
        }
    }

    private suspend fun refreshPopulationLocked(timestamp: Long = now()) {
        val checkpoint = session!!
        val player = checkpoint.regionLatitude?.let { lat ->
            checkpoint.regionLongitude?.let { lon -> GeoPoint.fromOrNull(lat, lon) }
        }
        val retained = individuals.map { it.individualId }.toSet()
        val population = store.loadPopulation(timestamp)
        val previousIndividuals = individuals
        val previousInteractions = interactions
        val previousClaims = claimedIndividuals
        val moving = actors.associateBy { it.individualId }
        actors = if (player == null) emptyList() else population.spawns.asSequence()
            .filter { it.recruitmentState == RecruitmentState.WILD && (it.expiresAt > timestamp || it.individualId in population.claimedIndividuals) }
            .mapNotNull { spawn ->
                val position = GeoPoint.fromOrNull(spawn.latitude, spawn.longitude) ?: return@mapNotNull null
                val radius = RadarWorldGeometry.REGION_RADIUS_METERS + if (spawn.individualId in retained) 50.0 else 0.0
                if (spawn.individualId !in population.claimedIndividuals && !RadarWorldGeometry.relative(player, position).withinRadius(radius)) return@mapNotNull null
                val current = moving[spawn.individualId]
                if (current != null && current.id == spawn.id && current.movementTick > spawn.movementTick &&
                    spawn.nextDecisionTick <= current.nextDecisionTick) {
                    spawn.copy(latitude = current.latitude, longitude = current.longitude,
                        wanderTargetLatitude = current.wanderTargetLatitude, wanderTargetLongitude = current.wanderTargetLongitude,
                        movementState = current.movementState, movementTick = current.movementTick, nextDecisionTick = current.nextDecisionTick)
                } else if (current == null && spawn.movementTick == 0L) spawn.copy(movementTick = checkpoint.tickIndex) else spawn
            }
            .sortedBy { it.individualId }
            .toList()
        interactions = population.interactions
        claimedIndividuals = population.claimedIndividuals
        personalities = population.personalities
        rebuildIndividualsLocked()
        if (previousIndividuals != individuals || previousInteractions != interactions || previousClaims != claimedIndividuals) {
            session = checkpoint.copy(revision = checkpoint.revision + 1)
        }
    }

    private suspend fun persistLocked() {
        store.saveMovement(actors)
        store.saveSession(session!!)
        lastPersistedAt = session!!.lastCheckpointAt
    }

    private fun rebuildIndividualsLocked() {
        individuals = actors.mapNotNull { spawn ->
            val position = GeoPoint.fromOrNull(spawn.latitude, spawn.longitude) ?: return@mapNotNull null
            val home = GeoPoint.fromOrNull(spawn.homeLatitude, spawn.homeLongitude) ?: return@mapNotNull null
            EcosystemIndividual(spawn.individualId, spawn.id, position, home,
                spawn.wanderTargetLatitude?.let { lat -> spawn.wanderTargetLongitude?.let { lon -> GeoPoint.fromOrNull(lat, lon) } },
                spawn.movementState, spawn.anchorRadiusMeters, spawn.denId, spawn.ecosystemEmotion,
                previousPositions[spawn.individualId], motionFrameNanos)
        }
    }

    private fun publishLocked(status: EcosystemStatus? = null) {
        val currentStatus = status ?: when {
            session!!.pauseReason == WorldPauseReason.PLAYER_BATTLE -> EcosystemStatus.FROZEN
            leases.isEmpty() -> EcosystemStatus.SUSPENDED
            else -> EcosystemStatus.READY
        }
        _snapshot.value = currentSnapshot(currentStatus)
        store.setRadarActive(leases.isNotEmpty() && currentStatus in listOf(EcosystemStatus.READY,EcosystemStatus.UPDATING))
        store.setRadarAutonomousEnabled(autonomousLeases.isNotEmpty() && currentStatus in listOf(EcosystemStatus.READY,EcosystemStatus.UPDATING))
    }

    private fun currentSnapshot(status: EcosystemStatus) = EcosystemSnapshot(
        status = status, session = session, individuals = individuals, interactions = interactions,
        claimedIndividuals = claimedIndividuals, playerFix = playerFix, leaseEpoch = leaseEpoch, observedAt = now()
    )

    private data class Memory(
        val session: WorldEcosystemSession?, val individuals: List<EcosystemIndividual>,
        val interactions: List<EcosystemInteractionSummary>, val claims: Set<String>,
        val fix: WorldPlayerFix?, val lastPersistedAt: Long,
        val actors: List<WorldSpawn>, val personalities: Map<String, DigimonPersonalityType>,
        val previousPositions: Map<String, GeoPoint>, val motionFrameNanos: Long
    )
    private fun memory() = Memory(session, individuals, interactions, claimedIndividuals, playerFix, lastPersistedAt,
        actors, personalities, previousPositions, motionFrameNanos)
    private fun restore(memory: Memory) {
        session = memory.session
        individuals = memory.individuals
        interactions = memory.interactions
        claimedIndividuals = memory.claims
        playerFix = memory.fix
        lastPersistedAt = memory.lastPersistedAt
        actors = memory.actors
        personalities = memory.personalities
        previousPositions = memory.previousPositions
        motionFrameNanos = memory.motionFrameNanos
    }
    private suspend fun <T> checkpointLocked(action: suspend () -> T): T {
        val previous = memory()
        try { return store.transaction(action) } catch (failure: Throwable) { restore(previous); throw failure }
    }
    private fun failStorageLocked() {
        _snapshot.value = currentSnapshot(EcosystemStatus.FAILED).copy(issue = EcosystemIssue.STORAGE)
        loop?.cancel()
        loop = null
        store.setRadarActive(false)
    }

    private fun startLoopLocked() {
        if (leases.isEmpty() || session!!.pauseReason == WorldPauseReason.PLAYER_BATTLE || loop?.isActive == true) return
        loop = externalScope.launch {
            while (isActive) {
                delay(WorldEcosystemClock.TICK_MILLIS)
                mutex.withLock {
                    guarded {
                        checkpointLocked {
                            advanceLocked()
                            if (session!!.lastCheckpointAt - lastPersistedAt >= 10_000L) {
                                refreshPopulationLocked()
                                persistLocked()
                            }
                        }
                        publishLocked()
                    }
                    if (_snapshot.value.status == EcosystemStatus.FAILED) return@launch
                }
            }
        }
    }

    private suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            failStorageLocked()
        }
    }
}
