package com.tamerslab.world.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.components.LabeledSwitchRow
import com.tamerslab.home.ui.PartnerSlabAlpha
import com.tamerslab.ui.theme.SpaceBlack
import com.tamerslab.ui.theme.StatusRed
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Floating chrome for the Radar place: home HUD ground rules over a full-bleed isometric radar
 * world, and the storage instrument vocabulary (cut-corner 48dp keys, cyan = live/selected, HUD ink
 * with a soft shadow) instead of the old page frame and its permanent control stack.
 *
 * Nothing here wraps the world: the top cluster and the bottom signal band are translucent grounds
 * painted in the partner slab's own surface tone, the compass layer draws straight onto the 3D
 * plate, and the options live in one bottom sheet. Every composable is stateless and hoisted so
 * `RadarScreen` owns the state; each one consumes exactly the system inset it owns - the top cluster
 * consumes the status bar, the bottom band the navigation bar, and nothing consumes both.
 */

/** The three Radar renderers. The isometric world is the default; the others are a Debug instrument. */
enum class RadarLegacyView { NONE, CLASSIC_2D, FIRST_PERSON }

/**
 * One range ring of the radar compass.
 *
 * [fraction] is measured in **half the shorter viewport edge** from the viewport centre: 1f is the
 * edge of the shorter side. The Lead computes the fraction from the renderer's own radial mapping;
 * the Radar place currently draws no rings, so this type is kept for the retained capability.
 */
internal data class RadarRingMark(
    val meters: Int,
    val fraction: Float,
    /** Ground bearing of this ring's label, so labels on different rings never stack. */
    val labelBearingDegrees: Float = 55f,
)

/** Large instrument ring, same footprint as the earlier compass. */
private const val RADAR_CARDINAL_RING_FRACTION = 0.86f
/** Dense thin ticks: one every 3°; every 30th tick is a cardinal direction. */
private const val RADAR_COMPASS_TICK_COUNT = 120
private const val RADAR_COMPASS_CARDINAL_EVERY = 30
private const val RADAR_COMPASS_MINOR_TICK_DP = 5f
private const val RADAR_COMPASS_NEAREST_TICK_DP = 9f
private const val RADAR_COMPASS_CARDINAL_TICK_DP = 14f
private const val RADAR_COMPASS_NORTH_TICK_DP = 20f
private const val RADAR_COMPASS_CARDINAL_TICK_WIDTH_DP = 2.5f
private const val RADAR_COMPASS_NORTH_TICK_WIDTH_DP = 3f
private const val RADAR_COMPASS_NEEDLE_APEX_INSET_DP = 8f
private const val RADAR_COMPASS_NEEDLE_UPPER_HEIGHT_DP = 13f
private const val RADAR_COMPASS_NEEDLE_UPPER_HALF_WIDTH_DP = 8f
private const val RADAR_COMPASS_NEEDLE_GAP_DP = 2.5f
private const val RADAR_COMPASS_NEEDLE_LOWER_HEIGHT_DP = 10f
private const val RADAR_COMPASS_NEEDLE_LOWER_HALF_WIDTH_DP = 5.5f
private val RadarKeySize = 48.dp
private val RadarKeyCorner = 10.dp
private val RadarChipCorner = 6.dp

// ---- Top cluster -------------------------------------------------------------------------------

/**
 * The top cluster: a soft scrim carrying one 48dp back key, the place label ("Radar · NW", the
 * cardinal rendered in cyan as passed by the caller), one quiet biome context line underneath, and,
 * at the right, the options key plus the spawn-settings key while the master Debug switch is on.
 *
 * There is no shelf, no box and no toolbar: contrast comes from the scrim, a single hairline and HUD
 * ink with a soft shadow, exactly like Home's floating status zone. Consumes the status-bar inset.
 */
