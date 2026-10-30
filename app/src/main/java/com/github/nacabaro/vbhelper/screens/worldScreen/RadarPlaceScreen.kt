package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R

/**
 * The Radar **place**: the same composition as the old World-tab radar section, without the
 * Radar|Digifarm tabs, under a "Radar • <cardinal>" header.
 *
 * The view preferences (2D/first-person, entity labels) live in this destination's saveable state
 * so they survive a trip to another place and back, exactly like they did inside the World tab.
 */
@Composable
fun RadarPlaceScreen(
    navController: NavController,
    worldEntry: NavBackStackEntry,
    onFullScreenBattleChanged: (Boolean) -> Unit = {}
) {
    var firstPersonPreferred by rememberSaveable { mutableStateOf(false) }
    var labelsPreferred by rememberSaveable { mutableStateOf(false) }
    RadarScreen(
        navController = navController,
        worldEntry = worldEntry,
        onFullScreenBattleChanged = onFullScreenBattleChanged,
        firstPersonPreferred = firstPersonPreferred,
        onViewPreferenceChanged = { firstPersonPreferred = it },
        labelsPreferred = labelsPreferred,
        onLabelsPreferenceChanged = { labelsPreferred = it },
        titleRes = R.string.nav_radar,
        showSectionTabs = false
    )
}
