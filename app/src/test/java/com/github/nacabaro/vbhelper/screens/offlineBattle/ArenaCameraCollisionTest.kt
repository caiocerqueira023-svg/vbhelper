package com.github.nacabaro.vbhelper.screens.offlineBattle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.math.hypot

class ArenaCameraCollisionTest {
    @Test
    fun leavesCameraPositionUnchangedInsideTheArena() {
        val result = constrainArenaCameraEye(8.0, -6.0, collisionRadius = 15.2)

        assertEquals(8.0, result.x, 0.0001)
        assertEquals(-6.0, result.z, 0.0001)
    }

    @Test
    fun clampsCameraToTheInnerEdgeOfTheStandsWithoutChangingItsDirection() {
        val result = constrainArenaCameraEye(24.0, 18.0, collisionRadius = 15.2)

        assertEquals(15.2, hypot(result.x, result.z), 0.0001)
        assertEquals(4.0 / 3.0, result.x / result.z, 0.0001)
    }

    @Test
    fun rejectsAnInvalidCollisionRadius() {
        assertThrows(IllegalArgumentException::class.java) {
            constrainArenaCameraEye(0.0, 0.0, collisionRadius = 0.0)
        }
    }
}
