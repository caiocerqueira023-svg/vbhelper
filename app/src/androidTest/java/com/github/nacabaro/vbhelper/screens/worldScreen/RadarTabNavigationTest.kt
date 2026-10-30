package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.navigation.deliverToRadar
import com.github.nacabaro.vbhelper.navigation.navigatePrimary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real radar composition (both the new `Radar` place and the legacy `World` tab)
 * while its saved navigation entry leaves the stack. The handoff sites in wild chat / DigiLine /
 * world conversation now resolve the radar entry through `radarEntryOrNull()`, so both route ids
 * have to be able to own a saved-state payload and survive the exit transition.
 */
@RunWith(AndroidJUnit4::class)
class RadarTabNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun leavingRadarForPrimaryAndMoreDestinationsDoesNotCrashDuringTheExitTransition() {
        lateinit var nav: NavHostController
        val destinations = listOf(NavigationItems.Home, NavigationItems.Storage, NavigationItems.Dex,
            NavigationItems.Battles, NavigationItems.Settings)
        compose.setContent {
            MaterialTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = NavigationItems.Home.route,
                    enterTransition = { fadeIn(tween(280)) }, exitTransition = { fadeOut(tween(220)) }) {
                    composable(NavigationItems.Radar.route) { entry -> RadarPlaceScreen(nav, worldEntry = entry) }
                    composable(NavigationItems.World.route) { entry -> WorldScreen(nav, worldEntry = entry) }
                    destinations.forEach { destination ->
                        composable(destination.route) { Text("Destination: ${destination.route}") }
                    }
                }
            }
        }
        for (radar in listOf(NavigationItems.Radar, NavigationItems.World)) {
            for (destination in destinations) {
                compose.runOnUiThread { nav.navigatePrimary(radar) }
                compose.waitForIdle()
                compose.runOnIdle { assertEquals(radar.route, nav.currentDestination?.route) }
                compose.runOnUiThread { nav.navigatePrimary(destination) }
                compose.waitForIdle()
                compose.onNodeWithText("Destination: ${destination.route}").assertExists()
                compose.runOnIdle { assertEquals(destination.route, nav.currentDestination?.route) }
            }
        }
    }

    @Test fun radarSavedStatePayloadIsDeliveredToTheLiveRadarEntry() {
        lateinit var nav: NavHostController
        compose.setContent {
            MaterialTheme {
                nav = rememberNavController()
                NavHost(nav, startDestination = NavigationItems.Home.route) {
                    composable(NavigationItems.Radar.route) { Text("Radar") }
                    composable(NavigationItems.World.route) { Text("World") }
                    composable(NavigationItems.Home.route) { Text("Home") }
                }
            }
        }
        compose.runOnUiThread { nav.navigatePrimary(NavigationItems.Radar) }
        compose.waitForIdle()
        compose.runOnUiThread { nav.deliverToRadar("radar-challenge", "payload-1") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(NavigationItems.Radar.route, nav.currentDestination?.route)
            assertEquals(
                "payload-1",
                nav.getBackStackEntry(NavigationItems.Radar.route).savedStateHandle.get<String>("radar-challenge")
            )
        }
    }
}
