package com.tamerslab.ar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tamerslab.BuildConfig
import com.tamerslab.R
import com.tamerslab.battle.ui.offline.OfflineBattleSceneView
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Which half of the AR encounter the stage is showing. */
enum class ArStagePhase { SCANNING, BATTLE }

/**
 * Full AR stage for one radar encounter.
 *
 * Owns a single [ArCameraBackgroundView] for the whole encounter: scanning (`SCANNING`) shows the
 * camera feed with [ArPlacementOverlay] on top, the battle (`BATTLE`) shows the transparent
 * [ArBattleScene] composited over the same feed. The stage composition never recreates the camera
 * view when the phase flips, so the ARCore session, the world frame and the anchor survive from
 * placement into the fight.
 *
 * The stage root is transparent on purpose: the camera view is a `SurfaceView`/`TextureView`
 * sibling and any opaque Compose fill would hide the camera. Only small panels carry a fill.
 */
@Composable
fun ArBattleStage(
    phase: ArStagePhase,
    battle: ArBattleContent?,
    onPlacementConfirmed: (ArPlacement) -> Unit,
    onCancel: () -> Unit,
    onSceneViewReady: (OfflineBattleSceneView?) -> Unit,
    onSceneReady: () -> Unit,
    onProjectionChanged: () -> Unit,
    onFighterTapped: (String) -> Unit,
    onRendererReleased: () -> Unit,
    onRendererCreated: () -> Unit,
    onAssetError: (String) -> Unit,
    onAvailabilityChanged: (ArAvailability) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var permissionGranted by remember { mutableStateOf(hasCameraPermission(context)) }
    var permissionDenied by remember { mutableStateOf(false) }
    var permissionRequested by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf(ArAvailability.UNKNOWN) }
    var cameraView by remember { mutableStateOf<ArCameraBackgroundView?>(null) }
    var arStarted by remember { mutableStateOf(false) }
    var battleTracking by remember { mutableStateOf(ArTrackingState.TRACKING) }

    // The confirmed placement is remembered inside the stage: the shared contract has no
    // placement parameter, and the anchor must survive the SCANNING -> BATTLE phase change.
    var placement by remember { mutableStateOf<ArPlacement?>(null) }

    val latestOnCancel by rememberUpdatedState(onCancel)
    val latestOnAvailabilityChanged by rememberUpdatedState(onAvailabilityChanged)
    val latestOnPlacementConfirmed by rememberUpdatedState(onPlacementConfirmed)
    val latestOnSceneViewReady by rememberUpdatedState(onSceneViewReady)
    val latestOnSceneReady by rememberUpdatedState(onSceneReady)
    val latestOnProjectionChanged by rememberUpdatedState(onProjectionChanged)
    val latestOnFighterTapped by rememberUpdatedState(onFighterTapped)
    val latestOnRendererReleased by rememberUpdatedState(onRendererReleased)
    val latestOnRendererCreated by rememberUpdatedState(onRendererCreated)
    val latestOnAssetError by rememberUpdatedState(onAssetError)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionDenied = !granted
    }

    // Ask for the camera permission once; AR never starts before it is granted.
    LaunchedEffect(Unit) {
        if (!hasCameraPermission(context) && !permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Surface ARCore availability to the host (it decides whether to fall back to a normal battle).
    LaunchedEffect(availability) { latestOnAvailabilityChanged(availability) }

    // Start the ARCore session once the camera view exists and the permission is granted.
    LaunchedEffect(permissionGranted, cameraView, availability) {
        val view = cameraView ?: return@LaunchedEffect
        if (!permissionGranted || arStarted) return@LaunchedEffect
        if (availability == ArAvailability.UNSUPPORTED ||
            availability == ArAvailability.SUPPORTED_NOT_INSTALLED
        ) {
            return@LaunchedEffect
        }
        view.startAr()
        arStarted = true
    }

    // ARCore camera session follows the host lifecycle; release it when the stage leaves.
    // The observer closure outlives individual recompositions, so it must read the latest
    // permission/session flags instead of the values captured when the effect was created.
    val permissionGrantedState = rememberUpdatedState(permissionGranted)
    val arStartedState = rememberUpdatedState(arStarted)
    DisposableEffect(lifecycleOwner, cameraView) {
        val view = cameraView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    // Re-check ARCore on resume: the user may have installed it from the Play
                    // Store while the stage was showing the "install" card.
                    if (permissionGrantedState.value) {
                        view?.startAr()
                        arStarted = true
                        view?.resumeAr()
                    }
                }

                Lifecycle.Event.ON_PAUSE -> if (arStartedState.value) view?.pauseAr()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Re-register on every effect run: the effect re-keys when the camera view is created, and
        // the availability callback is how the stage learns the session became SUPPORTED.
        view?.setAvailabilityListener { status ->
            mainHandler.post { availability = status }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Do not release a view that is still attached: this effect can re-key while the
            // AndroidView node stays alive (e.g. the cameraView state flips null -> instance),
            // and releasing there killed the ARCore session right after creation. The view
            // releases itself from onDetachedFromWindow when the stage really leaves.
            view?.setAvailabilityListener(null)
            if (view != null && !view.isAttachedToWindow) view.releaseAr()
        }
    }

    // Keep the battle-phase tracking banner up to date without polling at frame rate.
    LaunchedEffect(phase, cameraView) {
        val view = cameraView ?: return@LaunchedEffect
        if (phase != ArStagePhase.BATTLE) return@LaunchedEffect
        while (isActive) {
            battleTracking = view.cameraSource.latestFrame()?.tracking ?: ArTrackingState.TRACKING
            delay(TRACKING_POLL_MILLIS)
        }
    }

    // ARCore can stop tracking permanently after a bad internal state; a session restart recovers
    // that. During SCANNING (no anchor exists yet) restarting is cheap, so a long PAUSED streak
    // also gets one: some Samsung/One UI sessions stay stuck in PAUSED after an ARCore update and
    // a fresh session is the only recovery. The debug readout makes the ARCore state visible
    // instead of a silently empty scanner.
    var debugStatus by remember { mutableStateOf("") }
    LaunchedEffect(phase, cameraView) {
        val view = cameraView ?: return@LaunchedEffect
        if (phase != ArStagePhase.SCANNING) return@LaunchedEffect
        var stoppedSinceNanos = 0L
        var pausedSinceNanos = 0L
        var restarts = 0
        while (isActive) {
            val snapshot = view.cameraSource.latestFrame()
            val tracking = snapshot?.tracking
            val planes = snapshot?.planes?.count {
                it.isHorizontal && it.tracking == ArTrackingState.TRACKING
            } ?: 0
            val now = System.nanoTime()
            when (tracking) {
                ArTrackingState.STOPPED -> {
                    pausedSinceNanos = 0L
                    if (stoppedSinceNanos == 0L) stoppedSinceNanos = now
                    else if (now - stoppedSinceNanos > TRACKING_RESTART_NANOS && restarts < MAX_TRACKING_RESTARTS) {
                        view.restartAr()
                        restarts++
                        stoppedSinceNanos = now
                    }
                }
                ArTrackingState.PAUSED -> {
                    stoppedSinceNanos = 0L
                    if (pausedSinceNanos == 0L) pausedSinceNanos = now
                    else if (now - pausedSinceNanos > TRACKING_PAUSE_RESTART_NANOS && restarts < MAX_TRACKING_RESTARTS) {
                        view.restartAr()
                        restarts++
                        pausedSinceNanos = now
                    }
                }
                else -> {
                    stoppedSinceNanos = 0L
                    pausedSinceNanos = 0L
                }
            }
            debugStatus = buildString {
                append("tracking=").append(tracking ?: ArTrackingState.STOPPED)
                append(" planes=").append(planes)
                snapshot?.trackingFailure?.let { append(" | ").append(it) }
                if (restarts > 0) append(" | restarts=").append(restarts)
            }
            delay(TRACKING_POLL_MILLIS)
        }
    }

    Box(modifier = modifier.fillMaxSize().testTag(TAG_STAGE)) {
        // Camera feed: always the first (bottom) child and never recreated on phase changes.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                ArCameraBackgroundView(viewContext).also { view ->
                    view.setAvailabilityListener { status ->
                        mainHandler.post { availability = status }
                    }
                    cameraView = view
                }
            },
        )

        // Everything below is transparent except the small panels, so the feed stays visible.
        when {
            !permissionGranted -> StagePanel(
                title = stringResource(R.string.ar_battle_permission_title),
                body = stringResource(
                    if (permissionDenied) {
                        R.string.ar_battle_permission_denied
                    } else {
                        R.string.ar_battle_permission_rationale
                    },
                ),
                modifier = Modifier.testTag(TAG_PERMISSION_CARD),
                actions = {
                    VitalButton(
                        onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                        modifier = Modifier.weight(1f).testTag(TAG_PERMISSION_GRANT),
                        style = VitalButtonStyle.PRIMARY,
                    ) {
                        Text(stringResource(R.string.ar_battle_permission_grant))
                    }
                    VitalButton(
                        onClick = {
                            latestOnAvailabilityChanged(ArAvailability.UNSUPPORTED)
                            latestOnCancel()
                        },
                        modifier = Modifier.weight(1f).testTag(TAG_FALLBACK),
                    ) {
                        Text(stringResource(R.string.ar_battle_fight_normally))
                    }
                },
            )

            availability == ArAvailability.UNSUPPORTED -> StagePanel(
                title = stringResource(R.string.ar_battle_unavailable_title),
                body = stringResource(R.string.ar_battle_unavailable_message),
                modifier = Modifier.testTag(TAG_UNAVAILABLE_CARD),
                actions = {
                    VitalButton(
                        onClick = {
                            latestOnAvailabilityChanged(ArAvailability.UNSUPPORTED)
                            latestOnCancel()
                        },
                        modifier = Modifier.fillMaxWidth().testTag(TAG_FALLBACK),
                        style = VitalButtonStyle.PRIMARY,
                    ) {
                        Text(stringResource(R.string.ar_battle_fight_normally))
                    }
                },
            )

            availability == ArAvailability.SUPPORTED_NOT_INSTALLED -> StagePanel(
                title = stringResource(R.string.ar_battle_install_title),
                body = stringResource(R.string.ar_battle_install_message),
                modifier = Modifier.testTag(TAG_INSTALL_CARD),
                actions = {
                    VitalButton(
                        onClick = {
                            val activity = context.findActivity()
                            val view = cameraView
                            if (activity != null && view != null) {
                                // Shows the Play Store flow when ARCore is missing; starts the
                                // session when it is already installed. The availability listener
                                // updates the stage card either way.
                                view.requestInstall(activity)
                            } else {
                                latestOnAvailabilityChanged(availability)
                                latestOnCancel()
                            }
                        },
                        modifier = Modifier.weight(1f).testTag(TAG_INSTALL),
                        style = VitalButtonStyle.PRIMARY,
                    ) {
                        Text(stringResource(R.string.ar_battle_install_action))
                    }
                    VitalButton(
                        onClick = {
                            latestOnAvailabilityChanged(availability)
                            latestOnCancel()
                        },
                        modifier = Modifier.weight(1f).testTag(TAG_FALLBACK),
                    ) {
                        Text(stringResource(R.string.ar_battle_fight_normally))
                    }
                },
            )

            phase == ArStagePhase.SCANNING -> {
                val view = cameraView
                if (view != null) {
                    ArPlacementOverlay(
                        cameraSource = view.cameraSource,
                        hitTester = view.hitTester,
                        onPlacementChanged = { /* the stage only needs the confirmed placement */ },
                        onConfirm = { confirmed ->
                            placement = confirmed
                            latestOnPlacementConfirmed(confirmed)
                        },
                        onCancel = { latestOnCancel() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (availability == ArAvailability.UNKNOWN) {
                    StageHint(
                        text = stringResource(R.string.ar_battle_starting_camera),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .testTag(TAG_STARTING_HINT),
                    )
                }
                if (BuildConfig.DEBUG && debugStatus.isNotEmpty()) {
                    StageHint(
                        text = debugStatus,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .testTag(TAG_DEBUG_STATUS),
                    )
                }
            }

            else -> {
                val view = cameraView
                val content = battle
                val anchor = placement
                if (view != null && content != null && anchor != null) {
                    ArBattleScene(
                        cameraSource = view.cameraSource,
                        placement = anchor,
                        battle = content,
                        onSceneViewReady = { latestOnSceneViewReady(it) },
                        onSceneReady = { latestOnSceneReady() },
                        onProjectionChanged = { latestOnProjectionChanged() },
                        onFighterTapped = { latestOnFighterTapped(it) },
                        onRendererReleased = { latestOnRendererReleased() },
                        onRendererCreated = { latestOnRendererCreated() },
                        onAssetError = { latestOnAssetError(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    StageHint(
                        text = stringResource(R.string.ar_battle_loading_scene),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .testTag(TAG_LOADING_HINT),
                    )
                }
                if (battleTracking != ArTrackingState.TRACKING) {
                    StageHint(
                        text = stringResource(
                            if (battleTracking == ArTrackingState.PAUSED) {
                                R.string.ar_battle_tracking_paused
                            } else {
                                R.string.ar_battle_tracking_lost
                            },
                        ),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                            .testTag(TAG_TRACKING_BANNER),
                    )
                }
                Surface(
                    color = VitalCyan.copy(alpha = 0.22f),
                    shape = CutCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .testTag(TAG_BADGE),
                ) {
                    Text(
                        text = stringResource(R.string.ar_battle_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimaryOnDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Centred translucent card used for permission/availability/install states. */
@Composable
private fun StagePanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize().background(SpaceBlack.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = SurfaceElevatedPurple.copy(alpha = 0.94f),
            shape = CutCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryOnDark,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = actions,
                )
            }
        }
    }
}

/** Small translucent pill for status copy over the camera feed. */
@Composable
private fun StageHint(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = SpaceBlack.copy(alpha = 0.55f),
        shape = CutCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimaryOnDark,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private const val TRACKING_POLL_MILLIS = 500L
private const val TRACKING_RESTART_NANOS = 7_000_000_000L
private const val TRACKING_PAUSE_RESTART_NANOS = 12_000_000_000L
private const val MAX_TRACKING_RESTARTS = 3

internal const val TAG_STAGE = "ar-battle-stage"
internal const val TAG_PERMISSION_CARD = "ar-permission-card"
internal const val TAG_PERMISSION_GRANT = "ar-permission-grant"
internal const val TAG_UNAVAILABLE_CARD = "ar-unavailable-card"
internal const val TAG_INSTALL_CARD = "ar-install-card"
internal const val TAG_INSTALL = "ar-install-action"
internal const val TAG_FALLBACK = "ar-fight-normally"
internal const val TAG_STARTING_HINT = "ar-starting-camera"
internal const val TAG_LOADING_HINT = "ar-loading-scene"
internal const val TAG_TRACKING_BANNER = "ar-tracking-banner"
internal const val TAG_BADGE = "ar-badge"
internal const val TAG_DEBUG_STATUS = "ar-debug-status"
