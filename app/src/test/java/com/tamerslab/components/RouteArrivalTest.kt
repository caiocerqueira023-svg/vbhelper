package com.tamerslab.components

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shell raises the arrival flag while a route transition is delivering a destination and lowers it
 * once the enter motion settles. Content only reads it when it first composes, so both transitions of
 * the flag have to be explicit and idempotent.
 */
class RouteArrivalTest {

    @After
    fun clearArrival() {
        RouteArrival.settle()
    }

    @Test
    fun arrivalStaysInFlightUntilTheTransitionSettles() {
        RouteArrival.settle()
        RouteArrival.markInFlight()
        assertTrue(RouteArrival.inFlight)
        RouteArrival.markInFlight()
        assertTrue(RouteArrival.inFlight)
        RouteArrival.settle()
        assertFalse(RouteArrival.inFlight)
        RouteArrival.settle()
        assertFalse(RouteArrival.inFlight)
    }
}
