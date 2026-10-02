package com.github.nacabaro.vbhelper.world

import org.junit.Assert.*
import org.junit.Test

class RadarWorldGeometryTest {
    private val origin = GeoPoint(0.0, 0.0)

    @Test fun `cardinal offsets use geographic meters and clockwise bearings`() {
        listOf(
            Triple(100.0, 0.0, 0.0), Triple(0.0, 100.0, 90.0),
            Triple(-100.0, 0.0, 180.0), Triple(0.0, -100.0, 270.0)
        ).forEach { (north, east, bearing) ->
            val position = RadarWorldGeometry.offset(origin, north, east)
            val relative = RadarWorldGeometry.relative(origin, position)
            assertEquals(100.0, relative.distanceMeters, 0.001)
            assertEquals(bearing, relative.bearingDegrees, 0.001)
            assertEquals(north, relative.northMeters, 0.001)
            assertEquals(east, relative.eastMeters, 0.001)
        }
    }

    @Test fun `zero distance has finite offsets and a safe bearing`() {
        assertEquals(RadarRelativePosition(0.0, 0.0, 0.0, 0.0), RadarWorldGeometry.relative(origin, origin))
    }

    @Test fun `longitude wrap does not turn a nearby spawn into a distant one`() {
        val player = GeoPoint(0.0, 179.9999)
        val spawn = GeoPoint(0.0, -179.9999)
        val relative = RadarWorldGeometry.relative(player, spawn)
        assertEquals(22.239, relative.distanceMeters, 0.01)
        assertEquals(90.0, relative.bearingDegrees, 0.001)
        assertTrue(relative.withinInteractionRange)
    }

    @Test fun `high latitude and polar coordinates stay finite`() {
        listOf(GeoPoint(89.999, 179.999), GeoPoint(90.0, 0.0), GeoPoint(-90.0, 0.0)).forEach { player ->
            val target = RadarWorldGeometry.offset(player, 10.0, 15.0)
            val relative = RadarWorldGeometry.relative(player, target)
            assertTrue(relative.distanceMeters.isFinite())
            assertTrue(relative.bearingDegrees.isFinite())
            assertEquals(kotlin.math.hypot(10.0, 15.0), relative.distanceMeters, 0.01)
        }
    }

    @Test fun `invalid fixes are rejected before geographic state is created`() {
        listOf(Double.NaN to 0.0, 0.0 to Double.POSITIVE_INFINITY, 91.0 to 0.0, 0.0 to 181.0)
            .forEach { (lat, lon) -> assertNull(GeoPoint.fromOrNull(lat, lon)) }
    }

    @Test fun `culling and interaction range follow the live player after walking`() {
        val player = RadarWorldGeometry.offset(origin, 1_500.0, 0.0)
        val nearby = RadarWorldGeometry.offset(player, 39.9, 0.0)
        assertFalse(RadarWorldGeometry.relative(origin, nearby).withinRadius(1_000.0))
        val relative = RadarWorldGeometry.relative(player, nearby)
        assertTrue(relative.withinRadius(1_000.0))
        assertTrue(relative.withinInteractionRange)
        assertFalse(RadarWorldGeometry.relative(player, RadarWorldGeometry.offset(player, 40.1, 0.0)).withinInteractionRange)
        assertTrue(RadarRelativePosition(40.0, 0.0, 40.0, 0.0).withinInteractionRange)
    }

    @Test fun `heading is applied once when projecting a north-up map`() {
        val north = RadarWorldGeometry.relative(origin, RadarWorldGeometry.offset(origin, 100.0, 0.0))
        assertEquals(RadarMapOffset(0.0, -100.0), north.mapOffset(0.0))
        val eastFacing = north.mapOffset(90.0)
        assertEquals(-100.0, eastFacing.xMeters, 0.001)
        assertEquals(0.0, eastFacing.yMeters, 0.001)
    }
}
