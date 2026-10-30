package com.github.nacabaro.vbhelper.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination

/**
 * Back-stack lookups that must never throw.
 *
 * `NavController.getBackStackEntry(route)` throws `IllegalArgumentException` when the destination
 * is not on the back stack. The radar handoff is fired from screens that can be reached from more
 * than one place (wild chat, quest journal, DigiLine), so an absent entry has to degrade to a
 * navigation instead of a crash.
 */
fun NavController.getBackStackEntryOrNull(route: String): NavBackStackEntry? =
    runCatching { getBackStackEntry(route) }.getOrNull()

/**
 * The live radar destination, preferring the new `Radar` place and falling back to the legacy
 * `World` route while it is still registered.
 */
fun NavController.radarEntryOrNull(): NavBackStackEntry? =
    getBackStackEntryOrNull(NavigationItems.Radar.route)
        ?: getBackStackEntryOrNull(NavigationItems.World.route)

/**
 * Hands a payload to the radar and returns to it.
 *
 * If no radar destination is on the back stack (for example a deep link straight into
 * `WildContact/{individualId}/{cardCharacterId}`), the Radar place is pushed first so the payload
 * still has an owner.
 */
fun NavController.deliverToRadar(key: String, value: String) {
    val entry = radarEntryOrNull() ?: run {
        runCatching { navigate(NavigationItems.Radar.route) }.getOrNull()
        radarEntryOrNull()
    } ?: return
    entry.savedStateHandle[key] = value
    entry.destination.route?.let { route -> popBackStack(route, false) }
}

/** The `Items` destination with an optional `tab` query argument. */
object ItemsDestination {
    const val ARG_TAB = "tab"
    const val TAB_MINE = "mine"
    const val TAB_STORE = "store"
    const val TAB_QUEST = "quest"

    /**
     * Registered NavHost pattern. The query argument carries a default, so bare
     * `NavigationItems.Items.route` ("Items") still resolves to this destination — every
     * existing `navigate("Items")` call keeps working.
     */
    const val PATTERN = "Items?$ARG_TAB={$ARG_TAB}"

    fun route(tab: String = TAB_MINE): String =
        if (tab == TAB_MINE) NavigationItems.Items.route else "${NavigationItems.Items.route}?$ARG_TAB=$tab"

    fun tabIndex(tab: String?): Int = when (tab) {
        TAB_STORE -> 1
        TAB_QUEST -> 2
        else -> 0
    }
}

/** Opens the item hub on a specific tab while keeping the primary-tab state contract. */
fun NavController.navigateToItems(tab: String = ItemsDestination.TAB_MINE) {
    navigate(ItemsDestination.route(tab)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        // The requested tab must win over a restored "last tab" for this destination.
        restoreState = false
    }
}

/**
 * The dock place a route belongs to, for HUD selection and badges. Returns null for routes that
 * live in the overflow menu (settings, quests, transfer, scan, …) or for the home route itself.
 *
 * Intentionally independent from `topLevelRoute` in `BottomNavigationBar.kt`: that one keeps the
 * legacy bottom bar highlighted and is untouched during the transition.
 */
fun placeOf(route: String?): NavigationItems? {
    if (route == null) return null
    return when {
        route == NavigationItems.Radar.route || route == NavigationItems.World.route -> NavigationItems.Radar
        route.startsWith("WorldChat/") ||
            route.startsWith("WorldConversation/") ||
            route.startsWith("WildContact/") ||
            route == NavigationItems.WorldRecruits.route -> NavigationItems.Radar
        route == NavigationItems.Battles.route || route.startsWith("TechniqueLoadout/") -> NavigationItems.Battles
        route == NavigationItems.Digiline.route ||
            route.startsWith("FarmGroup/") ||
            route.startsWith("Chat/") -> NavigationItems.Digiline
        route == NavigationItems.Items.route ||
            route.startsWith("${NavigationItems.Items.route}?") ||
            route.startsWith("ApplyItem/") -> NavigationItems.Items
        route == NavigationItems.Storage.route ||
            route == NavigationItems.Adventure.route ||
            route == NavigationItems.Dex.route ||
            route.startsWith("Card/") ||
            route.startsWith("CardAdventure/") -> NavigationItems.Storage
        else -> null
    }
}
