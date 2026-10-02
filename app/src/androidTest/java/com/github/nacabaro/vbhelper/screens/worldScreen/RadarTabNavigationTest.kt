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
import com.github.nacabaro.vbhelper.navigation.navigatePrimary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real World/Radar composition while its saved navigation entry leaves the stack. */
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
                    composable(NavigationItems.World.route) { entry -> WorldScreen(nav, worldEntry = entry) }
                    destinations.forEach { destination ->
                        composable(destination.route) { Text("Destination: ${destination.route}") }
                    }
                }
            }
        }
        for (destination in destinations) {
            compose.runOnUiThread { nav.navigatePrimary(NavigationItems.World) }
            compose.waitForIdle()
            compose.runOnUiThread { nav.navigatePrimary(destination) }
            compose.waitForIdle()
            compose.onNodeWithText("Destination: ${destination.route}").assertExists()
            compose.runOnIdle { assertEquals(destination.route, nav.currentDestination?.route) }
        }
    }
}
