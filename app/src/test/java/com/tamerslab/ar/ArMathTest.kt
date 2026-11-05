package com.tamerslab.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

class ArMathTest {

    private fun assertMatEquals(expected: FloatArray, actual: FloatArray, epsilon: Float = 1e-4f) {
        assertEquals("matrix size", expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(
                "index $index",
                expected[index].toDouble(),
                actual[index].toDouble(),
                epsilon.toDouble(),
            )
        }
    }

    @Test
    fun `identity pose produces the identity matrix`() {
        assertMatEquals(ArMath.identity(), ArPose.IDENTITY.matrix16())
    }

    @Test
    fun `yaw of 90 degrees turns local plus Z into world plus X`() {
        val pose = ArPose.yaw(floatArrayOf(0f, 0f, 0f), (Math.PI / 2.0).toFloat())
        val transformed = pose.transformPoint(floatArrayOf(0f, 0f, 1f))
        assertEquals(1f, transformed[0], 1e-4f)
        assertEquals(0f, transformed[1], 1e-4f)
        assertEquals(0f, transformed[2], 1e-4f)
    }

    @Test
    fun `pose inverse undoes the pose`() {
        val pose = ArPose(
            floatArrayOf(1.5f, 0.4f, -2f),
            floatArrayOf(0f, 0.3826834f, 0f, 0.9238795f), // 45 degrees around Y
        )
        val composed = pose.compose(pose.inverse())
        assertMatEquals(ArMath.identity(), composed.matrix16())
    }

    @Test
    fun `compose applies the other pose first`() {
        val a = ArPose.translation(0f, 0f, 2f)
        val b = ArPose.translation(1f, 0f, 0f)
        val point = a.compose(b).transformPoint(floatArrayOf(0f, 0f, 0f))
        assertEquals(1f, point[0], 1e-4f)
        assertEquals(0f, point[1], 1e-4f)
        assertEquals(2f, point[2], 1e-4f)
    }

    @Test
    fun `vertical fov is recovered from a projection matrix`() {
        val fovY = 60f
        val projection = FloatArray(16)
        projection[0] = 1.3f
        projection[5] = (1.0 / tan(Math.toRadians(fovY / 2.0))).toFloat()
        assertEquals(fovY, ArMath.fovYFromProjection(projection), 1e-3f)
    }

    @Test
    fun `fov fallback is finite for a broken projection`() {
        val projection = FloatArray(16)
        val fov = ArMath.fovYFromProjection(projection)
        assertEquals(60f, fov, 1e-3f)
    }

    @Test
    fun `world point at the centre of the view projects to the centre of the viewport`() {
        val projection = FloatArray(16)
        projection[0] = 1.3f
        projection[5] = 1.7f
        projection[10] = -1.0004f
        projection[11] = -1f
        projection[14] = -0.20004f
        projection[15] = 0f
        val identityView = ArMath.identity()
        val viewProjection = ArMath.multiply(projection, identityView)
        val point = ArMath.projectToViewport(viewProjection, floatArrayOf(0f, 0f, -2f), 400, 400)
        assertNotNull(point)
        assertEquals(200f, point!![0], 1e-2f)
        assertEquals(200f, point[1], 1e-2f)
    }

    @Test
    fun `point behind the camera is culled`() {
        val projection = FloatArray(16)
        projection[0] = 1.3f
        projection[5] = 1.7f
        projection[10] = -1.0004f
        projection[11] = -1f
        projection[14] = -0.2f
        val viewProjection = ArMath.multiply(projection, ArMath.identity())
        assertNull(ArMath.projectToViewport(viewProjection, floatArrayOf(0f, 0f, 2f), 400, 400))
    }

    @Test
    fun `scene clip planes are expressed in scene units and stay positive`() {
        // A tabletop world (5 cm per scene unit) needs a near plane of 1 scene unit (5 cm) and a
        // far plane of 2400 units (120 m); a park world (50 cm per unit) uses 10x smaller values
        // in scene units for the same real-world distances.
        val tabletop = ArMath.sceneClipPlanes(0.05f)
        val park = ArMath.sceneClipPlanes(0.5f)
        assertEquals(1f, tabletop.first, 1e-4f)
        assertEquals(0.1f, park.first, 1e-4f)
        assertEquals(2400f, tabletop.second, 1e-2f)
        assertEquals(240f, park.second, 1e-2f)
        assertEquals(true, tabletop.first > 0f)
        assertEquals(true, tabletop.second > tabletop.first)
        assertEquals(true, park.second > park.first)
    }

    @Test
    fun `plane local point is transformed into world space`() {
        val plane = ArPlaneSnapshot(
            id = "plane-1",
            type = ArPlaneType.HORIZONTAL_UPWARD,
            tracking = ArTrackingState.TRACKING,
            centerPose = ArPose.yaw(floatArrayOf(2f, 0.5f, 3f), 0f),
            extentX = 1f,
            extentZ = 2f,
            polygon = floatArrayOf(-0.5f, -1f, 0.5f, -1f, 0.5f, 1f, -0.5f, 1f),
        )
        val world = ArMath.planePointToWorld(plane, 1f, 2f)
        assertEquals(3f, world[0], 1e-4f)
        assertEquals(0.5f, world[1], 1e-4f)
        assertEquals(5f, world[2], 1e-4f)
    }

    @Test
    fun `projection aspect matches the viewport`() {
        assertEquals(2f, ArMath.aspectRatio(400, 200), 1e-4f)
        assertEquals(1f, ArMath.aspectRatio(400, 0), 1e-4f)
        assertEquals(true, abs(ArMath.tanHalfFovY(90f) - 1f) < 1e-4f)
    }
}