@Composable
internal fun RadarTopChrome(
    cardinal: String?,
    onBack: () -> Unit,
    onOptions: () -> Unit,
    onSpawnSettings: () -> Unit,
    debugEnabled: Boolean,
    modifier: Modifier = Modifier,
    biomeText: String? = null,
) {
    val shadow = radarHudShadow()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(radarTopScrim())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadarChromeKey(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.ui_back_icon),
                onClick = onBack,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.nav_radar),
                        style = MaterialTheme.typography.titleMedium.copy(shadow = shadow),
                        color = TextPrimaryOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        // The cardinal is a localized N/NE/... passed by the caller; an uncalibrated
                        // compass keeps the slot honest with an em dash instead of inventing north.
                        text = cardinal?.let { "· $it" } ?: "· —",
                        style = MaterialTheme.typography.labelLarge.copy(shadow = shadow),
                        color = VitalCyan,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                // The resolved world biome, in the same quiet context-line role battle uses. It stays
                // absent while the lookup is Null, so an unavailable map never invents surroundings.
                biomeText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
                        color = TextSecondaryOnDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RadarChromeKey(
                icon = Icons.Rounded.Tune,
                description = stringResource(R.string.ui_world_options_title),
                onClick = onOptions,
            )
            if (debugEnabled) {
                Spacer(Modifier.width(6.dp))
                RadarChromeKey(
                    icon = Icons.Rounded.Settings,
                    description = stringResource(R.string.ui_world_spawn_settings),
                    onClick = onSpawnSettings,
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp)
                .height(1.dp)
                .background(TextPrimaryOnDark.copy(alpha = 0.12f))
        )
    }
}

// ---- Compass layer -----------------------------------------------------------------------------

/**
 * The radar compass: a large, quiet instrument ring in the same footprint as the earlier compass,
 * reduced to its essentials.
 *
 * One dense ring of thin cyan ticks turns with the phone heading (120 marks, every 3°); the main
 * cardinal directions get noticeably longer, thicker ticks, with north the longest so orientation is
 * readable without lettering. There is no inner dial, no bezel face, no blocks and no cardinal text:
 * the plate itself is the instrument face. A single red two-triangle needle — the reference digivice
 * arrow — rides the ring at the nearest wild Digimon's bearing and points outward at it.
 *
 * While the radar is live and motion is allowed, the needle occasionally performs one short idle
 * "search" gesture (a full sweep or a searching wiggle) and returns to the true bearing. The idle
 * gesture never replaces the reading: a hollow cyan bearing marker stays on the ring at the real
 * nearest-Digimon bearing for the duration, so the arrow's usability is unchanged. With motion
 * disabled the needle is simply static.
 */
