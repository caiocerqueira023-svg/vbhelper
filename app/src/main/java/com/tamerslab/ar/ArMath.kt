package com.tamerslab.ar

import kotlin.math.atan
import kotlin.math.tan

/**
 * Pure maths shared by the AR stage overlay and the battle scene camera.
 *
 * Deliberately avoids `android.opengl.Matrix` so it runs in plain JVM unit tests.
 * All matrices are column-major 4x4 arrays, matching Android/Filament conventions.
 */
object ArMath {

    /** Identity 4x4. */
    fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    /** Column-major `a * b` (applies b first, then a — the usual matrix convention). */
    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == 16 && b.size == 16)
        val out = FloatArray(16)
        for (column in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[column * 4 + k]
                }
                out[column * 4 + row] = sum
            }
        }
        return out
    }

    /** Multiplies a column-major 4x4 by a 4-component vector. */
    fun multiplyVector(m: FloatArray, v: FloatArray): FloatArray {
        require(m.size == 16 && v.size == 4)
        return floatArrayOf(
            m[0] * v[0] + m[4] * v[1] + m[8] * v[2] + m[12] * v[3],
            m[1] * v[0] + m[5] * v[1] + m[9] * v[2] + m[13] * v[3],
            m[2] * v[0] + m[6] * v[1] + m[10] * v[2] + m[14] * v[3],
            m[3] * v[0] + m[7] * v[1] + m[11] * v[2] + m[15] * v[3],
        )
    }

    /** Vertical field of view in degrees, extracted from a standard projection matrix. */
    fun fovYFromProjection(projection: FloatArray): Float {
        require(projection.size == 16)
        val p5 = projection[5]
        if (!p5.isFinite() || p5 <= 0f) return 60f
        return Math.toDegrees(2.0 * atan(1.0 / p5.toDouble())).toFloat()
    }

    /** Horizontal field of view in degrees (kept for diagnostics/tests). */
    fun fovXFromProjection(projection: FloatArray): Float {
        require(projection.size == 16)
        val p0 = projection[0]
        if (!p0.isFinite() || p0 <= 0f) return 60f
        return Math.toDegrees(2.0 * atan(1.0 / p0.toDouble())).toFloat()
    }

    /**
     * Projects an AR-world point through a world->clip matrix into viewport pixels.
     * Returns `null` when the point is behind the camera or outside the (slightly padded)
     * viewport, matching how HUD annotations are culled elsewhere in the app.
     */
    fun projectToViewport(
        viewProjection: FloatArray,
        worldPoint: FloatArray,
        viewportWidth: Int,
        viewportHeight: Int,
        padding: Float = 0.05f,
    ): FloatArray? {
        require(worldPoint.size == 3)
        if (viewportWidth <= 0 || viewportHeight <= 0) return null
        val clip = multiplyVector(
            viewProjection,
            floatArrayOf(worldPoint[0], worldPoint[1], worldPoint[2], 1f),
        )
        if (clip[3] <= 0f) return null
        val ndcX = clip[0] / clip[3]
        val ndcY = clip[1] / clip[3]
        if (ndcX < -1f - padding || ndcX > 1f + padding) return null
        if (ndcY < -1f - padding || ndcY > 1f + padding) return null
        return floatArrayOf(
            (ndcX * 0.5f + 0.5f) * viewportWidth,
            (1f - (ndcY * 0.5f + 0.5f)) * viewportHeight,
        )
    }

    /** World point of a plane-local position (`x`, `z` in plane space, `y = 0`). */
    fun planePointToWorld(plane: ArPlaneSnapshot, localX: Float, localZ: Float): FloatArray =
        plane.centerPose.transformPoint(floatArrayOf(localX, 0f, localZ))

    /**
     * Converts a real-world distance in metres to Filament camera near/far planes expressed in
     * arena scene units. Small dioramas need a small near plane; the far plane has to reach the
     * whole play space plus the skybox-free background.
     */
    fun sceneClipPlanes(metersPerSceneUnit: Float): Pair<Float, Float> {
        require(metersPerSceneUnit > 0f)
        val nearScene = (0.05f / metersPerSceneUnit).coerceIn(0.01f, 4f)
        val farScene = (120f / metersPerSceneUnit).coerceAtLeast(nearScene * 100f)
        return nearScene to farScene
    }

    /** Perspective aspect of the camera background: horizontal FOV / vertical FOV relation. */
    fun aspectRatio(width: Int, height: Int): Float =
        if (height <= 0) 1f else width.toFloat() / height.toFloat()

    /** Tangent of half vertical FOV, useful for aspect-correct point projection in tests. */
    fun tanHalfFovY(fovYDegrees: Float): Float = tan(Math.toRadians(fovYDegrees.toDouble() / 2.0)).toFloat()
}
