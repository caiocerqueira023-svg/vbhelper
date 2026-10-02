package com.github.nacabaro.vbhelper.world

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Accepted geographic coordinates, independent of Android Location and either renderer. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(valid(latitude, longitude)) { "Invalid geographic coordinates" }
    }

    companion object {
        private fun valid(latitude: Double, longitude: Double) =
            latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0

        fun fromOrNull(latitude: Double, longitude: Double): GeoPoint? =
            if (valid(latitude, longitude)) GeoPoint(latitude, longitude) else null
    }
}

data class RadarMapOffset(val xMeters: Double, val yMeters: Double)

data class RadarRelativePosition(
    val northMeters: Double,
    val eastMeters: Double,
    val distanceMeters: Double,
    val bearingDegrees: Double
) {
    val withinInteractionRange: Boolean get() = withinRadius(RadarWorldGeometry.INTERACTION_RANGE_METERS)

    fun withinRadius(radiusMeters: Double): Boolean = distanceMeters <= radiusMeters

    /** Screen Y points down. Rotate the north-up vector once by the negative compass heading. */
    fun mapOffset(headingDegrees: Double): RadarMapOffset {
        require(headingDegrees.isFinite())
        val angle = Math.toRadians(headingDegrees)
        return RadarMapOffset(
            eastMeters * cos(angle) - northMeters * sin(angle),
            -eastMeters * sin(angle) - northMeters * cos(angle)
        )
    }
}

object RadarWorldGeometry {
    const val INTERACTION_RANGE_METERS = 40.0
    const val REGION_RADIUS_METERS = 1_000.0
    private const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Great-circle distance and initial bearing; safe at the date line and near the poles. */
    fun relative(player: GeoPoint, target: GeoPoint): RadarRelativePosition {
        val lat1 = Math.toRadians(player.latitude)
        val lat2 = Math.toRadians(target.latitude)
        val deltaLat = lat2 - lat1
        val deltaLon = Math.toRadians(wrapLongitude(target.longitude - player.longitude))
        val a = (sin(deltaLat / 2) * sin(deltaLat / 2) +
            cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)).coerceIn(0.0, 1.0)
        val distance = EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
        if (distance < 1e-7) return RadarRelativePosition(0.0, 0.0, 0.0, 0.0)
        val bearing = atan2(
            sin(deltaLon) * cos(lat2),
            cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        )
        return RadarRelativePosition(
            northMeters = distance * cos(bearing),
            eastMeters = distance * sin(bearing),
            distanceMeters = distance,
            bearingDegrees = (Math.toDegrees(bearing) + 360.0) % 360.0
        )
    }

    /** Move by real-world meters, keeping the destination longitude normalized. */
    fun offset(origin: GeoPoint, northMeters: Double, eastMeters: Double): GeoPoint {
        require(northMeters.isFinite() && eastMeters.isFinite())
        val distance = hypot(northMeters, eastMeters)
        if (distance == 0.0) return origin
        val bearing = atan2(eastMeters, northMeters)
        val angularDistance = distance / EARTH_RADIUS_METERS
        val lat = Math.toRadians(origin.latitude)
        // A tiny pole inset avoids the destination formula's 0/0 longitude singularity.
        val stableLat = lat.coerceIn(-Math.PI / 2 + 1e-12, Math.PI / 2 - 1e-12)
        val destinationLat = asin((sin(stableLat) * cos(angularDistance) +
            cos(stableLat) * sin(angularDistance) * cos(bearing)).coerceIn(-1.0, 1.0))
        val destinationLon = Math.toRadians(origin.longitude) + atan2(
            sin(bearing) * sin(angularDistance) * cos(stableLat),
            cos(angularDistance) - sin(stableLat) * sin(destinationLat)
        )
        return GeoPoint(Math.toDegrees(destinationLat), wrapLongitude(Math.toDegrees(destinationLon)))
    }

    private fun wrapLongitude(degrees: Double): Double = ((degrees + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
}
