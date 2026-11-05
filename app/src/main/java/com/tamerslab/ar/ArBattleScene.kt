package com.tamerslab.ar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.tamerslab.battle.offline.core.BattleSnapshot
import com.tamerslab.battle.ui.offline.BattleFighterPresentation
import com.tamerslab.battle.ui.offline.OfflineArenaManifest
import com.tamerslab.battle.ui.offline.OfflineBattleScene
import com.tamerslab.battle.ui.offline.OfflineBattleSceneView
import kotlinx.coroutines.flow.StateFlow

/**
 * AR presentation state for the battle scene: where the arena is anchored in the physical world
 * and where the live camera frames come from. Both are owned by the AR session layer; the scene
 * only reads them.
 */
data class ArBattlePresentation(
    val cameraSource: ArCameraSource,
    val placement: ArPlacement,
)

/**
 * Everything the AR battle overlay needs from the offline battle state.
 *
 * Mirrors the parameters of [OfflineBattleScene] so the session/placement layer can hand the
 * battle state across without knowing about the renderer.
 */
data class ArBattleContent(
    val snapshot: BattleSnapshot?,
    val fighters: Map<String, BattleFighterPresentation>,
    val manifest: OfflineArenaManifest?,
    val sessionId: String?,
    val preparedForms: Map<String, BattleFighterPresentation>,
    val allowMotion: Boolean,
    val sceneSnapshotFlow: StateFlow<BattleSnapshot?>?,
)

/**
 * Transparent Filament battle rendered over the AR camera feed.
 *
 * This composable only renders the arena/fighters as an overlay: the camera background view and
 * the placement UI belong to the AR stage that hosts it. It reuses the existing offline battle
 * scene (same engine, same battle state plumbing), switching it to the AR presentation where the
 * Filament camera follows the device pose and the backdrop is transparent.
 *
 * Callbacks are kept in [rememberUpdatedState] so the `AndroidView` factory (which only runs once)
 * never captures a stale lambda when the host recomposes.
 */
@Composable
fun ArBattleScene(
    cameraSource: ArCameraSource,
    placement: ArPlacement,
    battle: ArBattleContent,
    onSceneViewReady: (OfflineBattleSceneView?) -> Unit,
    onSceneReady: () -> Unit,
    onProjectionChanged: () -> Unit,
    onFighterTapped: (String) -> Unit,
    onRendererReleased: () -> Unit,
    onRendererCreated: () -> Unit,
    onAssetError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Stable AR presentation; a new instance would only be needed when the anchor/source change.
    val presentation = remember(cameraSource, placement) {
        ArBattlePresentation(cameraSource = cameraSource, placement = placement)
    }
    val latestOnSceneViewReady = rememberUpdatedState(onSceneViewReady)
    val latestOnSceneReady = rememberUpdatedState(onSceneReady)
    val latestOnProjectionChanged = rememberUpdatedState(onProjectionChanged)
    val latestOnFighterTapped = rememberUpdatedState(onFighterTapped)
    val latestOnRendererReleased = rememberUpdatedState(onRendererReleased)
    val latestOnRendererCreated = rememberUpdatedState(onRendererCreated)
    val latestOnAssetError = rememberUpdatedState(onAssetError)
    val sceneViewReadyCallback = remember {
        { view: OfflineBattleSceneView? -> latestOnSceneViewReady.value(view) }
    }
    val sceneReadyCallback = remember { { latestOnSceneReady.value() } }
    val projectionChangedCallback = remember { { latestOnProjectionChanged.value() } }
    val fighterTappedCallback = remember { { fighterId: String -> latestOnFighterTapped.value(fighterId) } }
    val rendererReleasedCallback = remember { { latestOnRendererReleased.value() } }
    val rendererCreatedCallback = remember { { latestOnRendererCreated.value() } }
    val assetErrorCallback = remember { { message: String -> latestOnAssetError.value(message) } }

    Box(modifier = modifier) {
        OfflineBattleScene(
            snapshot = battle.snapshot,
            fighters = battle.fighters,
            manifest = battle.manifest,
            sessionId = battle.sessionId,
            onReady = sceneViewReadyCallback,
            onSceneReady = sceneReadyCallback,
            onProjectionChanged = projectionChangedCallback,
            onFighterTapped = fighterTappedCallback,
            onAssetError = assetErrorCallback,
            modifier = Modifier.fillMaxSize(),
            onReleased = rendererReleasedCallback,
            onCreated = rendererCreatedCallback,
            preparedForms = battle.preparedForms,
            allowMotion = battle.allowMotion,
            sceneSnapshotFlow = battle.sceneSnapshotFlow,
            arPresentation = presentation,
        )
    }
}
