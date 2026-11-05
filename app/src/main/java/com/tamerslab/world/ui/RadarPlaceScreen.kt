package com.tamerslab.world.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * The Radar **place**: the full-bleed isometric radar world with its floating chrome, without the
 * legacy Radar|Digifarm tabs.
 *
 * View preferences live in this destination's saveable state so they survive a trip to another place
 * and back: the Debug-only legacy renderer choice, entity labels and the AR battle preference.
 */
@Composable
fun RadarPlaceScreen(
    navController: NavController,
    worldEntry: NavBackStackEntry,
    onFullScreenBattleChanged: (Boolean) -> Unit = {}
) {
    var legacyViewName by rememberSaveable { mutableStateOf(RadarLegacyView.NONE.name) }
    var labelsPreferred by rememberSaveable { mutableStateOf(false) }
    var arPreferred by rememberSaveable { mutableStateOf(false) }
    RadarScreen(
        navController = navController,
        worldEntry = worldEntry,
        onFullScreenBattleChanged = onFullScreenBattleChanged,
        legacyView = runCatching { RadarLegacyView.valueOf(legacyViewName) }.getOrDefault(RadarLegacyView.NONE),
        onLegacyViewChanged = { legacyViewName = it.name },
        labelsPreferred = labelsPreferred,
        onLabelsPreferenceChanged = { labelsPreferred = it },
        arPreferred = arPreferred,
        onArPreferenceChanged = { arPreferred = it },
        showSectionTabs = false,
    )
}
