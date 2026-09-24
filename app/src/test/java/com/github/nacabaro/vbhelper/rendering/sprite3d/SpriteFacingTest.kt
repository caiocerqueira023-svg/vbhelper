package com.github.nacabaro.vbhelper.rendering.sprite3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class SpriteFacingTest {
    @Test
    fun cameraAssistanceLimitsTheVisibleProfileToFortyDegrees() {
        val fortyDegrees = Math.toRadians(40.0).toFloat()
        val yaw = cameraAssistedSpriteYaw(
            worldHeadingYaw = 0f,
            cameraYaw = (PI / 3.0).toFloat(),
            maxRelativeYaw = fortyDegrees,
        )

        assertEquals(Math.toRadians(20.0).toFloat(), yaw, 0.0001f)
    }

    @Test
    fun oppositeTravelDirectionKeepsEquivalentFlatPlane() {
        val yaw = cameraAssistedSpriteYaw(
            worldHeadingYaw = PI.toFloat(),
            cameraYaw = 0f,
        )

        assertEquals(0f, yaw, 0.0001f)
    }

    @Test
    fun rotatingPastAProfileViewFlipsTheSpriteAbruptly() {
        val before = cameraAssistedSpriteYaw(
            worldHeadingYaw = 0f,
            cameraYaw = (PI / 2.0 - 0.01).toFloat(),
        )
        val after = cameraAssistedSpriteYaw(
            worldHeadingYaw = 0f,
            cameraYaw = (PI / 2.0 + 0.01).toFloat(),
        )

        assertTrue(abs(after - before) > 1.3f)
    }
}
