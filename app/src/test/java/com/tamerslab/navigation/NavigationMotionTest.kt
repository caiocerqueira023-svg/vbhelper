package com.tamerslab.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Home is a world, not a page: the shell gives it its own motion in both directions and never lets it
 * ride the sideways tab slide. These cases pin that contract so the transitions cannot quietly drift
 * back to a plain crossfade when the destination list changes.
 */
class NavigationMotionTest {

    @Test
    fun homeNeverRidesTheTabSlide() {
        for (direction in -1..1) {
            assertEquals(
                RouteEnterMotion.PlaceOverFarm,
                routeEnterMotion(
                    initialIsHome = true,
                    targetIsHome = false,
                    direction = direction,
                    motionAllowed = true,
                ),
            )
            assertEquals(
                RouteExitMotion.HomeLeave,
                routeExitMotion(
                    initialIsHome = true,
                    targetIsHome = false,
                    direction = direction,
                    motionAllowed = true,
                ),
            )
            assertEquals(
                RouteEnterMotion.HomeReturn,
                routeEnterMotion(
                    initialIsHome = false,
                    targetIsHome = true,
                    direction = direction,
                    motionAllowed = true,
                ),
            )
            assertEquals(
                RouteExitMotion.PlaceSink,
                routeExitMotion(
                    initialIsHome = false,
                    targetIsHome = true,
                    direction = direction,
                    motionAllowed = true,
                ),
            )
        }
    }

    @Test
    fun placeToPlaceSlidesOnlyAlongThePrimaryRow() {
        assertEquals(
            RouteEnterMotion.TabSlide,
            routeEnterMotion(initialIsHome = false, targetIsHome = false, direction = 1, motionAllowed = true),
        )
        assertEquals(
            RouteExitMotion.TabSlide,
            routeExitMotion(initialIsHome = false, targetIsHome = false, direction = -1, motionAllowed = true),
        )
        assertEquals(
            RouteEnterMotion.Fade,
            routeEnterMotion(initialIsHome = false, targetIsHome = false, direction = 0, motionAllowed = true),
        )
        assertEquals(
            RouteExitMotion.Fade,
            routeExitMotion(initialIsHome = false, targetIsHome = false, direction = 0, motionAllowed = true),
        )
    }

    @Test
    fun reducedMotionFallsBackToCrossfadesEverywhere() {
        for (initialIsHome in listOf(false, true)) {
            for (targetIsHome in listOf(false, true)) {
                for (direction in -1..1) {
                    assertEquals(
                        RouteEnterMotion.Fade,
                        routeEnterMotion(initialIsHome, targetIsHome, direction, motionAllowed = false),
                    )
                    assertEquals(
                        RouteExitMotion.Fade,
                        routeExitMotion(initialIsHome, targetIsHome, direction, motionAllowed = false),
                    )
                }
            }
        }
    }
}
