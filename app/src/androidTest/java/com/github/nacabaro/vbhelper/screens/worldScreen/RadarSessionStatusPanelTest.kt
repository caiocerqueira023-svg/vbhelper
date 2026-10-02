package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RadarSessionStatusPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recoveryActionHasAnAndroidTouchTargetAndInvokesTheRetry() {
        var retries = 0
        compose.setContent {
            MaterialTheme { RadarSessionStatusPanel(RadarSurfaceState.STORAGE_FAILURE, { retries++ }) }
        }
        val button = compose.onNodeWithTag("radar-status-recovery")
        val bounds = button.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.height >= with(compose.density) { 48.dp.toPx() })
        assertTrue(bounds.width >= with(compose.density) { 48.dp.toPx() })
        button.performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun statusChangesRetainTheRecoverySlotPosition() {
        var state by mutableStateOf(RadarSurfaceState.READY)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.width(320.dp)) { RadarSessionStatusPanel(state, {}) }
            }
        }
        val original = compose.onNodeWithTag("radar-status-action-slot").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { state = RadarSurfaceState.STORAGE_FAILURE }
        val failed = compose.onNodeWithTag("radar-status-action-slot").fetchSemanticsNode().boundsInRoot
        assertEquals(original.top, failed.top, 1f)
        assertEquals(original.bottom, failed.bottom, 1f)
    }

    @Test fun unrecoverableRulesDoNotOfferADestructiveOrIneffectiveRetry() {
        compose.setContent {
            MaterialTheme { RadarSessionStatusPanel(RadarSurfaceState.UNSUPPORTED_WORLD, {}) }
        }
        compose.onNodeWithTag("radar-session-status").assertExists()
        compose.onNodeWithTag("radar-status-recovery").assertDoesNotExist()
    }
}
