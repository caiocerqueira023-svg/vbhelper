package com.tamerslab.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class ArBattleCameraTest {

    private fun assertClose(expected: Float, actual: Float, epsilon: Float = 1e-4f, message: String = "") {
        assertEquals(message, expected.toDouble(), actual.toDouble(), epsilon.toDouble())
    }

    private fun assertVector(expected: FloatArray, actual: FloatArray, epsilon: Float = 1e-4f) {
        assertEquals("vector size", expected.size, actual.size)
        expected.indices.forEach { index ->
            assertClose(expected[index], actual[index], epsilon, "component $index")
        }
    }

    private fun frame(
        pose: ArPose,
        projection: FloatArray = projectionForFov(60f),
        viewportWidth: Int = 1080,
        viewportHeight: Int = 1920,
        tracking: ArTrackingState = ArTrackingState.TRACKING,
    ): ArCameraFrame = ArCameraFrame(
        timestampNanos = 1_000_000_000L,
        tracking = tracking,
        pose = pose,
        viewMatrix = ArMath.identity(),
        projectionMatrix = projection,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        displayRotation = 0,
    )

    private fun projectionForFov(fovYDegrees: Float): FloatArray {
        val projection = FloatArray(16)
        projection[0] = 1.3f
        projection[5] = (1.0 / tan(Math.toRadians(fovYDegrees / 2.0))).toFloat()
        return projection
    }

    private fun placement(
        anchor: ArPose = ArPose.IDENTITY,
        metersPerSceneUnit: Float = 0.1f,
    ): ArPlacement = ArPlacement(anchor, metersPerSceneUnit)

    @Test
    fun `camera above and in front of an anchor maps into scene units`() {
        // Tabletop scale: 0.1 m per scene unit, so a camera 2 m up and 3 m in front of the
        // anchor lands 20 / 30 scene units away in the same direction.
        val pose = ArPose(floatArrayOf(0f, 2f, 3f), floatArrayOf(0f, 0f, 0f, 1f))
        val camera = arSceneCamera(frame(pose), placement())

        assertVector(floatArrayOf(0f, 20f, 30f), arSceneCameraEye(camera), epsilon = 1e-3f)
        // The camera sits on the anchor's +Z side, the arena yaw origin.
        assertClose(0f, arSceneCameraYaw(camera), epsilon = 1e-4f)
        assertClose(0f, arSceneCameraPitch(camera), epsilon = 1e-4f)
    }

    @Test
    fun `camera at the anchor origin yields the scene origin`() {
        val anchor = ArPose(
            floatArrayOf(1.5f, 2f, -3f),
            floatArrayOf(0f, sin(Math.PI / 8.0).toFloat(), 0f, cos(Math.PI / 8.0).toFloat()),
        )
        val camera = arSceneCamera(frame(anchor), placement(anchor))

        assertVector(floatArrayOf(0f, 0f, 0f), arSceneCameraEye(camera), epsilon = 1e-4f)
    }

    @Test
    fun `anchor translation is subtracted before scaling`() {
        val anchor = ArPose.translation(2f, 0f, 0f)
        val pose = ArPose(floatArrayOf(3f, 2f, 3f), floatArrayOf(0f, 0f, 0f, 1f))
        val camera = arSceneCamera(frame(pose), placement(anchor, metersPerSceneUnit = 0.1f))

        assertVector(floatArrayOf(10f, 20f, 30f), arSceneCameraEye(camera), epsilon = 1e-3f)
    }

    @Test
    fun `anchor rotation is undone before scaling`() {
        // The anchor faces +X (yaw 90 degrees); the camera is 3 m along world +Z from it.
        // In the anchor frame that is -3 on X, still 2 m up.
        val anchor = ArPose.yaw(floatArrayOf(0f, 0f, 0f), (Math.PI / 2.0).toFloat())
        val pose = ArPose(floatArrayOf(0f, 2f, 3f), floatArrayOf(0f, 0f, 0f, 1f))
        val camera = arSceneCamera(frame(pose), placement(anchor, metersPerSceneUnit = 0.1f))

        assertVector(floatArrayOf(-30f, 20f, 0f), arSceneCameraEye(camera), epsilon = 1e-3f)
    }

    @Test
    fun `yaw follows the arena camera convention around the Y axis`() {
        val identityRotation = floatArrayOf(0f, 0f, 0f, 1f)

        // On +Z looking at the anchor: yaw 0.
        val behind = arSceneCamera(
            frame(ArPose(floatArrayOf(0f, 0f, 3f), identityRotation)),
            placement(metersPerSceneUnit = 1f),
        )
        assertClose(0f, arSceneCameraYaw(behind), epsilon = 1e-4f)

        // On +X looking at the anchor: yaw +90 degrees.
        val right = arSceneCamera(
            frame(ArPose.yaw(floatArrayOf(3f, 0f, 0f), (Math.PI / 2.0).toFloat())),
            placement(metersPerSceneUnit = 1f),
        )
        assertClose((Math.PI / 2.0).toFloat(), arSceneCameraYaw(right), epsilon = 1e-4f)

        // On -Z looking at the anchor: yaw +/- 180 degrees.
        val front = arSceneCamera(
            frame(ArPose.yaw(floatArrayOf(0f, 0f, -3f), Math.PI.toFloat())),
            placement(metersPerSceneUnit = 1f),
        )
        assertClose(Math.PI.toFloat(), abs(arSceneCameraYaw(front)), epsilon = 1e-4f)
    }

    @Test
    fun `yaw is relative to the placement frame`() {
        // Anchor rotated 90 degrees; the camera stands on the anchor's +Z side facing it, so its
        // yaw in the anchor frame is still 0 even though its AR-world facing is 90 degrees.
        val anchor = ArPose.yaw(floatArrayOf(0f, 0f, 0f), (Math.PI / 2.0).toFloat())
        val pose = ArPose.yaw(floatArrayOf(3f, 0f, 0f), (Math.PI / 2.0).toFloat())
        val camera = arSceneCamera(frame(pose), placement(anchor, metersPerSceneUnit = 1f))

        assertClose(0f, arSceneCameraYaw(camera), epsilon = 1e-4f)
        assertVector(floatArrayOf(0f, 0f, 3f), arSceneCameraEye(camera), epsilon = 1e-4f)
    }

    @Test
    fun `rotation is preserved while translation is scaled`() {
        val pose = ArPose.yaw(floatArrayOf(0f, 2f, 3f), (Math.PI / 2.0).toFloat())
        val camera = arSceneCamera(frame(pose), placement(metersPerSceneUnit = 0.1f))

        assertVector(floatArrayOf(0f, 20f, 30f), arSceneCameraEye(camera), epsilon = 1e-3f)
        assertClose((Math.PI / 2.0).toFloat(), arSceneCameraYaw(camera), epsilon = 1e-4f)
        // Rigid transform: the rotation columns stay unit length, translation does not leak in.
        val m = camera.modelMatrix
        assertEquals(1f, kotlin.math.hypot(m[0], kotlin.math.hypot(m[1], m[2])), 1e-4f)
        assertEquals(1f, kotlin.math.hypot(m[4], kotlin.math.hypot(m[5], m[6])), 1e-4f)
        assertEquals(1f, kotlin.math.hypot(m[8], kotlin.math.hypot(m[9], m[10])), 1e-4f)
        assertEquals(0f, m[3], 1e-6f)
        assertEquals(0f, m[7], 1e-6f)
        assertEquals(0f, m[11], 1e-6f)
        assertEquals(1f, m[15], 1e-6f)
    }

    @Test
    fun `pitch is positive when the device looks down on the stage`() {
        // Rotate 30 degrees down around X: the backward axis tilts up by 30 degrees.
        val halfAngle = Math.toRadians(15.0).toFloat()
        val rotation = floatArrayOf(-sin(halfAngle), 0f, 0f, cos(halfAngle))
        val pose = ArPose(floatArrayOf(0f, 2f, 3f), rotation)
        val camera = arSceneCamera(frame(pose), placement(metersPerSceneUnit = 1f))

        assertClose((Math.toRadians(30.0)).toFloat(), arSceneCameraPitch(camera), epsilon = 1e-3f)
        assertClose(0f, arSceneCameraYaw(camera), epsilon = 1e-4f)
    }

    @Test
    fun `fov and aspect are propagated from the ARCore frame`() {
        val camera = arSceneCamera(
            frame(
                pose = ArPose(floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, 0f, 1f)),
                projection = projectionForFov(65f),
                viewportWidth = 1080,
                viewportHeight = 1920,
            ),
            placement(metersPerSceneUnit = 1f),
        )

        assertClose(65f, camera.fovYDegrees, epsilon = 1e-3f)
        assertClose(1080f / 1920f, camera.aspect, epsilon = 1e-5f)
    }

    @Test
    fun `clip planes are the placement scene clip planes and stay ordered`() {
        val (expectedNear, expectedFar) = ArMath.sceneClipPlanes(0.25f)
        val camera = arSceneCamera(
            frame(ArPose(floatArrayOf(0f, 1f, 1f), floatArrayOf(0f, 0f, 0f, 1f))),
            placement(metersPerSceneUnit = 0.25f),
        )

        assertClose(expectedNear, camera.nearSceneUnits)
        assertClose(expectedFar, camera.farSceneUnits)
        assertTrue(camera.nearSceneUnits > 0f)
        assertTrue(camera.farSceneUnits > camera.nearSceneUnits)
    }

    @Test
    fun `zero sized viewports fall back to a square aspect`() {
        val camera = arSceneCamera(
            frame(
                pose = ArPose.IDENTITY,
                viewportWidth = 0,
                viewportHeight = 0,
            ),
            placement(metersPerSceneUnit = 1f),
        )
        assertClose(1f, camera.aspect)
    }
}
