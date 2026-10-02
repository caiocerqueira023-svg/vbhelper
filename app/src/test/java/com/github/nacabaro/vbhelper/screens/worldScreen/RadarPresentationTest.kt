package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.ecosystem.*
import org.junit.Assert.*
import org.junit.Test

class RadarPresentationTest {
    private fun assets(id: Long = 1, individualId: String = "wild") = WorldDtos.SpawnWithDetails(
        id, 1, individualId, 50.0, 50.0, 0, 100_000, false, 1, 3, 1, "dim000_mon02",
        NfcCharacter.Attribute.Virus, 2_000, 1_000, 500, false,
        byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), byteArrayOf(4), byteArrayOf(5), byteArrayOf(6),
        1, 1, "Testmon", 50, RecruitmentState.WILD
    )
    private fun snapshot(status: EcosystemStatus = EcosystemStatus.READY, capturedAt: Long = 0, observedAt: Long = 0) = EcosystemSnapshot(
        status = status, session = WorldEcosystemSession(seed = 42, lastCheckpointAt = 0),
        playerFix = WorldPlayerFix(GeoPoint(0.0, 0.0), capturedAt), observedAt = observedAt,
        individuals = listOf(EcosystemIndividual("wild", 1, GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.0), null,
            WorldMovementState.HOME, 25.0, null, 0))
    )

    @Test fun `marker coordinates come from the snapshot rather than an older asset DTO`() {
        val marker = radarPresentations(snapshot(), listOf(assets())).single()
        assertEquals(GeoPoint(0.0, 0.0), marker.individual.position)
        assertEquals(50.0, marker.assets.latitude, 0.0)
        assertEquals("wild", marker.individual.individualId)
    }

    @Test fun `assets with a mismatched spawn identity cannot represent the selected individual`() {
        assertTrue(radarPresentations(snapshot(), listOf(assets(id = 2))).isEmpty())
        assertTrue(radarPresentations(snapshot(), listOf(assets(individualId = "another"))).isEmpty())
    }

    @Test fun `movement pose selects walking frames while reduced motion keeps a static frame`() {
        val frame = snapshot().copy(individuals = snapshot().individuals.map { it.copy(movementState = WorldMovementState.WANDERING) })
        val marker = radarPresentations(frame, listOf(assets())).single()
        assertArrayEquals(byteArrayOf(4), marker.spriteFrame(1, true))
        assertArrayEquals(byteArrayOf(1), marker.spriteFrame(1, false))
    }

    @Test fun `loading and storage failure take priority over empty and permission states`() {
        assertEquals(RadarSurfaceState.RECONCILING, radarSurfaceState(snapshot(EcosystemStatus.LOADING), false, 0, 0))
        assertEquals(RadarSurfaceState.STORAGE_FAILURE, radarSurfaceState(snapshot(EcosystemStatus.FAILED).copy(issue = EcosystemIssue.STORAGE), false, 0, 0))
        assertEquals(RadarSurfaceState.UNSUPPORTED_WORLD, radarSurfaceState(snapshot(EcosystemStatus.FAILED).copy(issue = EcosystemIssue.UNSUPPORTED_RULES), true, 1, 1))
    }

    @Test fun `freshness is evaluated even when coordinates have not changed`() {
        assertEquals(RadarSurfaceState.READY, radarSurfaceState(snapshot(observedAt = 30_000), true, 1, 1))
        assertEquals(RadarSurfaceState.STALE_LOCATION, radarSurfaceState(snapshot(observedAt = 30_001), true, 1, 1))
        assertEquals(RadarSurfaceState.LOCATING, radarSurfaceState(snapshot().copy(playerFix = null), true, 1, 1))
    }

    @Test fun `permission and card states explain why the local region is empty`() {
        assertEquals(RadarSurfaceState.LOCATION_PERMISSION, radarSurfaceState(snapshot(), false, 1, 1))
        assertEquals(RadarSurfaceState.NO_CARDS, radarSurfaceState(snapshot(), true, 0, 0))
        assertEquals(RadarSurfaceState.CARDS_DISABLED, radarSurfaceState(snapshot(), true, 1, 0))
        assertEquals(RadarSurfaceState.EMPTY_REGION, radarSurfaceState(snapshot().copy(individuals = emptyList()), true, 1, 1))
    }

    @Test fun `failed battle finalization remains an explicit recoverable state`() {
        assertEquals(RadarSurfaceState.SAVING_BATTLE, radarSurfaceState(snapshot(EcosystemStatus.FROZEN), true, 1, 1, pendingBattle = true, savingBattle = true))
        assertEquals(RadarSurfaceState.BATTLE_SAVE_FAILURE, radarSurfaceState(snapshot(EcosystemStatus.FAILED), true, 1, 1, pendingBattle = true))
    }

    @Test fun `failed reconciliation keeps the last reconciled map center despite a queued fix`() {
        val last = GeoPoint(0.0, 0.0)
        val pending = GeoPoint(1.0, 1.0)
        val frame = snapshot(EcosystemStatus.FAILED).copy(
            session = snapshot().session!!.copy(regionLatitude = last.latitude, regionLongitude = last.longitude),
            playerFix = WorldPlayerFix(pending, 0)
        )
        assertEquals(last, radarPlayerPosition(frame))
        assertEquals(pending, radarPlayerPosition(frame.copy(status = EcosystemStatus.READY)))
    }
}
