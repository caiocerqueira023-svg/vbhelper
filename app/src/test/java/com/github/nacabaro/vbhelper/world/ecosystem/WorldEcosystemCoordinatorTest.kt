package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldEcosystemCoordinatorTest {
    private class Store : WorldEcosystemStore {
        var session: WorldEcosystemSession? = null
        var saves = 0
        var failSaves = false
        var spawns = emptyList<WorldSpawn>()
        var claimed = emptySet<String>()
        var interactions = emptyList<EcosystemInteractionSummary>()
        val savedRevisions = mutableListOf<Long>()
        var onAdvance: suspend (WorldEcosystemSession) -> Boolean = { false }
        var activeSurface=false
        var autonomousSurface=false
        override suspend fun loadSession() = session
        override suspend fun saveSession(session: WorldEcosystemSession) {
            check(!failSaves) { "Storage unavailable" }
            this.session = session
            saves++
            savedRevisions += session.revision
        }
        override suspend fun loadSpawns(now: Long) = spawns
        override suspend fun loadPopulation(now: Long) = EcosystemPopulation(spawns, claimed, interactions)
        override suspend fun advanceInteractions(session: WorldEcosystemSession, actors: List<WorldSpawn>, replay: Boolean, playerFix: WorldPlayerFix?) = onAdvance(session)
        override fun setRadarActive(active: Boolean) { activeSurface=active }
        override fun setRadarAutonomousEnabled(enabled: Boolean) { autonomousSurface=enabled }
    }

    @Test fun `event reconciliation cannot rewind revisions at the end of replay`() = runTest {
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        coordinator.release("radar")
        store.savedRevisions.clear()
        store.onAdvance = { session ->
            store.interactions = listOf(EcosystemInteractionSummary("npc", InteractionType.CHAT,
                InteractionOrigin.AUTONOMOUS, InteractionState.ACTIVE, session.tickIndex, listOf("a", "b"), 100_000))
            true
        }

        advanceTimeBy(6_000L)
        coordinator.acquire("radar")

        assertEquals(4L, coordinator.snapshot.value.session!!.tickIndex)
        assertTrue("Every durable revision must be monotonic: ${store.savedRevisions}",
            store.savedRevisions.zipWithNext().all { (before, after) -> after >= before })
        assertEquals(store.session!!.revision, coordinator.snapshot.value.session!!.revision)
        coordinator.release("radar")
    }

    @Test fun `a full screen conversation lease keeps the same clock and fix after Radar leaves`() = runTest {
        val store=Store()
        val world=WorldEcosystemCoordinator(store,backgroundScope,{testScheduler.currentTime},{42L})
        world.acquire("radar")
        val epoch=world.snapshot.value.leaseEpoch
        val fix=WorldPlayerFix(GeoPoint(0.0,0.0),testScheduler.currentTime)
        world.acceptPlayerFix("radar",epoch,fix)
        world.acquire("conversation")
        world.release("radar")
        assertEquals(epoch,world.snapshot.value.leaseEpoch)
        assertEquals(fix,world.snapshot.value.playerFix)
        assertTrue(store.activeSurface)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(1L,world.snapshot.value.session!!.tickIndex)
        assertEquals(42L,world.snapshot.value.session!!.seed)
        world.release("conversation")
        assertFalse(store.activeSurface)
    }

    @Test fun `regular private chat keeps the clock but disables other autonomous player interruptions`() = runTest {
        val store=Store()
        val world=WorldEcosystemCoordinator(store,backgroundScope,{testScheduler.currentTime},{42L})
        world.acquire("radar")
        assertTrue(store.autonomousSurface)
        world.acquire("private-chat",autonomous=false)
        world.release("radar")
        assertTrue(store.activeSurface)
        assertFalse(store.autonomousSurface)
        advanceTimeBy(1500)
        runCurrent()
        assertEquals(1L,world.snapshot.value.session!!.tickIndex)
        world.release("private-chat")
        assertFalse(store.activeSurface)
    }

    @Test fun `external input uses the reconciled durable tick before applying its effects`() = runTest {
        var now = 0L
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { now }, { 42L })
        coordinator.acquire("radar")
        now = 4_500L
        var inputTick: Long? = null
        assertTrue(coordinator.commitExternalInput { inputTick = store.session!!.tickIndex; true })
        assertEquals(3L, inputTick)
        assertEquals(store.session, coordinator.snapshot.value.session)
        coordinator.release("radar")
    }

    @Test fun `declined participation is not a storage failure and preserves readiness`() = runTest {
        val coordinator = WorldEcosystemCoordinator(Store(), backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        val before = coordinator.snapshot.value.session
        try {
            coordinator.commitExternalInput { throw WorldInteractionException(InteractionFailure.STALE_LOCATION) }
            fail("Participation rejection must reach the caller")
        } catch (rejected: WorldInteractionException) {
            assertEquals(InteractionFailure.STALE_LOCATION, rejected.reason)
        }
        assertEquals(EcosystemStatus.READY, coordinator.snapshot.value.status)
        assertEquals(before, coordinator.snapshot.value.session)
        coordinator.release("radar")
    }

    @Test fun `duplicate leases and two observers still advance one clock`() = runTest {
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("2d")
        coordinator.acquire("2d")
        coordinator.acquire("overlay")
        advanceTimeBy(3_000L)
        runCurrent()
        assertEquals(2L, coordinator.snapshot.value.session!!.tickIndex)
        coordinator.release("2d")
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(3L, coordinator.snapshot.value.session!!.tickIndex)
        coordinator.release("overlay")
        val saves = store.saves
        advanceTimeBy(59_000L)
        runCurrent()
        assertEquals(saves, store.saves)
        coordinator.acquire("fp")
        assertEquals(42L, coordinator.snapshot.value.session!!.tickIndex)
        assertEquals(500L, coordinator.snapshot.value.session!!.tickRemainderMillis)
        assertEquals(42L, coordinator.snapshot.value.session!!.seed)
        coordinator.release("fp")
    }

    @Test fun `suspension checkpoints fractional time across process recreation`() = runTest {
        val store = Store()
        val first = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        first.acquire("radar")
        advanceTimeBy(1_000L)
        first.release("radar")
        assertEquals(1_000L, store.session!!.tickRemainderMillis)
        advanceTimeBy(59_000L)
        val second = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 999L })
        second.acquire("radar")
        assertEquals(40L, second.snapshot.value.session!!.tickIndex)
        assertEquals(42L, second.snapshot.value.session!!.seed)
        second.release("radar")
    }

    @Test fun `battle pause survives suspension and is excluded until ownership ends`() = runTest {
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        advanceTimeBy(1_000L)
        coordinator.setBattlePaused(true)
        coordinator.release("radar")
        advanceTimeBy(60_000L)
        coordinator.acquire("radar")
        assertEquals(0L, coordinator.snapshot.value.session!!.tickIndex)
        assertEquals(EcosystemStatus.FROZEN, coordinator.snapshot.value.status)
        coordinator.setBattlePaused(false)
        advanceTimeBy(500L)
        coordinator.release("radar")
        assertEquals(1L, store.session!!.tickIndex)
        assertEquals(0L, store.session!!.tickRemainderMillis)
    }

    @Test fun `an interrupted persisted battle freeze is rebased before resuming`() = runTest {
        val store = Store().apply {
            session = WorldEcosystemSession(seed = 7L, lastCheckpointAt = 0L, pauseReason = WorldPauseReason.PLAYER_BATTLE)
        }
        advanceTimeBy(60_000L)
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.setBattlePaused(false)
        coordinator.acquire("radar")
        assertEquals(0L, coordinator.snapshot.value.session!!.tickIndex)
        coordinator.release("radar")
    }

    @Test fun `checkpoints are batched while visible and flushed on suspension`() = runTest {
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        val initialSaves = store.saves
        advanceTimeBy(9_000L)
        runCurrent()
        assertEquals(initialSaves, store.saves)
        advanceTimeBy(1_500L)
        runCurrent()
        assertEquals(initialSaves + 1, store.saves)
        coordinator.release("radar")
        assertEquals(coordinator.snapshot.value.session, store.session)
    }

    @Test fun `unsupported checkpoint is exposed without overwriting it or ticking`() = runTest {
        val old = WorldEcosystemSession(seed = 42L, rulesVersion = 99, lastCheckpointAt = 0L)
        val store = Store().apply { session = old }
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(EcosystemStatus.FAILED, coordinator.snapshot.value.status)
        assertEquals(EcosystemIssue.UNSUPPORTED_RULES, coordinator.snapshot.value.issue)
        assertEquals(old, store.session)
        assertEquals(0, store.saves)
    }

    @Test fun `battle handoff is rejected if the freeze cannot be durably saved and can be retried`() = runTest {
        val store = Store()
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        store.failSaves = true
        assertFalse(coordinator.setBattlePaused(true))
        assertEquals(EcosystemStatus.FAILED, coordinator.snapshot.value.status)
        assertNull(store.session!!.pauseReason)
        advanceTimeBy(60_000L)
        runCurrent()
        assertEquals(0L, store.session!!.tickIndex)
        store.failSaves = false
        assertTrue(coordinator.setBattlePaused(true))
        assertEquals(WorldPauseReason.PLAYER_BATTLE, store.session!!.pauseReason)
        // A failed checkpoint never handed off a battle: the elapsed interval is eligible.
        assertEquals(40L, store.session!!.tickIndex)
        coordinator.setBattlePaused(false)
        coordinator.release("radar")
    }

    @Test fun `region membership is live-location based with a small exit margin`() = runTest {
        val origin = GeoPoint(0.0, 0.0)
        val near = RadarWorldGeometry.offset(origin, 990.0, 0.0)
        val far = RadarWorldGeometry.offset(origin, 1_040.0, 0.0)
        val store = Store().apply {
            spawns = listOf(
                WorldSpawn(id = 2, cardCharacterId = 1, individualId = "b", latitude = far.latitude, longitude = far.longitude, spawnedAt = 0, expiresAt = 100_000),
                WorldSpawn(id = 1, cardCharacterId = 1, individualId = "a", latitude = near.latitude, longitude = near.longitude, spawnedAt = 0, expiresAt = 100_000)
            )
        }
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        coordinator.acceptPlayerFix("radar", coordinator.snapshot.value.leaseEpoch, WorldPlayerFix(origin, testScheduler.currentTime))
        assertEquals(listOf("a"), coordinator.snapshot.value.individuals.map { it.individualId })
        advanceTimeBy(1L)
        coordinator.acceptPlayerFix("radar", coordinator.snapshot.value.leaseEpoch,
            WorldPlayerFix(RadarWorldGeometry.offset(origin, -40.0, 0.0), testScheduler.currentTime))
        assertEquals(listOf("a"), coordinator.snapshot.value.individuals.map { it.individualId })
        advanceTimeBy(1L)
        coordinator.acceptPlayerFix("radar", coordinator.snapshot.value.leaseEpoch,
            WorldPlayerFix(RadarWorldGeometry.offset(origin, 100.0, 0.0), testScheduler.currentTime))
        assertEquals(listOf("a", "b"), coordinator.snapshot.value.individuals.map { it.individualId })
        coordinator.release("radar")
    }

    @Test fun `claimed participants stay loaded beyond expiry and the region until release`() = runTest {
        val origin = GeoPoint(0.0, 0.0)
        val position = RadarWorldGeometry.offset(origin, 1_500.0, 0.0)
        val store = Store().apply {
            spawns = listOf(WorldSpawn(id = 1, cardCharacterId = 1, individualId = "wild", latitude = position.latitude,
                longitude = position.longitude, spawnedAt = 0, expiresAt = 1_000L))
            claimed = setOf("wild")
        }
        val coordinator = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        coordinator.acquire("radar")
        coordinator.acceptPlayerFix("radar", coordinator.snapshot.value.leaseEpoch, WorldPlayerFix(origin, testScheduler.currentTime))
        advanceTimeBy(2_000L)
        coordinator.refreshPopulation()
        assertEquals(listOf("wild"), coordinator.snapshot.value.individuals.map { it.individualId })
        val revision = coordinator.snapshot.value.session!!.revision
        store.claimed = emptySet()
        coordinator.refreshPopulation()
        assertTrue(coordinator.snapshot.value.individuals.isEmpty())
        assertTrue(coordinator.snapshot.value.session!!.revision > revision)
        coordinator.release("radar")
    }
}
