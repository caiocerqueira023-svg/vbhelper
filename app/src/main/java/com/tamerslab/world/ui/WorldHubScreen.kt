package com.tamerslab.world.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Legacy `World` route alias.
 *
 * The Radar place is the only world surface now: the old Radar|Digifarm tab row and the retired
 * multi-farm management surface are gone, so this alias simply opens the Radar world for restored
 * back stacks and external deep links.
 */
@Composable
fun WorldScreen(
    navController: NavController,
    worldEntry: NavBackStackEntry,
    onFullScreenBattleChanged: (Boolean) -> Unit = {}
) {
    RadarScreen(
        navController = navController,
        worldEntry = worldEntry,
        onFullScreenBattleChanged = onFullScreenBattleChanged
    )
}
