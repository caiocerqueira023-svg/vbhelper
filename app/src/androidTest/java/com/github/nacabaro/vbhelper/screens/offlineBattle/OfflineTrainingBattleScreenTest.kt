package com.github.nacabaro.vbhelper.screens.offlineBattle

import androidx.compose.material3.MaterialTheme
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
    fun arenaIsSquareAndCameraControlSitsOutsideTheViewport() {
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

        assertEquals("Battle viewport should use the Digifarm's square framing", arena.width, arena.height, 1.5f)
        assertTrue(
            "Battle viewport should fill the screen width available to Radar and Digifarm",
            arena.width >= root.width - with(composeRule.density) { 32.dp.toPx() } - 2f
        )
        assertTrue("Camera control should remain in the battle toolbar", cameraControl.bottom <= arena.top)
    }
}
