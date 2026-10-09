package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class CompassHeadingTest {
    @Test fun `low magnetic accuracy cannot provide north even at plausible strength`() {
        assertFalse(trustworthyMagneticField(1, 23f, 23f))
        assertFalse(trustworthyMagneticField(0, 23f, 23f))
        assertTrue(trustworthyMagneticField(3, 23f, 23f))
    }

    @Test fun `field strengths recorded before calibration are rejected`() {
        // A34 diagnostic: 45-105 uT before calibration, about 20-24 uT afterwards.
        for (strength in listOf(45.11f, 73f, 105.26f)) {
            assertFalse(trustworthyMagneticField(3, strength, 23f))
        }
        for (strength in listOf(20f, 23f, 24f)) {
            assertTrue(trustworthyMagneticField(3, strength, 23f))
        }
        assertFalse(trustworthyMagneticField(3, Float.NaN, 23f))
    }

    @Test fun `unknown north is not reported as zero degrees`() {
        val filter = CompassHeading()
        repeat(100) { assertNull(filter.update(null, -23f)) }
        assertEquals(180f, filter.update(203f, -23f)!!, 0.001f)
    }

    @Test fun `bad samples hold last bearing instead of attracting it to north`() {
        val filter = CompassHeading()
        filter.update(203f, -23f)
        repeat(1000) { assertEquals(180f, filter.update(null, -23f)!!, 0.001f) }
        repeat(50) { filter.update(293f, -23f) }
        assertEquals(270f, filter.update(293f, -23f)!!, 0.01f)
    }

    @Test fun `unreliable rotation readings cannot acquire or reverse the radar heading`() {
        val filter = CompassHeading()
        repeat(20) { assertNull(filter.update(203f, -23f, sampleUsable = false)) }
        assertEquals(0f, filter.update(23f, -23f, sampleUsable = true)!!, 0.001f)
        repeat(100) {
            assertEquals(0f, filter.update(203f, -23f, sampleUsable = false)!!, 0.001f)
        }
        repeat(50) { filter.update(113f, -23f, sampleUsable = true) }
        assertEquals(90f, filter.update(113f, -23f, sampleUsable = true)!!, 0.01f)
    }

    @Test fun `walking toward an upright phone bearing approaches the target in both radar modes`() {
        val origin = GeoPoint(-23.55, -46.63)
        for (yaw in 0 until 360 step 45) {
            val radians = Math.toRadians(yaw.toDouble())
            val target = RadarWorldGeometry.offset(
                origin, cos(radians) * 100.0, sin(radians) * 100.0)
            val heading = compassBearing(pose(yaw.toDouble(), 85.0))
            val camera = radarFirstPersonCamera(heading, 0f)
            var previousDistance = Double.POSITIVE_INFINITY
            var previousDepth = Double.POSITIVE_INFINITY
            for (walked in 0..60 step 10) {
                val player = RadarWorldGeometry.offset(
                    origin, cos(radians) * walked, sin(radians) * walked)
                val relative = RadarWorldGeometry.relative(player, target)
                val map = relative.mapOffset(heading.toDouble())
                val point = relative.firstPersonDisplayPoint()
                val depth = point.x * camera.targetX + point.z * camera.targetZ
                assertEquals("target stays ahead on map: yaw=$yaw", 0.0, map.xMeters, 0.01)
                assertTrue(map.yMeters < 0.0)
                assertTrue("map approaches: yaw=$yaw walked=$walked", -map.yMeters < previousDistance)
                assertTrue("first person approaches: yaw=$yaw walked=$walked", depth > 0.0 && depth < previousDepth)
                previousDistance = -map.yMeters
                previousDepth = depth
            }
        }
    }

    @Test fun `north crossing follows the short path both ways`() {
        val clockwise = CompassHeading()
        clockwise.update(359f, 0f)
        val next = clockwise.update(1f, 0f)!!
        assertTrue(next > 359f || next < 1f)
        val counterclockwise = CompassHeading()
        counterclockwise.update(1f, 0f)
        val previous = counterclockwise.update(359f, 0f)!!
        assertTrue(previous < 1f || previous > 359f)
    }

    @Test fun `complete turns cover every bearing when flat tilted or upright`() {
        for (tilt in listOf(0.0, 30.0, 44.0, 45.0, 46.0, 60.0, 89.0, 90.0)) {
            for (direction in listOf(1, -1)) {
                val filter = CompassHeading()
                for (step in 0..720) {
                    val yaw = step * direction.toDouble()
                    val target = compassDegrees(yaw.toFloat())
                    val magnetic = compassBearing(pose(yaw, tilt))
                    assertEquals("tilt=$tilt yaw=$yaw", 0f, compassDelta(target, magnetic), 0.001f)
                    val output = filter.update(magnetic, 0f)!!
                    assertTrue("turn must not get stuck: tilt=$tilt yaw=$yaw",
                        kotlin.math.abs(compassDelta(target, output)) < 3f)
                }
            }
        }
    }

    @Test fun `display rotations use the top of the displayed map`() {
        for (rotation in 0..3) {
            for (yaw in listOf(0.0, 90.0, 180.0, 270.0)) {
                val expected = compassDegrees(yaw.toFloat() - rotation * 90f)
                assertEquals(0f, compassDelta(expected, compassBearing(pose(yaw, 0.0), rotation)), 0.001f)
            }
        }
    }

    @Test fun `magnetic declination aligns north with geographic map north`() {
        val filter = CompassHeading()
        assertEquals(0f, filter.update(23f, -23f)!!, 0.001f)
        val otherHemisphere = CompassHeading()
        assertEquals(0f, otherHemisphere.update(348f, 12f)!!, 0.001f)
    }

    private fun pose(yaw: Double, tilt: Double): FloatArray {
        val h = Math.toRadians(yaw)
        val t = Math.toRadians(tilt)
        // Device-to-world basis for a physical turn about world up, with fixed tilt.
        return floatArrayOf(
            cos(h).toFloat(), (sin(h) * cos(t)).toFloat(), (-sin(h) * sin(t)).toFloat(),
            -sin(h).toFloat(), (cos(h) * cos(t)).toFloat(), (-cos(h) * sin(t)).toFloat(),
            0f, sin(t).toFloat(), cos(t).toFloat()
        )
    }
}