@Composable
internal fun RadarCompassLayer(
    heading: Float,
    nearestBearingDegrees: Float? = null,
    nearestCardinalName: String? = null,
    animate: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val safeHeading = if (heading.isFinite()) heading else 0f
    val tickInk = VitalCyan
    val needleInk = StatusRed
    val needleEdge = SpaceBlack.copy(alpha = 0.35f)
    val vignetteInk = SpaceBlack
    val accessibility = nearestCardinalName?.let {
        stringResource(R.string.ui_world_compass_nearest, it)
    }
    // Idle search gesture: a short, infrequent authored sweep of the needle. The offset is additive
    // on top of the live bearing and always returns to 0, so the arrow keeps tracking the world.
    val searchOffset = remember { Animatable(0f) }
    LaunchedEffect(animate) {
        if (!animate) {
            searchOffset.snapTo(0f)
            return@LaunchedEffect
        }
        val random = Random(System.nanoTime())
        delay(2_800L + random.nextLong(3_000L))
        while (isActive) {
            when (random.nextInt(3)) {
                // A full search revolution, easing out as it locks back onto the bearing.
                0 -> {
                    searchOffset.animateTo(
                        360f,
                        tween(950, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)),
                    )
                    searchOffset.snapTo(0f)
                }
                // A quick searching wiggle either side of the bearing.
                1 -> {
                    val sweep = 16f + random.nextFloat() * 12f
                    searchOffset.animateTo(sweep, tween(260, easing = FastOutSlowInEasing))
                    searchOffset.animateTo(-sweep * 0.75f, tween(320, easing = FastOutSlowInEasing))
                    searchOffset.animateTo(sweep * 0.3f, tween(240, easing = FastOutSlowInEasing))
                    searchOffset.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                }
                // A revolution followed by a small confirm sweep.
                else -> {
                    searchOffset.animateTo(
                        360f,
                        tween(780, easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)),
                    )
                    searchOffset.snapTo(0f)
                    delay(90L)
                    searchOffset.animateTo(-14f, tween(190, easing = FastOutSlowInEasing))
                    searchOffset.animateTo(7f, tween(160, easing = FastOutSlowInEasing))
                    searchOffset.animateTo(0f, tween(180, easing = FastOutSlowInEasing))
                }
            }
            delay(8_000L + random.nextLong(6_000L))
        }
    }
    Canvas(
        modifier = modifier.then(
            if (accessibility != null) {
                Modifier.semantics { contentDescription = accessibility }
            } else {
                Modifier
            }
        )
    ) {
        val viewportWidth = size.width
        val viewportHeight = size.height
        if (viewportWidth <= 0f || viewportHeight <= 0f) return@Canvas
        val center = Offset(viewportWidth / 2f, viewportHeight / 2f)
        val compassRadius = radarChromeRingRadiusPx(
            RADAR_CARDINAL_RING_FRACTION,
            viewportWidth,
            viewportHeight,
        )
        if (compassRadius <= 0f) return@Canvas
        val hairline = 1.dp.toPx()

        // The plate edge is square in the 3D scene; a vignette dissolves it into the page instead of
        // letting the radar read as a bordered box again.
        drawRect(
            brush = Brush.radialGradient(
                0.58f to Color.Transparent,
                1f to vignetteInk.copy(alpha = 0.52f),
                center = center,
                radius = maxOf(viewportWidth, viewportHeight) * 0.62f,
            )
        )

        // Thin tick ring. Every tick is a short radial dash; the four cardinal ticks are longer and
        // thicker, north (azimuth 0) is the longest one, and the tick closest to the nearest
        // Digimon's bearing stays a little longer as the quiet, always-visible direction cue.
        val bearing = nearestBearingDegrees
        val tickStepDegrees = 360f / RADAR_COMPASS_TICK_COUNT
        val nearestTickIndex = if (bearing != null && bearing.isFinite()) {
            ((((bearing % 360f) + 360f) % 360f) / tickStepDegrees)
                .roundToInt()
                .mod(RADAR_COMPASS_TICK_COUNT)
        } else {
            -1
        }
        repeat(RADAR_COMPASS_TICK_COUNT) { index ->
            val azimuth = index * tickStepDegrees
            val screenAngle = radarChromeCardinalScreenAngle(azimuth, safeHeading)
            val cardinal = index % RADAR_COMPASS_CARDINAL_EVERY == 0
            val north = index == 0
            val nearest = index == nearestTickIndex && !cardinal
            val length = when {
                north -> RADAR_COMPASS_NORTH_TICK_DP.dp.toPx()
                cardinal -> RADAR_COMPASS_CARDINAL_TICK_DP.dp.toPx()
                nearest -> RADAR_COMPASS_NEAREST_TICK_DP.dp.toPx()
                else -> RADAR_COMPASS_MINOR_TICK_DP.dp.toPx()
            }
            val width = when {
                north -> RADAR_COMPASS_NORTH_TICK_WIDTH_DP.dp.toPx()
                cardinal -> RADAR_COMPASS_CARDINAL_TICK_WIDTH_DP.dp.toPx()
                else -> hairline
            }
            val alpha = when {
                north -> 0.95f
                cardinal -> 0.80f
                nearest -> 0.62f
                else -> 0.30f
            }
            rotate(degrees = screenAngle, pivot = center) {
                drawLine(
                    color = tickInk.copy(alpha = alpha),
                    start = Offset(center.x, center.y - compassRadius + length),
                    end = Offset(center.x, center.y - compassRadius),
                    strokeWidth = width,
                )
            }
        }

        // Nearest-Digimon needle: the reference digivice arrow is two stacked triangles — a larger
        // one pointing outward with its apex at the tick band, a small gap, and a slightly smaller
        // one pointing inward. It rides the ring at the nearest wild Digimon's bearing, with the idle
        // search offset added on top; the nearest tick above keeps the true direction visible.
        if (bearing != null && bearing.isFinite()) {
            val searchAngle = searchOffset.value
            val upperHalfWidth = RADAR_COMPASS_NEEDLE_UPPER_HALF_WIDTH_DP.dp.toPx()
            val upperHeight = RADAR_COMPASS_NEEDLE_UPPER_HEIGHT_DP.dp.toPx()
            val lowerHalfWidth = RADAR_COMPASS_NEEDLE_LOWER_HALF_WIDTH_DP.dp.toPx()
            val lowerHeight = RADAR_COMPASS_NEEDLE_LOWER_HEIGHT_DP.dp.toPx()
            val gap = RADAR_COMPASS_NEEDLE_GAP_DP.dp.toPx()
            val screenAngle = radarChromeCardinalScreenAngle(bearing + searchAngle, safeHeading)
            rotate(degrees = screenAngle, pivot = center) {
                val apex = center.y - compassRadius + RADAR_COMPASS_NEEDLE_APEX_INSET_DP.dp.toPx()
                val upperBase = apex + upperHeight
                val lowerBase = upperBase + gap
                val upper = Path().apply {
                    moveTo(center.x, apex)
                    lineTo(center.x + upperHalfWidth, upperBase)
                    lineTo(center.x - upperHalfWidth, upperBase)
                    close()
                }
                val lower = Path().apply {
                    moveTo(center.x, lowerBase + lowerHeight)
                    lineTo(center.x + lowerHalfWidth, lowerBase)
                    lineTo(center.x - lowerHalfWidth, lowerBase)
                    close()
                }
                drawPath(upper, needleInk)
                drawPath(lower, needleInk)
                drawPath(upper, needleEdge, style = Stroke(width = hairline))
                drawPath(lower, needleEdge, style = Stroke(width = hairline))
            }
        }
    }
}

