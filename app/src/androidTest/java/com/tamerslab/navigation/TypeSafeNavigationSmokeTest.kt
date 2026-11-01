package com.tamerslab.navigation

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TypeSafeNavigationSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typedArgumentsRoundTripThroughRealNavHost() {
        lateinit var nav: androidx.navigation.NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = AppRoute.Home) {
                composable<AppRoute.Home> { Text("home") }
                composable<AppRoute.Chat> { entry ->
                    Text("chat:${entry.toRoute<AppRoute.Chat>().characterId}")
                }
                composable<AppRoute.Transfer> { entry ->
                    val route = entry.toRoute<AppRoute.Transfer>()
                    Text("transfer:${route.section}:${route.characterId}")
                }
                composable<AppRoute.WildContact> { entry ->
                    val route = entry.toRoute<AppRoute.WildContact>()
                    Text("wild:${route.individualId}:${route.cardCharacterId}")
                }
            }
        }
        compose.runOnUiThread { nav.navigate(AppRoute.Chat(17)) }
        compose.onNodeWithText("chat:17").assertExists()
        compose.runOnUiThread { nav.navigate(AppRoute.Transfer("devices", 42, null)) }
        compose.onNodeWithText("transfer:devices:42").assertExists()
        compose.runOnUiThread { nav.navigate(AppRoute.WildContact("individual:7", 9)) }
        compose.onNodeWithText("wild:individual:7:9").assertExists()
    }
}
