package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Geographic fixed-step behavior, independent of rendering, wall time or list order. */
object WorldEcosystemEngine {
    const val WALK_METERS_PER_SECOND = 0.5

    fun unit(seed: Long, id: String, purpose: String, tick: Long): Double =
        (EcosystemSeed.mix(seed, id, purpose, tick) ushr 11).toDouble() / 9_007_199_254_740_992.0

    fun step(
        seed: Long, tick: Long, actors: List<WorldSpawn>, claimed: Set<String> = emptySet(),
        personalities: Map<String, DigimonPersonalityType> = emptyMap()
    ): List<WorldSpawn> = actors.sortedBy { it.individualId }.map { original ->
        if (original.movementTick >= tick) return@map original
        var actor = original
        val home = GeoPoint.fromOrNull(actor.homeLatitude, actor.homeLongitude) ?: return@map actor
        val position = GeoPoint.fromOrNull(actor.latitude, actor.longitude) ?: return@map actor
        val radius = actor.anchorRadiusMeters.coerceIn(1.0, 40.0)
        val profile = DigimonSocialProfile.forIndividual(actor.individualId,
            personalities[actor.individualId] ?: DigimonPersonalityType.FRIENDLY)
        val isClaimed = actor.individualId in claimed
        if (isClaimed && actor.movementState != WorldMovementState.APPROACHING) {
            return@map actor.copy(movementTick = tick)
        }
        if (!isClaimed && actor.wanderTargetLatitude == null && tick >= actor.nextDecisionTick) {
            val horizon = (20 + profile.patience * 35 - profile.initiative * 10).toLong().coerceIn(10, 60)
            val angle = unit(seed, actor.individualId, "wander-bearing", tick) * Math.PI * 2
            val distance = sqrt(unit(seed, actor.individualId, "wander-radius", tick)) * radius * (.35 + profile.curiosity * .65)
            val target = RadarWorldGeometry.offset(home, cos(angle) * distance, sin(angle) * distance)
            actor = actor.copy(wanderTargetLatitude = target.latitude, wanderTargetLongitude = target.longitude,
                movementState = WorldMovementState.WANDERING, nextDecisionTick = tick + horizon +
                    (unit(seed, actor.individualId, "wander-horizon", tick) * horizon).toLong())
        }
        val target = actor.wanderTargetLatitude?.let { lat -> actor.wanderTargetLongitude?.let { lon -> GeoPoint.fromOrNull(lat, lon) } }
        if (target == null) return@map actor.copy(movementTick = tick)
        // Reject an invalid meeting/wander target rather than teleporting outside a territory.
        if (!RadarWorldGeometry.relative(home, target).withinRadius(radius + 0.01)) {
            return@map actor.copy(wanderTargetLatitude = home.latitude, wanderTargetLongitude = home.longitude,
                movementState = WorldMovementState.RETURNING, movementTick = tick)
        }
        val remaining = RadarWorldGeometry.relative(position, target)
        val pace = .75 + profile.initiative * .25
        val stepMeters = WALK_METERS_PER_SECOND * pace * WorldEcosystemClock.TICK_MILLIS / 1000
        if (remaining.distanceMeters <= stepMeters) {
            if (actor.movementState == WorldMovementState.WANDERING) {
                actor.copy(latitude = target.latitude, longitude = target.longitude,
                    wanderTargetLatitude = home.latitude, wanderTargetLongitude = home.longitude,
                    movementState = WorldMovementState.RETURNING, movementTick = tick)
            } else actor.copy(latitude = target.latitude, longitude = target.longitude,
                wanderTargetLatitude = null, wanderTargetLongitude = null,
                movementState = WorldMovementState.HOME, movementTick = tick)
        } else {
            val scale = stepMeters / remaining.distanceMeters
            val next = RadarWorldGeometry.offset(position, remaining.northMeters * scale, remaining.eastMeters * scale)
            actor.copy(latitude = next.latitude, longitude = next.longitude, movementTick = tick)
        }
    }
}