// ---- Bottom signal band ------------------------------------------------------------------------

/**
 * The one bottom band: the ground floats over the world and fades upward with the navigation-bar
 * inset consumed by its content, so the band never becomes a deck or a permanent panel.
 *
 * The nearby count is always the primary reading. The status line appears only while the radar is
 * not READY, with its recovery action as a 48dp text action beside it; a quiet cyan rule marks it as
 * a live signal and it is announced politely. Zoom out / reset (the percentage) / zoom in keep the
 * same 48dp cut-corner key rhythm as the top cluster.
 */
@Composable
internal fun RadarSignalBand(
    nearbyCount: Int,
    statusText: String?,
    statusIsError: Boolean,
    recoveryLabel: String?,
    onRecovery: () -> Unit,
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shadow = radarHudShadow()
    val errorInk = MaterialTheme.colorScheme.error
    Box(modifier = modifier.fillMaxWidth()) {
        // Ground: same material as the top cluster, mirrored so the band fades up into the plate.
        Box(Modifier.matchParentSize().background(radarBottomScrim()))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (statusText != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(16.dp)
                            .background(if (statusIsError) errorInk else VitalCyan)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (statusIsError) errorInk else TextSecondaryOnDark,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (recoveryLabel != null) {
                        RadarTextAction(label = recoveryLabel, onClick = onRecovery)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = RadarKeySize),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.ui_world_nearby_count, nearbyCount),
                    style = MaterialTheme.typography.labelLarge.copy(shadow = shadow),
                    color = TextPrimaryOnDark,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 9.sp,
                        maxFontSize = MaterialTheme.typography.labelLarge.fontSize,
                        stepSize = 0.5.sp,
                    ),
                    modifier = Modifier.weight(1f),
                )
                RadarZoomControls(
                    zoom = zoom,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onZoomReset = onZoomReset,
                )
            }
        }
    }
}

