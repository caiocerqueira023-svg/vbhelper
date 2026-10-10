package com.github.nacabaro.vbhelper.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.InfoStatRow
import com.github.nacabaro.vbhelper.domain.chat.ChatMessageEntity
import com.github.nacabaro.vbhelper.domain.species.SpeciesProfile
import com.github.nacabaro.vbhelper.domain.species.SpeciesSource
import com.github.nacabaro.vbhelper.navigation.BottomNavigationBar
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.navigation.VitalNavigationRail
import com.github.nacabaro.vbhelper.screens.chatScreen.ChatScreen
import com.github.nacabaro.vbhelper.screens.chatScreen.ChatScreenController
import com.github.nacabaro.vbhelper.screens.chatScreen.SpeciesContext
import com.github.nacabaro.vbhelper.screens.homeScreens.HomeSetupPanel
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageAdventureTimeDialog
import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiRefinementsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Composable private fun Themed(content: @Composable () -> Unit) {
        VBHelperTheme { Surface(color = AppTheme.VB_HELPER.palette.background, content = content) }
    }

    @Test fun checklistAdvancesFromRealReadinessAndCanBeSkipped() {
        val cards = mutableStateOf(false)
        val connection = mutableStateOf(false)
        var scans = 0
        var skips = 0
        compose.setContent { Themed {
            HomeSetupPanel(cards.value, connection.value, { cards.value = true }, { connection.value = true },
                { scans++ }, { skips++ })
        } }
        compose.onNodeWithTag("home-setup-primary").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(cards.value); assertFalse(connection.value) }
        compose.onNodeWithTag("home-setup-primary").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(connection.value) }
        compose.onNodeWithTag("home-setup-primary").assertIsDisplayed().performClick()
        compose.onNodeWithTag("home-setup-skip").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, scans); assertEquals(1, skips) }
    }

    @Test fun chatDraftSurvivesSavedStateRestoration() {
        val restore = StateRestorationTester(compose)
        restore.setContent { Themed { ChatScreen(rememberNavController(), FakeChat(), 7) } }
        compose.onNodeWithTag("chat-composer-input").performTextInput("Keep this draft")
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("chat-composer-input").assertTextContains("Keep this draft")
    }

    @Test fun chatFailurePreservesInputAndShowsRecoveryInsteadOfInternalDetails() {
        compose.setContent { Themed { ChatScreen(rememberNavController(), FakeChat(fail = true), 7) } }
        compose.onNodeWithTag("chat-composer-input").performTextInput("Keep this message")
        compose.onNodeWithTag("chat-composer-send").performScrollTo().performClick()
        compose.onNodeWithTag("chat-composer-input").assertTextContains("Keep this message")
        compose.onNodeWithText(context.getString(R.string.app_chat_failed)).assertExists()
        compose.onNodeWithText("internal implementation detail").assertDoesNotExist()
    }

    @Test fun compactNavigationExposesSelectedTabsAndLabelledOverflow() {
        compose.setContent { Themed {
            val nav = rememberNavController()
            Column {
                NavHost(nav, NavigationItems.Home.route, Modifier.weight(1f)) {
                    listOf(NavigationItems.Home, NavigationItems.Storage, NavigationItems.Dex, NavigationItems.World,
                        NavigationItems.Digiline, NavigationItems.Quests, NavigationItems.Items,
                        NavigationItems.Battles, NavigationItems.Settings).forEach { item ->
                        composable(item.route) { Text("page:${item.route}") }
                    }
                }
                BottomNavigationBar(nav)
            }
        } }
        val tab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        compose.onNode(hasText(context.getString(R.string.nav_storage)) and tab).performClick().assertIsSelected()
        compose.onNode(hasText(context.getString(R.string.nav_more)) and tab).performClick()
        compose.onNode(hasText(context.getString(R.string.nav_settings)) and hasClickAction()).performClick()
        compose.onNodeWithText("page:Settings").assertExists()
    }

    @Test fun shortRailKeepsItsLastDestinationReachable() {
        compose.setContent { Themed {
            val nav = rememberNavController()
            Row(Modifier.height(260.dp)) {
                VitalNavigationRail(nav)
                NavHost(nav, NavigationItems.Home.route, Modifier.weight(1f)) {
                    listOf(NavigationItems.Home, NavigationItems.Storage, NavigationItems.Dex, NavigationItems.World,
                        NavigationItems.Digiline, NavigationItems.Quests, NavigationItems.Items,
                        NavigationItems.Battles, NavigationItems.Settings).forEach { item ->
                        composable(item.route) { Text("page:${item.route}") }
                    }
                }
            }
        } }
        compose.onNode(hasText(context.getString(R.string.nav_settings)) and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("page:Settings").assertExists()
    }

    @Test fun smallWindowAndLargeFontsKeepEmptyStateActionsReachable() {
        var clicks = 0
        compose.setContent { Themed {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                Box(Modifier.height(240.dp)) {
                    CyberEmptyState("A long explanation about importing cards and choosing a partner.",
                        title = "Choose your partner", actionLabel = "Open Storage", onAction = { clicks++ })
                }
            }
        } }
        compose.onNodeWithText("Open Storage").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun largeFontStatLabelAndValueDoNotOverlap() {
        compose.setContent { Themed {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                InfoStatRow(R.drawable.baseline_trophy_24, "Level", "12345", Modifier.width(200.dp))
            }
        } }
        val label = compose.onNodeWithText("Level").fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("12345").fetchSemanticsNode().boundsInRoot
        assertTrue(label.bottom <= value.top)
    }

    @Test fun adventureDurationRequiresSelectionAndSurvivesRestoration() {
        val restore = StateRestorationTester(compose)
        var minutes: Long? = null
        restore.setContent { Themed { StorageAdventureTimeDialog({ minutes = it }, {}) } }
        val send = context.getString(R.string.storage_send_on_adventure)
        compose.onNodeWithText(send).assertIsNotEnabled()
        compose.onNode(hasText(context.getString(R.string.app_duration_hours, 12)) and hasClickAction()).performClick()
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(send).assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(720L, minutes) }
    }

    private class FakeChat(private val fail: Boolean = false) : ChatScreenController {
        override fun getHistory(characterId: Long): Flow<List<ChatMessageEntity>> = flowOf(emptyList())
        override fun getMood(characterId: Long) = flowOf(50)
        override fun sendMessage(characterId: Long, text: String, onResult: (Result<String>) -> Unit) {
            onResult(if (fail) Result.failure(IllegalStateException("internal implementation detail")) else Result.success("reply"))
        }
        override fun deleteFromMessage(characterId: Long, messageId: Long) {}
        override fun resendMessage(characterId: Long, messageId: Long, text: String, onResult: (Result<String>) -> Unit) = sendMessage(characterId, text, onResult)
        override fun markAssistantMessagesRead(characterId: Long) {}
        override fun getSpeciesContext(characterId: Long, onResult: (SpeciesContext) -> Unit) =
            onResult(SpeciesContext(characterId, "Fixture", SpeciesProfile(characterId, "Fixture", "Fixture", source = SpeciesSource.entries.first())))
        override fun saveManualSpeciesProfile(cardCharacterId: Long, name: String, level: String?, type: String?, profile: String?, specialMoves: List<String>, onSaved: () -> Unit) = onSaved()
    }
}
