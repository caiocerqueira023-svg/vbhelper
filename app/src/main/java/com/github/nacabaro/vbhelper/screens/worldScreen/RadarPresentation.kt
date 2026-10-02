package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.dtos.WorldDtos
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemIndividual
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemIssue
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemSnapshot
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemStatus

data class RadarPresentation(val individual: EcosystemIndividual, val assets: WorldDtos.SpawnWithDetails) {
    fun spriteFrame(frame: Int, motion: Boolean): ByteArray {
        if (!motion) return assets.spriteIdle
        val walking = individual.movementState != WorldMovementState.HOME
        val bytes = if (walking) {
            if (frame % 2 == 0) assets.spriteWalk else assets.spriteWalk2
        } else if (frame % 2 == 0) assets.spriteIdle else assets.spriteIdle2
        return bytes.takeIf { it.isNotEmpty() } ?: assets.spriteIdle
    }
}

/** Asset DTO coordinates are deliberately ignored. Only matching identities can supply sprites. */
fun radarPresentations(snapshot: EcosystemSnapshot, assets: List<WorldDtos.SpawnWithDetails>): List<RadarPresentation> {
    val byId = assets.associateBy { it.individualId }
    return snapshot.individuals.mapNotNull { individual ->
        byId[individual.individualId]?.takeIf { it.id == individual.spawnId }?.let { RadarPresentation(individual, it) }
    }
}

fun radarPlayerPosition(snapshot: EcosystemSnapshot): GeoPoint? {
    val checkpointPosition = snapshot.session?.let { session ->
        session.regionLatitude?.let { lat -> session.regionLongitude?.let { lon -> GeoPoint.fromOrNull(lat, lon) } }
    }
    return if (snapshot.status == EcosystemStatus.FAILED || snapshot.status == EcosystemStatus.LOADING)
        checkpointPosition ?: snapshot.playerFix?.position else snapshot.playerFix?.position ?: checkpointPosition
}

enum class RadarSurfaceState {
    RECONCILING, UPDATING, STORAGE_FAILURE, UNSUPPORTED_WORLD, LOCATION_PERMISSION, LOCATING,
    STALE_LOCATION, NO_CARDS, CARDS_DISABLED, POPULATING, POPULATION_FAILURE, EMPTY_REGION,
    READY, APPROXIMATE_LOCATION, FROZEN, SUSPENDED, SAVING_BATTLE, BATTLE_SAVE_FAILURE
}

fun radarSurfaceState(
    snapshot: EcosystemSnapshot, locationPermission: Boolean, cardCount: Int?, enabledCardCount: Int?,
    populating: Boolean = false, populationFailed: Boolean = false,
    pendingBattle: Boolean = false, savingBattle: Boolean = false
): RadarSurfaceState {
    if (pendingBattle) return if (savingBattle) RadarSurfaceState.SAVING_BATTLE else RadarSurfaceState.BATTLE_SAVE_FAILURE
    when (snapshot.status) {
        EcosystemStatus.LOADING -> return RadarSurfaceState.RECONCILING
        EcosystemStatus.UPDATING -> return RadarSurfaceState.UPDATING
        EcosystemStatus.FAILED -> return if (snapshot.issue == EcosystemIssue.UNSUPPORTED_RULES) RadarSurfaceState.UNSUPPORTED_WORLD else RadarSurfaceState.STORAGE_FAILURE
        EcosystemStatus.FROZEN -> return RadarSurfaceState.FROZEN
        EcosystemStatus.SUSPENDED -> return RadarSurfaceState.SUSPENDED
        EcosystemStatus.READY -> Unit
    }
    if (!locationPermission) return RadarSurfaceState.LOCATION_PERMISSION
    val fix = snapshot.playerFix ?: return RadarSurfaceState.LOCATING
    if (!fix.isFresh(snapshot.observedAt)) return RadarSurfaceState.STALE_LOCATION
    if (cardCount == null) return RadarSurfaceState.RECONCILING
    if (cardCount == 0) return RadarSurfaceState.NO_CARDS
    if (enabledCardCount == 0) return RadarSurfaceState.CARDS_DISABLED
    if (populationFailed) return RadarSurfaceState.POPULATION_FAILURE
    if (populating) return RadarSurfaceState.POPULATING
    if (snapshot.individuals.isEmpty()) return RadarSurfaceState.EMPTY_REGION
    if ((fix.accuracyMeters ?: 0f) > 40f) return RadarSurfaceState.APPROXIMATE_LOCATION
    return RadarSurfaceState.READY
}
