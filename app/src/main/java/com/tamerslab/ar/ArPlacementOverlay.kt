package com.tamerslab.ar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.VitalOrange
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.coroutines.resume

/**
 * Hands-free AR placement.
 *
 * The player only points the phone at a table or the floor: this overlay continuously hit-tests a
 * small cross in screen space (first real planes, then ARCore depth points, then instant
 * placement), picks the scale plan automatically from the detected surface, and confirms the
 * arena as soon as the candidate pose stays put for a couple of samples. There is nothing to tap
 * or choose — the battle starts by itself once a surface is stable.
 *
 * The only control is a small Cancel action, so the player can leave the AR scanner. The overlay
 * never draws an opaque background: the camera feed lives in a sibling view below it.
 */
@Composable
fun ArPlacementOverlay(
    cameraSource: ArCameraSource,
    hitTester: ArHitTester,
    onPlacementChanged: (ArPlacement?) -> Unit,
    onConfirm: (ArPlacement) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    arenaWidthUnits: Float = ArBattleScale.RADAR_ARENA_WIDTH_UNITS,
) {
    var frame by remember { mutableStateOf<ArFrameSnapshot?>(null) }
    var candidate by remember { mutableStateOf<ArPlacement?>(null) }
    var candidateSource by remember { mutableStateOf<String?>(null) }
    var stableHits by remember { mutableIntStateOf(0) }
    var confirmed by remember { mutableStateOf(false) }
    var fallbackTriggered by remember { mutableStateOf(false) }
    var showScanHint by remember { mutableStateOf(false) }
    var scanStartedAtNanos by remember { mutableLongStateOf(0L) }

    val latestOnPlacementChanged by rememberUpdatedState(onPlacementChanged)
    val latestOnConfirm by rememberUpdatedState(onConfirm)
    val latestOnCancel by rememberUpdatedState(onCancel)

    // Frame tick: poll the latest ARCore snapshot at ~30 Hz so the plane outlines and the reticle
    // follow the camera without recomposing on every GL frame.
    LaunchedEffect(cameraSource) {
        var lastTick = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (now - lastTick >= FRAME_INTERVAL_NANOS) {
                    lastTick = now
                    frame = cameraSource.latestFrame()
                }
            }
        }
    }

    // Continuous hands-free probing. The hit test has to run on the ARCore GL thread, so each
    // sample suspends until its callback lands on the main thread.
    LaunchedEffect(cameraSource, hitTester, arenaWidthUnits) {
        if (scanStartedAtNanos == 0L) scanStartedAtNanos = System.nanoTime()
        while (isActive) {
            delay(HIT_INTERVAL_MILLIS)
            if (confirmed) continue
            val snapshot = cameraSource.latestFrame() ?: continue
            val camera = snapshot.camera ?: continue
            val viewportWidth = camera.viewportWidth
            val viewportHeight = camera.viewportHeight
            if (viewportWidth <= 0 || viewportHeight <= 0) continue
            val samples = probePoints(viewportWidth, viewportHeight)
            var accepted: ArHitTestResult? = null
            for ((x, y) in samples) {
                val hit = suspendCancellableCoroutine { continuation ->
                    hitTester.hitTest(x, y) { result ->
                        if (continuation.isActive) continuation.resume(result)
                    }
                }
                if (hit != null) {
                    accepted = hit
                    break
                }
            }
            if (accepted == null) {
                if (candidate != null) {
                    candidate = null
                    stableHits = 0
                    latestOnPlacementChanged(null)
                }
                // Never leave the player stuck in a dark/low-texture corner: after a few seconds
                // without a usable surface, place the battle in front of the camera on an
                // estimated floor/table height so the AR fight still starts. This also runs while
                // tracking is PAUSED (a dim/featureless room never reaches TRACKING), using the
                // last known camera pose; the battle then holds that pose and recovers if ARCore
                // starts tracking again.
                if (!confirmed &&
                    System.nanoTime() - scanStartedAtNanos >= FALLBACK_AFTER_NANOS
                ) {
                    val placement = ArBattleScale.manualPlacement(
                        cameraPose = camera.pose,
                        plan = ArBattleScalePlan.AUTO,
                        arenaWidthUnits = arenaWidthUnits,
                    )
                    fallbackTriggered = true
                    candidate = placement
                    confirmed = true
                    latestOnPlacementChanged(placement)
                    latestOnConfirm(placement)
                }
                continue
            }
            val hit = accepted
            val cameraPose = camera.pose
            val placement = placementFor(hit, snapshot, cameraPose, arenaWidthUnits)
            val source = hit.planeId ?: if (hit.isInstant) SOURCE_INSTANT else SOURCE_DEPTH
            val previous = candidate
            val sameSource = source == candidateSource
            val stable = sameSource && previous != null &&
                previous.anchor.translation.distanceTo(placement.anchor.translation) <= HIT_STABLE_DISTANCE_METERS
            stableHits = if (stable) stableHits + 1 else 1
            candidate = placement
            candidateSource = source
            latestOnPlacementChanged(placement)
            // Quality gates: a real plane may lock almost instantly, a depth surface needs a few
            // stable samples, and the surface-less instant placement is only a fallback that must
            // not start the battle while the player is still pointing the phone around.
            val requiredHits = when {
                hit.planeType != ArPlaneType.UNKNOWN -> 2
                hit.isInstant -> 4
                else -> 3
            }
            val minimumScanNanos = when {
                hit.planeType != ArPlaneType.UNKNOWN -> 0L
                hit.isInstant -> INSTANT_MIN_SCAN_NANOS
                else -> DEPTH_MIN_SCAN_NANOS
            }
            val scannedNanos = System.nanoTime() - scanStartedAtNanos
            if (stableHits >= requiredHits && scannedNanos >= minimumScanNanos) {
                confirmed = true
                latestOnConfirm(placement)
            }
        }
    }

    LaunchedEffect(candidate, confirmed) {
        showScanHint = false
        if (candidate == null && !confirmed) {
            delay(SCAN_HINT_DELAY_MILLIS)
            showScanHint = true
        }
    }

    val trackingState = frame?.tracking ?: ArTrackingState.TRACKING
    val panelColor = SpaceBlack.copy(alpha = 0.55f)
    val panelShape = CutCornerShape(8.dp)
    val accent = VitalCyan
    val planeFill = VitalCyan.copy(alpha = 0.07f)
    val planeStroke = VitalCyan.copy(alpha = 0.6f)
    val previewFill = VitalCyan.copy(alpha = 0.18f)
    val previewStroke = VitalCyan
    val axisColor = VitalOrange.copy(alpha = 0.95f)
    val secondaryText = TextSecondaryOnDark

    Box(modifier = modifier.fillMaxSize().testTag(TAG_OVERLAY)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawScene(
                frame = frame,
                placement = candidate,
                arenaWidthUnits = arenaWidthUnits,
                planeFill = planeFill,
                planeStroke = planeStroke,
                previewFill = previewFill,
                previewStroke = previewStroke,
                axisColor = axisColor,
                reticleColor = accent,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusPanel(panelColor = panelColor, panelShape = panelShape) {
                Text(
                    text = stringResource(
                        when {
                            confirmed && fallbackTriggered -> R.string.ar_battle_scan_fallback
                            confirmed -> R.string.ar_battle_scan_starting
                            candidate != null -> R.string.ar_battle_scan_hold
                            else -> R.string.ar_battle_scan_instruction
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryOnDark,
                )
            }
            if (!confirmed && candidate == null && showScanHint) {
                StatusPanel(panelColor = panelColor, panelShape = panelShape) {
                    Text(
                        text = stringResource(R.string.ar_battle_scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryText,
                    )
                }
            }
            if (trackingState != ArTrackingState.TRACKING) {
                StatusPanel(panelColor = panelColor, panelShape = panelShape) {
                    Text(
                        text = stringResource(
                            when {
                                trackingState == ArTrackingState.STOPPED -> R.string.ar_battle_tracking_lost
                                frame?.trackingFailureKind == ArTrackingFailure.INSUFFICIENT_LIGHT ->
                                    R.string.ar_battle_too_dark
                                frame?.trackingFailureKind == ArTrackingFailure.EXCESSIVE_MOTION ->
                                    R.string.ar_battle_move_slower
                                frame?.trackingFailureKind == ArTrackingFailure.INSUFFICIENT_FEATURES ->
                                    R.string.ar_battle_find_texture
                                else -> R.string.ar_battle_tracking_paused
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimaryOnDark,
                    )
                }
            }
        }

        TextButton(
            onClick = { latestOnCancel() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .heightIn(min = 48.dp)
                .testTag(TAG_CANCEL),
        ) {
            Text(
                text = stringResource(R.string.ui_cancel),
                color = TextPrimaryOnDark,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Candidate placement for one ARCore hit. Plane hits keep the plane orientation (so the arena
 * follows the real surface); depth/instant hits are gravity-aligned, because their hit pose is
 * camera-oriented and would otherwise leave the arena tilted/sideways.
 */
private fun placementFor(
    hit: ArHitTestResult,
    snapshot: ArFrameSnapshot,
    cameraPose: ArPose,
    arenaWidthUnits: Float,
): ArPlacement {
    val plane = hit.planeId?.let { id -> snapshot.planes.firstOrNull { it.id == id } }
    val planeSize = plane?.let { ArBattleScale.planeSizeOf(it) }
        ?: ArBattleScale.estimatedPlaneSize(hit.distanceMeters)
    val plan = ArBattleScale.recommendedPlan(planeSize)
    val metersPerSceneUnit = ArBattleScale.metersPerSceneUnit(plan, planeSize, arenaWidthUnits)
    return if (hit.planeType == ArPlaneType.UNKNOWN) {
        ArBattleScale.makeGravityAlignedPlacement(hit.pose.translation, cameraPose, metersPerSceneUnit)
    } else {
        ArBattleScale.makePlacement(hit.pose, cameraPose, metersPerSceneUnit)
    }
}

/** Screen-space probe positions in viewport pixels: centre plus a small cross around it. */
private fun probePoints(viewportWidth: Int, viewportHeight: Int): List<Pair<Float, Float>> {
    val centerX = viewportWidth * 0.5f
    val centerY = viewportHeight * 0.5f
    val dx = viewportWidth * 0.16f
    val dy = viewportHeight * 0.16f
    return listOf(
        centerX to centerY,
        centerX to centerY + dy,
        centerX to centerY - dy,
        centerX - dx to centerY,
        centerX + dx to centerY,
    )
}

private fun FloatArray.distanceTo(other: FloatArray): Float {
    val dx = this[0] - other[0]
    val dy = this[1] - other[1]
    val dz = this[2] - other[2]
    return sqrt(dx * dx + dy * dy + dz * dz)
}

@Composable
private fun StatusPanel(
    panelColor: androidx.compose.ui.graphics.Color,
    panelShape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier, color = panelColor, shape = panelShape) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) { content() }
    }
}

/**
 * Draws the detected horizontal surfaces, the centre reticle and the arena preview.
 *
 * All coordinates are projected through the latest ARCore `projection * view` matrix, then
 * scaled from the ARCore viewport to the Compose canvas size (they normally match, but the
 * scale keeps the overlay correct if the camera background reports a different frame size).
 */
private fun DrawScope.drawScene(
    frame: ArFrameSnapshot?,
    placement: ArPlacement?,
    arenaWidthUnits: Float,
    planeFill: androidx.compose.ui.graphics.Color,
    planeStroke: androidx.compose.ui.graphics.Color,
    previewFill: androidx.compose.ui.graphics.Color,
    previewStroke: androidx.compose.ui.graphics.Color,
    axisColor: androidx.compose.ui.graphics.Color,
    reticleColor: androidx.compose.ui.graphics.Color,
) {
    val snapshot = frame
    val camera = snapshot?.camera
    if (snapshot != null && camera != null) {
        val viewportWidth = camera.viewportWidth
        val viewportHeight = camera.viewportHeight
        if (viewportWidth > 0 && viewportHeight > 0) {
            val viewProjection = ArMath.multiply(camera.projectionMatrix, camera.viewMatrix)
            val scaleX = size.width / viewportWidth
            val scaleY = size.height / viewportHeight
            fun project(worldPoint: FloatArray): Offset? {
                val projected =
                    ArMath.projectToViewport(viewProjection, worldPoint, viewportWidth, viewportHeight)
                        ?: return null
                return Offset(projected[0] * scaleX, projected[1] * scaleY)
            }

            snapshot.planes.forEach { plane ->
                if (!plane.isHorizontal || plane.tracking != ArTrackingState.TRACKING) {
                    return@forEach
                }
                if (plane.polygon.size < 6) return@forEach
                val corners = ArrayList<Offset>(plane.polygon.size / 2)
                var index = 0
                var complete = true
                while (index + 1 < plane.polygon.size) {
                    val point = project(
                        ArMath.planePointToWorld(plane, plane.polygon[index], plane.polygon[index + 1]),
                    )
                    if (point == null) {
                        complete = false
                    } else {
                        corners += point
                    }
                    index += 2
                }
                if (corners.size >= 3) {
                    val path = Path()
                    path.moveTo(corners[0].x, corners[0].y)
                    for (corner in corners.drop(1)) path.lineTo(corner.x, corner.y)
                    if (complete) {
                        path.close()
                        drawPath(path, planeFill)
                    }
                    drawPath(path, planeStroke, style = Stroke(width = 1.5.dp.toPx()))
                }
            }

            placement?.let { placed ->
                drawArenaPreview(
                    anchor = placed.anchor,
                    metersPerSceneUnit = placed.metersPerSceneUnit,
                    arenaWidthUnits = arenaWidthUnits,
                    project = ::project,
                    previewFill = previewFill,
                    previewStroke = previewStroke,
                    axisColor = axisColor,
                )
            }
        }
    }

    // Centre reticle: always visible so the player knows where the scanner is probing.
    val center = Offset(size.width / 2f, size.height / 2f)
    val reticleRadius = 9.dp.toPx()
    drawCircle(reticleColor, radius = reticleRadius, center = center, style = Stroke(width = 1.5.dp.toPx()))
    drawCircle(reticleColor, radius = 2.dp.toPx(), center = center)
    val tickInner = reticleRadius + 4.dp.toPx()
    val tickOuter = reticleRadius + 10.dp.toPx()
    listOf(
        Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f),
    ).forEach { direction ->
        drawLine(
            color = reticleColor,
            start = center + direction * tickInner,
            end = center + direction * tickOuter,
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

/**
 * Projects a circle in the arena plane so the preview shows the real perspective of the battle
 * footprint, plus a forward axis at the anchor origin.
 */
private fun DrawScope.drawArenaPreview(
    anchor: ArPose,
    metersPerSceneUnit: Float,
    arenaWidthUnits: Float,
    project: (FloatArray) -> Offset?,
    previewFill: androidx.compose.ui.graphics.Color,
    previewStroke: androidx.compose.ui.graphics.Color,
    axisColor: androidx.compose.ui.graphics.Color,
) {
    val radiusMeters = (arenaWidthUnits / 2f) * metersPerSceneUnit
    if (!radiusMeters.isFinite() || radiusMeters <= 0f) return
    val segments = 48
    val points = ArrayList<Offset>(segments)
    var complete = true
    for (index in 0 until segments) {
        val angle = index * 2.0 * PI / segments
        val local = floatArrayOf(
            (radiusMeters * cos(angle)).toFloat(),
            0f,
            (radiusMeters * sin(angle)).toFloat(),
        )
        val projected = project(anchor.transformPoint(local))
        if (projected == null) {
            complete = false
        } else {
            points += projected
        }
    }
    if (points.size >= 3) {
        val ring = Path()
        ring.moveTo(points[0].x, points[0].y)
        for (point in points.drop(1)) ring.lineTo(point.x, point.y)
        if (complete) {
            ring.close()
            drawPath(ring, previewFill)
        }
        drawPath(ring, previewStroke, style = Stroke(width = 2.dp.toPx()))
    }
    val center = project(anchor.translation) ?: return
    project(anchor.transformPoint(floatArrayOf(0f, 0f, radiusMeters)))?.let { forward ->
        drawLine(axisColor, center, forward, strokeWidth = 2.5.dp.toPx())
    }
}

private const val FRAME_INTERVAL_NANOS = 33_000_000L
private const val HIT_INTERVAL_MILLIS = 140L
private const val HIT_STABLE_DISTANCE_METERS = 0.25f
private const val SCAN_HINT_DELAY_MILLIS = 2_500L
private const val FALLBACK_AFTER_NANOS = 6_000_000_000L
private const val DEPTH_MIN_SCAN_NANOS = 1_200_000_000L
private const val INSTANT_MIN_SCAN_NANOS = 2_500_000_000L
private const val SOURCE_DEPTH = "depth"
private const val SOURCE_INSTANT = "instant"

internal const val TAG_OVERLAY = "ar-placement-overlay"
internal const val TAG_CONFIRM = "ar-confirm-placement"
internal const val TAG_CANCEL = "ar-cancel-placement"
