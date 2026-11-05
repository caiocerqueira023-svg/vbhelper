package com.tamerslab.ar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.battle.ui.offline.OfflineBattleSceneView
import kotlin.math.roundToInt
import timber.log.Timber

/**
 * Debug-only host for the real [ArBattleStage] in its SCANNING phase, so the placement overlay,
 * camera feed and localized strings can be validated on device without starting a radar battle.
 *
 * All stage callbacks are logged and shown in a small overlay (top end). The "simulate confirm"
 * row exercises the placement-confirmed callback without needing a detected plane.
 * Debug source set only; never referenced from release code.
 */
class ArStagePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        setContent { ArStagePreviewScreen() }
    }
}

@Composable
private fun ArStagePreviewScreen() {
    var availability by remember { mutableStateOf(ArAvailability.UNKNOWN) }
    var lastPlacement by remember { mutableStateOf<ArPlacement?>(null) }
    var events by remember { mutableStateOf(emptyList<String>()) }

    fun record(event: String) {
        Timber.tag(TAG).i(event)
        events = (events + event).takeLast(6)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ArBattleStage(
            phase = ArStagePhase.SCANNING,
            battle = null,
            onPlacementConfirmed = { placement ->
                lastPlacement = placement
                record(
                    "onPlacementConfirmed t=${placement.anchor.translation.joinToString { round2(it).toString() }} " +
                        "m/unit=${round2(placement.metersPerSceneUnit)}",
                )
            },
            onCancel = { record("onCancel") },
            onSceneViewReady = { scene: OfflineBattleSceneView? ->
                record("onSceneViewReady(${if (scene == null) "null" else "view"})")
            },
            onSceneReady = { record("onSceneReady") },
            onProjectionChanged = { },
            onFighterTapped = { fighterId -> record("onFighterTapped($fighterId)") },
            onRendererReleased = { record("onRendererReleased") },
            onRendererCreated = { record("onRendererCreated") },
            onAssetError = { message -> record("onAssetError($message)") },
            onAvailabilityChanged = { status ->
                availability = status
                record("availability: $status")
            },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
                .background(Color(0xCC000000))
                .padding(8.dp),
        ) {
            Text("AR stage preview (debug)", color = Color.White, fontSize = 13.sp)
            Text("availability: $availability", color = Color.White, fontSize = 11.sp)
            val placement = lastPlacement
            Text(
                if (placement == null) {
                    "placement: none"
                } else {
                    "placement: t=${placement.anchor.translation.joinToString { round2(it).toString() }} " +
                        "m/unit=${round2(placement.metersPerSceneUnit)}"
                },
                color = Color.White,
                fontSize = 11.sp,
            )
            Text(
                "simulate confirm (tap)",
                color = Color(0xFF80D8FF),
                fontSize = 11.sp,
                modifier = Modifier
                    .clickable {
                        lastPlacement = ArPlacement(ArPose.IDENTITY, 0.05f)
                        record("simulated confirm")
                    }
                    .padding(vertical = 2.dp),
            )
            events.forEach { event ->
                Text(event, color = Color(0xFFFFE082), fontSize = 10.sp)
            }
        }
    }
}

private fun round2(value: Float): Float = (value * 100f).roundToInt() / 100f

private const val TAG = "ArStagePreview"