/** A secondary text action that keeps a full 48dp target while staying quieter than a button. */
@Composable
private fun RadarTextAction(label: String, onClick: () -> Unit) {
    val shadow = radarHudShadow()
    Box(
        modifier = Modifier
            .heightIn(min = RadarKeySize)
            .widthIn(min = RadarKeySize)
            .clip(CutCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(shadow = shadow),
            color = VitalCyan,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Zoom out / reset (with the live percentage) / zoom in, all 48dp targets. */
@Composable
private fun RadarZoomControls(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
) {
    val resetLabel = stringResource(R.string.ui_world_zoom_reset)
    val percent = ((if (zoom.isFinite()) zoom else 1f) * 100f).roundToInt()
    val zoomLabel = stringResource(R.string.ui_world_zoom_label, percent)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadarChromeKey(
            icon = Icons.Rounded.Remove,
            description = stringResource(R.string.dex_chart_zoom_out),
            onClick = onZoomOut,
        )
        Box(
            modifier = Modifier
                .size(RadarKeySize)
                .clip(CutCornerShape(RadarKeyCorner))
                .clickable(role = Role.Button, onClick = onZoomReset)
                .semantics {
                    contentDescription = resetLabel
                    stateDescription = zoomLabel
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = TextPrimaryOnDark,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 9.sp,
                    maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                    stepSize = 0.5.sp,
                ),
            )
        }
        RadarChromeKey(
            icon = Icons.Rounded.Add,
            description = stringResource(R.string.dex_chart_zoom_in),
            onClick = onZoomIn,
        )
    }
}

// ---- Options sheet -----------------------------------------------------------------------------

/**
 * The single Radar options sheet: display and battle preferences are native switches, the legacy
 * renderers are Debug-only radio rows, and the Debug-only technical section carries the spawn
 * settings entry plus the caller's [debugExtra] instruments.
 *
 * [visible] gates the sheet; dismissal stays a sheet behaviour (drag or scrim tap). The
 * [onLegacyViewChange]/[onSpawnSettings] callbacks are expected to close the sheet, so the rows do
 * not fight the caller's state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RadarOptionsSheet(
    visible: Boolean,
    labelsEnabled: Boolean,
    onLabelsChange: (Boolean) -> Unit,
    arBattlesEnabled: Boolean,
    onArBattlesChange: (Boolean) -> Unit,
    /** Developer-only: AR battles are hidden when the build does not enable them. */
    arBattlesAvailable: Boolean = false,
    debugEnabled: Boolean,
    legacyView: RadarLegacyView,
    onLegacyViewChange: (RadarLegacyView) -> Unit,
    onSpawnSettings: () -> Unit,
    onDismiss: () -> Unit,
    debugExtra: (@Composable () -> Unit)? = null,
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDeepPurple,
        contentColor = TextPrimaryOnDark,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.ui_world_options_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .semantics { heading() },
            )
            RadarOptionSection(title = stringResource(R.string.ui_world_options_display)) {
                LabeledSwitchRow(
                    label = stringResource(R.string.ui_world_show_labels),
                    checked = labelsEnabled,
                    onCheckedChange = onLabelsChange,
                )
            }
            if (arBattlesAvailable) {
                RadarOptionSection(title = stringResource(R.string.ui_world_options_battles)) {
                    LabeledSwitchRow(
                        label = stringResource(R.string.ar_battle_toggle),
                        checked = arBattlesEnabled,
                        onCheckedChange = onArBattlesChange,
                        description = stringResource(R.string.ar_battle_toggle_description),
                    )
                }
            }
            if (debugEnabled) {
                RadarOptionSection(title = stringResource(R.string.ui_world_options_legacy)) {
                    Column(Modifier.selectableGroup()) {
                        RadarLegacyOption(
                            label = stringResource(R.string.ui_world_mode_iso),
                            selected = legacyView == RadarLegacyView.NONE,
                            onClick = { onLegacyViewChange(RadarLegacyView.NONE) },
                        )
                        RadarLegacyOption(
                            label = stringResource(R.string.ui_world_mode_2d),
                            selected = legacyView == RadarLegacyView.CLASSIC_2D,
                            onClick = { onLegacyViewChange(RadarLegacyView.CLASSIC_2D) },
                        )
                        RadarLegacyOption(
                            label = stringResource(R.string.ui_world_mode_fp),
                            selected = legacyView == RadarLegacyView.FIRST_PERSON,
                            onClick = { onLegacyViewChange(RadarLegacyView.FIRST_PERSON) },
                        )
                    }
                }
                RadarOptionSection(title = stringResource(R.string.ui_world_options_technical)) {
                    RadarOptionsEntry(
                        icon = Icons.Rounded.Settings,
                        label = stringResource(R.string.ui_world_spawn_settings),
                        onClick = onSpawnSettings,
                    )
                    if (debugExtra != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            debugExtra()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarOptionSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = VitalCyan,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/** A native radio row for the Debug-only legacy renderer choice. */
@Composable
private fun RadarLegacyOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RadarKeySize)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = VitalCyan,
                unselectedColor = TextSecondaryOnDark,
            ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) TextPrimaryOnDark else TextSecondaryOnDark,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A quiet management row; the sheet's technical entries use the same instrument rhythm. */
@Composable
private fun RadarOptionsEntry(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RadarKeySize)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondaryOnDark,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimaryOnDark,
        )
    }
}

