package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadarCommandGateTest {
    private class Store : WorldEcosystemStore {
        var saved: WorldEcosystemSession? = null
        var failWrites = false
        var loadBarrier: CompletableDeferred<Unit>? = null
        var effects = 0
        var claimed = emptySet<String>()
        var spawns = listOf(WorldSpawn(id = 1, cardCharacterId = 1, individualId = "wild", latitude = 0.0,
            longitude = 0.0, spawnedAt = 0, expiresAt = 100_000))
        override suspend fun loadSession() = saved
        override suspend fun saveSession(session: WorldEcosystemSession) {
            check(!failWrites)
            saved = session
        }
        override suspend fun loadSpawns(now: Long) = spawns
        override suspend fun loadPopulation(now: Long): EcosystemPopulation {
            loadBarrier?.await()
            return EcosystemPopulation(spawns, claimed)
        }
        override suspend fun <T> transaction(action: suspend () -> T): T {
            val previous = saved
            val previousEffects = effects
            try { return action() } catch (failure: Throwable) {
                saved = previous
                effects = previousEffects
                throw failure
            }
        }
    }

    @Test fun `external input checkpoint failure rolls back its effects and logical time`() = runTest {
        var now=0L
        val store=Store()
        val world=WorldEcosystemCoordinator(store,backgroundScope,{now},{42L})
        world.acquire("radar")
        val checkpoint=store.saved
        now=4_500L
        assertFalse(world.commitExternalInput { store.effects++;store.failWrites=true;true })
        assertEquals(0,store.effects)
        assertEquals(checkpoint,store.saved)
        assertEquals(checkpoint,world.snapshot.value.session)
        assertEquals(EcosystemStatus.FAILED,world.snapshot.value.status)
        store.failWrites=false
        assertTrue(world.retry("radar"))
        assertEquals(3L,world.snapshot.value.session!!.tickIndex)
        world.release("radar")
    }

    @Test fun `external input cannot apply while hidden or battle frozen`() = runTest {
        val store=Store()
        val world=WorldEcosystemCoordinator(store,backgroundScope,{testScheduler.currentTime},{42L})
        assertFalse(world.commitExternalInput { store.effects++;true })
        world.acquire("radar")
        assertTrue(world.setBattlePaused(true))
        assertFalse(world.commitExternalInput { store.effects++;true })
        world.release("radar")
        assertFalse(world.commitExternalInput { store.effects++;true })
        assertEquals(0,store.effects)
    }

    @Test fun `same ticket cannot apply two mutations`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        world.acceptPlayerFix("radar", world.snapshot.value.leaseEpoch, WorldPlayerFix(GeoPoint(0.0, 0.0), 0))
        val command = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.ENCOUNTER, "wild")
        assertTrue(world.executeCommand("radar", command) { store.effects++ } is RadarCommandResult.Applied)
        assertEquals(RadarRejection.STALE_SNAPSHOT, (world.executeCommand("radar", command) { store.effects++ } as RadarCommandResult.Rejected).reason)
        assertEquals(1, store.effects)
        world.release("radar")
    }

    @Test fun `an old resume epoch cannot apply callbacks after reacquiring the same lease`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        val oldEpoch = world.snapshot.value.leaseEpoch
        val command = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)
        world.release("radar")
        world.acquire("radar")
        assertFalse(world.acceptPlayerFix("radar", oldEpoch, WorldPlayerFix(GeoPoint(0.0, 0.0), 0)))
        assertTrue(world.executeCommand("radar", command) { store.effects++ } is RadarCommandResult.Rejected)
        assertEquals(0, store.effects)
        world.release("radar")
    }

    @Test fun `a released surface cannot mutate through another surface's live lease`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        world.acquire("overlay")
        world.release("radar")
        val result = world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)) { store.effects++ }
        assertEquals(RadarRejection.INACTIVE, (result as RadarCommandResult.Rejected).reason)
        assertEquals(0, store.effects)
        world.release("overlay")
    }

    @Test fun `mutation waits for reconciliation and rejects its loading ticket`() = runTest {
        val store = Store().apply { loadBarrier = CompletableDeferred() }
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        val acquisition = launch { world.acquire("radar") }
        runCurrent()
        assertEquals(EcosystemStatus.LOADING, world.snapshot.value.status)
        val command = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)
        var result: RadarCommandResult<Int>? = null
        val pending = launch { result = world.executeCommand("radar", command) { store.effects++ } }
        runCurrent()
        assertEquals(0, store.effects)
        store.loadBarrier!!.complete(Unit)
        acquisition.join()
        pending.join()
        assertTrue(result is RadarCommandResult.Rejected)
        assertEquals(0, store.effects)
        world.release("radar")
    }

    @Test fun `location can become stale without a new GPS callback`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        world.acceptPlayerFix("radar", world.snapshot.value.leaseEpoch, WorldPlayerFix(GeoPoint(0.0, 0.0), 0))
        advanceTimeBy(30_001)
        runCurrent()
        val result = world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.ENCOUNTER, "wild")) { store.effects++ }
        assertEquals(RadarRejection.STALE_LOCATION, (result as RadarCommandResult.Rejected).reason)
        assertEquals(0, store.effects)
        world.release("radar")
    }

    @Test fun `settings remain usable without location but encounters do not`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        assertTrue(world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)) { store.effects++ } is RadarCommandResult.Applied)
        val result = world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.REGION)) { store.effects++ }
        assertEquals(RadarRejection.STALE_LOCATION, (result as RadarCommandResult.Rejected).reason)
        assertEquals(1, store.effects)
        world.release("radar")
    }

    @Test fun `unpublished claim changes reject a stale selection before its action runs`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        world.acceptPlayerFix("radar", world.snapshot.value.leaseEpoch, WorldPlayerFix(GeoPoint(0.0, 0.0), 0))
        val command = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.ENCOUNTER, "wild")
        store.claimed = setOf("wild")
        assertTrue(world.executeCommand("radar", command) { store.effects++ } is RadarCommandResult.Rejected)
        assertEquals(0, store.effects)
        world.release("radar")
    }

    @Test fun `command and checkpoint failure roll back effects and require explicit recovery`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        val previous = store.saved
        val command = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)
        store.failWrites = true
        assertTrue(world.executeCommand("radar", command) { store.effects++ } is RadarCommandResult.Failed)
        assertEquals(0, store.effects)
        assertEquals(previous, store.saved)
        assertEquals(EcosystemStatus.FAILED, world.snapshot.value.status)
        world.refreshPopulation()
        assertEquals(EcosystemStatus.FAILED, world.snapshot.value.status)
        store.failWrites = false
        assertTrue(world.retry("radar"))
        assertEquals(EcosystemStatus.READY, world.snapshot.value.status)
        assertTrue(world.executeCommand("radar", command) { store.effects++ } is RadarCommandResult.Rejected)
        world.release("radar")
    }

    @Test fun `battle reservation freezes other commands until completion is durably saved`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        world.acceptPlayerFix("radar", world.snapshot.value.leaseEpoch, WorldPlayerFix(GeoPoint(0.0, 0.0), 0))
        val result = world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.BATTLE_RESERVATION, "wild")) { store.effects++ }
        assertTrue(result is RadarCommandResult.Applied)
        assertEquals(EcosystemStatus.FROZEN, world.snapshot.value.status)
        assertTrue(world.executeCommand("radar", RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)) { store.effects++ } is RadarCommandResult.Rejected)
        store.failWrites = true
        assertTrue(world.finishBattle { store.effects++ } is RadarCommandResult.Failed)
        assertEquals(WorldPauseReason.PLAYER_BATTLE, store.saved!!.pauseReason)
        assertEquals(1, store.effects)
        store.failWrites = false
        assertTrue(world.finishBattle { store.effects++ } is RadarCommandResult.Applied)
        assertEquals(EcosystemStatus.READY, world.snapshot.value.status)
        assertEquals(2, store.effects)
        world.release("radar")
    }

    @Test fun `cancelled command restores readiness and rolls back its local transaction`() = runTest {
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { testScheduler.currentTime }, { 42L })
        world.acquire("radar")
        val ticket = RadarCommand(world.snapshot.value.commandStamp!!, RadarCommandKind.SETTINGS)
        val barrier = CompletableDeferred<Unit>()
        val operation = launch {
            world.executeCommand("radar", ticket) { store.effects++; barrier.await() }
        }
        runCurrent()
        assertEquals(EcosystemStatus.UPDATING, world.snapshot.value.status)
        operation.cancel()
        operation.join()
        assertEquals(0, store.effects)
        assertEquals(EcosystemStatus.READY, world.snapshot.value.status)
        assertTrue(world.executeCommand("radar", ticket) { store.effects++ } is RadarCommandResult.Applied)
        world.release("radar")
    }

    @Test fun `a fresh sample can replace the old fix after clock rollback`() = runTest {
        var wallClock = 10_000L
        val store = Store()
        val world = WorldEcosystemCoordinator(store, backgroundScope, { wallClock }, { 42L })
        world.acquire("radar")
        val epoch = world.snapshot.value.leaseEpoch
        assertTrue(world.acceptPlayerFix("radar", epoch, WorldPlayerFix(GeoPoint(0.0, 0.0), wallClock)))
        wallClock = 9_000L
        assertFalse(world.acceptPlayerFix("radar", epoch, WorldPlayerFix(GeoPoint(1.0, 1.0), 20_000L)))
        assertTrue(world.acceptPlayerFix("radar", epoch, WorldPlayerFix(GeoPoint(0.0, 0.0001), wallClock)))
        assertEquals(9_000L, world.snapshot.value.playerFix!!.capturedAt)
        world.release("radar")
    }

    @Test fun `event joining rejects a missing original participant rather than checking only those still loaded`() {
        val session=WorldEcosystemSession(seed=42,lastCheckpointAt=0)
        val actor=EcosystemIndividual("a",1,GeoPoint(0.0,0.0),GeoPoint(0.0,0.0),null,
            com.github.nacabaro.vbhelper.domain.world.WorldMovementState.HOME,25.0,null,0)
        val frame=EcosystemSnapshot(status=EcosystemStatus.READY,session=session,individuals=listOf(actor),
            playerFix=WorldPlayerFix(GeoPoint(0.0,0.0),0),interactions=listOf(
                EcosystemInteractionSummary("event",InteractionType.CHAT,InteractionOrigin.AUTONOMOUS,InteractionState.ACTIVE,0,listOf("a","missing"),100_000)))
        val request=RadarCommand(frame.commandStamp!!,RadarCommandKind.EVENT_PARTICIPATION,interactionId="event")
        assertEquals(RadarRejection.UNAVAILABLE,frame.commandRejection(request,0))
    }
}
