package com.tamerslab.components

/**
 * Process-wide arrival signal for the shell's route transitions.
 *
 * The shell marks a route change in flight until its enter motion has settled. Content that runs its
 * own entrance (the collection grids) consults this once, at composition time, so a destination does
 * not play a second, per-item entrance on top of the page transition - which on a mid-range phone
 * meant a screenful of simultaneous alpha/scale layers during the exact frames the transition needed.
 *
 * Deliberately not Compose state: reading it never subscribes a composable and settling it never
 * causes a recomposition wave, so the only cost is the branch itself.
 */
object RouteArrival {
    @Volatile
    var inFlight: Boolean = false
        private set

    fun markInFlight() {
        inFlight = true
    }

    fun settle() {
        inFlight = false
    }
}