// ---- Projected overlays ------------------------------------------------------------------------

/**
 * The projected hit target over one wild Digimon.
 *
 * The root is exactly a 48dp target centred on the projection, so the Lead can position it
 * mechanically; the name/range pill floats above the target (the parent Box must not clip) and is
 * hidden entirely when [name] is null (labels off). A cyan dot marks a Digimon that has a live
 * interaction. The whole marker is one semantics node, and long-press keeps the species detail
 * reachable from the world.
 */
@Composable
internal fun RadarEntityMarker(
    name: String?,
    distanceMeters: Int?,
    hasInteraction: Boolean,
    onTap: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val label = name?.let { resolvedName ->
        distanceMeters?.let { distance ->
            stringResource(R.string.ui_world_entity_distance, resolvedName, distance)
        } ?: resolvedName
    }
    val wildDigimon = stringResource(R.string.ui_world_wild_digimon)
    val interactionAvailable = stringResource(R.string.ui_world_interaction_available)
    val description = when {
        label != null && hasInteraction -> "$label, $interactionAvailable"
        label != null -> label
        hasInteraction -> "$wildDigimon, $interactionAvailable"
        else -> wildDigimon
    }
    Box(
        modifier = modifier
            .size(RadarKeySize)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) RadarMarkerPill(label = label)
    }
}

/**
 * The compact name/range pill. It is laid out with loosened constraints so it can overflow the 48dp
 * hit target upward while staying a single ellipsized line of bounded width; the marker owns the
 * semantics, so the visual text is not announced twice.
 */
