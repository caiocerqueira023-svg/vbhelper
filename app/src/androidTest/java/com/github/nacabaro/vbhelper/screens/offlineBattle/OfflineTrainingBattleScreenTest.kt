package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
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
    fun arenaAndCommandDeckShareTheVisiblePhoneSurface() {
        composeRule.setContent {
            MaterialTheme {
                OfflineTrainingBattleScreen(
                    viewModel = OfflineBattleSessionViewModel(),
                    onExit = {}
                )
            }
        }

        val arena = composeRule.onNodeWithTag("offline-battle-arena-viewport")
            .fetchSemanticsNode().boundsInRoot
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val cameraControl = composeRule.onNodeWithTag("offline-battle-recenter-camera")
            .fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("offline-battle-touch-deck")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Battle viewport should fill the phone width",
            arena.width >= root.width - with(composeRule.density) { 32.dp.toPx() } - 2f
        )
        assertTrue("Camera control should remain in the battle toolbar", cameraControl.bottom <= arena.top)
        assertTrue(
            "The command deck should breathe below the arena",
            deck.top - arena.bottom >= with(composeRule.density) { 8.dp.toPx() }
        )
        assertTrue("The command deck should remain inside the visible screen", deck.bottom <= root.bottom + 2f)
        assertTrue("Both Decode-inspired screens should receive meaningful height", arena.height > root.height * 0.25f)
        assertTrue("Both Decode-inspired screens should receive meaningful height", deck.height > root.height * 0.25f)
    }

    @Test
    fun touchDeckKeepsNineCommandSlotsInAStableLowerRegion() {
        composeRule.setContent {
            MaterialTheme {
                OfflineTrainingBattleScreen(
                    viewModel = OfflineBattleSessionViewModel(),
                    onExit = {}
                )
            }
        }

        val arena = composeRule.onNodeWithTag("offline-battle-arena-viewport")
            .fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("offline-battle-touch-deck")
            .fetchSemanticsNode().boundsInRoot
        val commandSlots = composeRule.onAllNodesWithTag("offline-battle-command-slot")
            .fetchSemanticsNodes()

        assertTrue("The command deck should read as the lower touch screen", deck.top >= arena.bottom)
        assertEquals(
            "The touch deck should retain every command position even while loading",
            9,
            commandSlots.size
        )
        commandSlots.forEach { slot ->
            assertTrue("Every command should begin inside the touch deck", slot.boundsInRoot.top >= deck.top)
            assertTrue("Every command should remain visible without scrolling", slot.boundsInRoot.bottom <= deck.bottom)
        }
    }

    @Test
    fun radarBattleUsesTheSameSeparatedNineCommandDeck() {
        composeRule.setContent {
            MaterialTheme {
                WorldRadarBattleContent(
                    viewModel = OfflineBattleSessionViewModel(),
                    battleActive = true,
                    onOutcome = {},
                    onExit = {},
                    radarViewport = {},
                    radarControls = {}
                )
            }
        }

        val viewport = composeRule.onNodeWithTag("world-radar-battle-viewport")
            .fetchSemanticsNode().boundsInRoot
        val deck = composeRule.onNodeWithTag("world-radar-command-deck")
            .fetchSemanticsNode().boundsInRoot

        assertTrue(
            "Radar commands should breathe below the viewport",
            deck.top - viewport.bottom >= with(composeRule.density) { 8.dp.toPx() }
        )
        assertEquals(
            "Radar should expose the same complete command grid",
            9,
            composeRule.onAllNodesWithTag("offline-battle-command-slot").fetchSemanticsNodes().size
        )
    }
}
