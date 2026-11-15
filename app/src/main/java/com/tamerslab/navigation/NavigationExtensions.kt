package com.tamerslab.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination

/** Back-stack lookups that must never throw. */
fun NavController.getBackStackEntryOrNull(route: AppRoute): NavBackStackEntry? =
    runCatching { getBackStackEntry(route) }.getOrNull()

/** The live radar destination, preferring `Radar` and falling back to the legacy `World` alias. */
fun NavController.radarEntryOrNull(): NavBackStackEntry? =
    getBackStackEntryOrNull(AppRoute.Radar) ?: getBackStackEntryOrNull(AppRoute.World)

/**
 * Hands a payload to the radar and returns to it. If no radar destination is on the back stack,
 * the Radar place is pushed first so the payload still has an owner.
 */
fun NavController.deliverToRadar(key: String, value: String) {
    val entry = radarEntryOrNull() ?: run {
        runCatching { navigate(AppRoute.Radar) }.getOrNull()
        radarEntryOrNull()
    } ?: return
    entry.savedStateHandle[key] = value
    if (entry.destination.hasRoute<AppRoute.Radar>()) popBackStack(AppRoute.Radar, false)
    else popBackStack(AppRoute.World, false)
}

/** The `Items` destination with an optional `tab` query argument. */
object ItemsDestination {
    const val ARG_TAB = "tab"
    const val TAB_MINE = "mine"
    const val TAB_STORE = "store"
    const val TAB_QUEST = "quest"

    fun route(tab: String = TAB_MINE): AppRoute.Items = AppRoute.Items(tab)

    fun tabIndex(tab: String?): Int = when (tab) {
        TAB_STORE -> 1
        // Battle stock moved into the owned catalogue; old quest links land on it.
        else -> 0
    }
}

/** Opens the item hub on a specific tab while keeping the primary-tab state contract. */
fun NavController.navigateToItems(tab: String = ItemsDestination.TAB_MINE) {
    navigate(ItemsDestination.route(tab)) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = false
    }
}

/** The dock place a destination belongs to, for HUD selection and badges. */
fun placeOf(destination: NavDestination?): NavigationItems? = when {
    destination == null -> null
    destination.hasRoute<AppRoute.Radar>() || destination.hasRoute<AppRoute.World>() -> NavigationItems.Radar
    destination.hasRoute<AppRoute.WorldChat>() ||
        destination.hasRoute<AppRoute.WorldConversation>() ||
        destination.hasRoute<AppRoute.WildContact>() ||
        destination.hasRoute<AppRoute.WorldRecruits>() -> NavigationItems.Radar
    destination.hasRoute<AppRoute.Battles>() || destination.hasRoute<AppRoute.TechniqueLoadout>() ->
        NavigationItems.Battles
    destination.hasRoute<AppRoute.Digiline>() ||
        destination.hasRoute<AppRoute.Chat>() -> NavigationItems.Digiline
    destination.hasRoute<AppRoute.Items>() || destination.hasRoute<AppRoute.ApplyItem>() -> NavigationItems.Items
    destination.hasRoute<AppRoute.Storage>() ||
        destination.hasRoute<AppRoute.Adventure>() ||
        destination.hasRoute<AppRoute.Dex>() ||
        destination.hasRoute<AppRoute.CardView>() ||
        destination.hasRoute<AppRoute.CardAdventure>() -> NavigationItems.Storage
    else -> null
}

