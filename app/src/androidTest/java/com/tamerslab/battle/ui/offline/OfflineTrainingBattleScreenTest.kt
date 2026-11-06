package com.tamerslab.battle.ui.offline

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfflineTrainingBattleScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun battleSurfaceFillsTheWindowInsteadOfAFramedViewport() {
        val viewModel = OfflineBattleSessionViewModel()
        composeRule.setContent {
            MaterialTheme {
                OfflineTrainingBattleScreen(
                    viewModel = viewModel,
                    onExit = {}
                )
            }
        }

        val surface = composeRule.onNodeWithTag("offline-battle-arena-viewport")
            .fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("offline-battle-touch-deck")
            .fetchSemanticsNode().boundsInRoot

        assertEquals("The battle surface should own the window width", root.width, surface.width, 2f)
        assertEquals("The battle surface should own the window height", root.height, surface.height, 2f)
        assertTrue(
            "The command band should sit in the lower half of the window",
            deck.top >= root.height * 0.5f
        )
        assertTrue("The command band should remain inside the visible screen", deck.bottom <= root.bottom + 2f)
    }

    @Test
    fun commandBandKeepsNineStableCutCornerKeys() {
        val viewModel = OfflineBattleSessionViewModel()
        composeRule.setContent {
            MaterialTheme {
                OfflineTrainingBattleScreen(
                    viewModel = viewModel,
                    onExit = {}
                )
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("offline-battle-touch-deck")
            .fetchSemanticsNode().boundsInRoot
        val commandSlots = composeRule.onAllNodesWithTag("offline-battle-command-slot")
            .fetchSemanticsNodes()
        val minimumKeyPx = with(composeRule.density) { 48.dp.toPx() }

        assertEquals(
            "The command band should retain every command position even while loading",
            9,
            commandSlots.size
        )
        commandSlots.forEach { slot ->
            assertTrue("Every command should begin inside the band", slot.boundsInRoot.top >= deck.top)
            assertTrue("Every command should remain visible without scrolling", slot.boundsInRoot.bottom <= deck.bottom)
            assertTrue(
                "Every command key should keep a 48dp touch target",
                slot.boundsInRoot.height >= minimumKeyPx - 2f
            )
            assertTrue("Every command should stay inside the window", slot.boundsInRoot.bottom <= root.bottom + 2f)
        }
    }

    @Test
    fun radarBattleKeepsTheFullBleedSurfaceAndCommandBand() {
        val viewModel = OfflineBattleSessionViewModel()
        composeRule.setContent {
            MaterialTheme {
                WorldRadarBattleContent(
                    viewModel = viewModel,
                    battleActive = true,
                    onOutcome = {},
                    onExit = {},
                    radarViewport = {}
                )
            }
        }

        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val surface = composeRule.onNodeWithTag("world-radar-battle-viewport")
            .fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("world-radar-command-deck")
            .fetchSemanticsNode().boundsInRoot

        assertEquals("The Radar battle should stay full width", root.width, surface.width, 2f)
        assertEquals("The Radar battle should stay full height", root.height, surface.height, 2f)
        assertTrue("The Radar command band should sit below the midpoint", deck.top >= root.height * 0.5f)
        assertEquals(
            "Radar should expose the same complete command grid",
            9,
            composeRule.onAllNodesWithTag("offline-battle-command-slot").fetchSemanticsNodes().size
        )
    }
}
