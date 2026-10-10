package com.github.nacabaro.vbhelper.screens.homeScreens

enum class HomeReadiness { LOADING, SETUP, EMPTY, CHOOSE_PARTNER, PROFILE_UNAVAILABLE, READY }

fun homeReadiness(
    storedCount: Int?,
    cardCount: Int?,
    secretsReady: Boolean?,
    hasActivePartner: Boolean,
    profileReady: Boolean,
): HomeReadiness = when {
    storedCount == null -> HomeReadiness.LOADING
    hasActivePartner -> if (profileReady) HomeReadiness.READY else HomeReadiness.PROFILE_UNAVAILABLE
    storedCount > 0 -> HomeReadiness.CHOOSE_PARTNER
    cardCount == null || secretsReady == null -> HomeReadiness.LOADING
    cardCount == 0 || !secretsReady -> HomeReadiness.SETUP
    else -> HomeReadiness.EMPTY
}
