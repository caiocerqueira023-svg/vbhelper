package com.github.nacabaro.vbhelper.screens.homeScreens.screens

// The firmware-side vitals counter is capped at this value across the app
// (see ItemsScreenControllerImpl), so we reuse it as the gauge's max.
const val MAX_VITAL_POINTS = 9999

fun shortStageName(stage: Int): String = when (stage) {
    0 -> "Baby I"
    1 -> "Baby II"
    2 -> "Child"
    3 -> "Adult"
    4 -> "Perfect"
    5 -> "Ultimate"
    else -> "Stage $stage"
}