@Composable
private fun RadarMarkerPill(label: String) {
    val density = LocalDensity.current
    val maxWidthPx = with(density) { 176.dp.roundToPx() }
    val gapPx = with(density) { 4.dp.roundToPx() }
    val shape = CutCornerShape(RadarChipCorner)
    Layout(
        content = {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(SurfaceDeepPurple.copy(alpha = 0.94f))
                    .border(1.dp, SurfaceStroke.copy(alpha = 0.72f), shape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextPrimaryOnDark,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
        },
    ) { measurables, _ ->
        val placeable = measurables.first().measure(
            Constraints(maxWidth = maxWidthPx, maxHeight = Constraints.Infinity)
        )
        // The marker's 48dp target is centred by the caller; the pill hangs above it with a 4dp gap.
        layout(0, 0) {
            placeable.place(x = -(placeable.width / 2), y = -(placeable.height + gapPx))
        }
    }
}

/**
 * One clamped speech bubble with a downward tail, used above the nearest speaking Digimon.
 *
 * The root width is fixed (default 220dp) so the Lead's `x - 110dp` anchor centres the tail exactly
 * over the projected Digimon; the surface wraps shorter speech and is centred under the tail. The
 * text stays at two lines maximum and the bubble is a single tap target for the interaction.
 */
@Composable
internal fun RadarSpeechBubble(
    text: String,
    onTap: () -> Unit,
    width: Dp = 220.dp,
    modifier: Modifier = Modifier,
) {
    val bubbleInk = SurfaceDeepPurple.copy(alpha = 0.94f)
    Column(
        modifier = modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = width)
                .clickable(role = Role.Button, onClick = onTap),
            shape = CutCornerShape(RadarKeyCorner),
            color = bubbleInk,
            contentColor = TextPrimaryOnDark,
            border = BorderStroke(1.dp, SurfaceStroke.copy(alpha = 0.8f)),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimaryOnDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
        // The tail is painted over the surface's bottom border so the bubble and leader read as one
        // shape; it is decorative, so it carries no semantics.
        Canvas(
            Modifier
                .width(16.dp)
                .height(7.dp)
                .offset(y = (-1).dp)
        ) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, bubbleInk)
        }
    }
}

// ---- Shared chrome primitives ------------------------------------------------------------------

/** One cut-corner key: surface tone plus hairline at rest, cyan when pressed. */
@Composable
private fun RadarChromeKey(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = CutCornerShape(RadarKeyCorner)
    Box(
        modifier = modifier
            .size(RadarKeySize)
            .clip(shape)
            // Bare key, like Home's HUD: the icon and its 48dp target, no fill and no hairline frame.
            // The cut-corner shape only bounds the press ripple; cyan marks the press.
            .clickable(
                interactionSource = interaction,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (pressed) VitalCyan else TextPrimaryOnDark.copy(alpha = 0.92f),
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Top ground: the partner slab's own surface tone at the status bar, fading down into the world.
 * Mirrors `hudTopScrim` from Home so the two places read as one material.
 */
@Composable
private fun radarTopScrim(): Brush {
    val ground = SurfaceElevatedPurple
    return Brush.verticalGradient(
        0f to ground.copy(alpha = PartnerSlabAlpha),
        0.55f to ground.copy(alpha = PartnerSlabAlpha * 0.55f),
        1f to Color.Transparent,
    )
}

/**
 * Bottom ground: fades up out of the world and firms up toward the bottom edge, so the band sits on
 * its most solid ground at the system bar (the navigation bar composites on the tone, not the raw
 * scene).
 */
@Composable
private fun radarBottomScrim(): Brush {
    val ground = SurfaceElevatedPurple
    return Brush.verticalGradient(
        0f to Color.Transparent,
        0.42f to ground.copy(alpha = PartnerSlabAlpha * 0.55f),
        1f to ground.copy(alpha = PartnerSlabAlpha),
    )
}

/** HUD text shadow: one soft 1dp/3dp drop, never a flat halo. */
@Composable
private fun radarHudShadow(): Shadow {
    val density = LocalDensity.current
    return Shadow(
        color = Color.Black.copy(alpha = 0.34f),
        offset = Offset(0f, with(density) { 1.dp.toPx() }),
        blurRadius = with(density) { 3.dp.toPx() },
    )
}
