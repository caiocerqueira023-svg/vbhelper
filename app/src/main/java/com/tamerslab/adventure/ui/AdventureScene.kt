package com.tamerslab.adventure.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.components.CharacterEntry
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.motionEnabled
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.ui.theme.AppTheme
import com.tamerslab.ui.theme.DeepPurpleBg
import com.tamerslab.ui.theme.DeepPurpleBgAlt
import com.tamerslab.ui.theme.LocalAppTheme
import com.tamerslab.ui.theme.OnVitalAccent
import com.tamerslab.ui.theme.StatusGreen
import com.tamerslab.ui.theme.SurfaceDeepPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.VitalCyanDim
import com.tamerslab.ui.theme.VitalOrange
import com.tamerslab.ui.theme.VitalPurple
import com.tamerslab.ui.theme.VitalPurpleBright
import com.tamerslab.ui.theme.VitalPurpleDim
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.getBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Adventure surface
//
// Structure follows the VB Arena Adventure composition: a city viewport with the
// walking team and a countdown plate, the "Character list" grid used to pick the
// team, and a single primary action (Depart / Collect / Withdraw).
//
// The geometry and timing below are the values shipped in the source scene and
// animation assets, read from the AssetBundle type trees:
//   * the running view places its four slots at exactly the source RectTransforms
//     (CharacterView 224x196 at panel fractions x 0.4265/0.7416/0.5168/0.8319 and
//     y 0.3936/0.6976 — two on top, two below, all on the right side);
//   * character animation id 12 "Walk": frames [(4, 20), (5, 20)] measured in
//     1/60 s draw frames -> one pose is held for 20/60 s = 0.333 s;
//   * AdvAnimMasterData.m_BgMoveSpeed = 0.12 and m_BackBgMoveSpeed = 0.13 UV/second
//     (a full 2-tile mirrored cycle therefore takes 2 / speed seconds).
//
// The two city layers and the departure ground plates are the extracted artwork.
// The city is recoloured off the main thread with a luminance -> palette gradient
// map built from the active saved palette, so the scene keeps many colours, takes
// all of them from the theme, and keeps its dark-city-on-light-page polarity in
// the light palettes. Partner artwork is the app's own sprite store, rendered
// through the Storage card.
// ---------------------------------------------------------------------------

const val AdventureViewportAspect: Float = 952f / 592f

/** Maximum partners that can leave on one adventure (the source scene's four CharacterView slots). */
const val AdventureMaxTeamSize: Int = 4

// Source walk pose: 20 draw frames at 60 fps.
private const val WALK_POSE_MILLIS = 333L

// Source background shader speeds (UV/second): middle 0.12, back 0.13.
private const val PARALLAX_MIDDLE_MS = 8_333
private const val PARALLAX_BACK_MS = 7_692

// CharacterView rect inside the 952x592 source "Adventure View" panel.
private const val RUNNER_WIDTH_FRACTION = 224f / 952f
private const val RUNNER_HEIGHT_FRACTION = 196f / 592f

private val AdventureDurationsHours = listOf(6, 12, 24)

enum class AdventureAction { DEPART, COLLECT, WITHDRAW }

/** What one partner is doing on the Adventure screen right now. */
sealed interface PartnerStageState {
    data object Idle : PartnerStageState
    data class Selected(val order: Int) : PartnerStageState
    data class Away(val order: Int, val remainingSeconds: Long, val ready: Boolean) : PartnerStageState
}

// ---------------------------------------------------------------------------
// Viewport: themed parallax city, countdown plate and the walking team
// ---------------------------------------------------------------------------

@Composable
fun AdventureViewport(
    runners: List<CharacterDtos.CharacterWithSprites>,
    running: Boolean,
    timerText: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val darkPalette = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val cityStops = cityGradientStops(darkPalette)
    var cityArtwork by remember { mutableStateOf<CityArtwork?>(null) }
    val cityKey = cityStops.hashCode()
    LaunchedEffect(cityKey) {
        cityArtwork = withContext(Dispatchers.Default) {
            buildCityArtwork(context.applicationContext, cityStops)
        }
    }
    val motion = motionEnabled()
    val animateCity = running && motion
    val backProgress = parallaxProgress(PARALLAX_BACK_MS, animateCity, "advBack")
    val middleProgress = parallaxProgress(PARALLAX_MIDDLE_MS, animateCity, "advMiddle")
    val walkClockState = walkClock(running && motion, "advWalk")
    val scrim = DeepPurpleBgAlt
    val topScrim = scrim.copy(alpha = if (darkPalette) 0.55f else 0.24f)
    val bottomScrim = scrim.copy(alpha = if (darkPalette) 0.62f else 0.34f)
    val frameBase = SurfaceStroke.copy(alpha = if (darkPalette) 0.9f else 1f)
    val frameAccent = TextPrimaryOnDark.copy(alpha = if (darkPalette) 0.85f else 0.95f)

    Box(
        modifier = modifier
            .clip(RectangleShape)
            .background(DeepPurpleBgAlt),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val artwork = cityArtwork
            if (artwork != null) {
                // One full panel-width tile per layer, scrolled by the source shader speed.
                // The tiles were pre-blended seamlessly, so the wrap has no mirror seam.
                drawTiledLayer(artwork.back, size.width, size.height, backProgress.value * size.width)
                drawTiledLayer(artwork.middle, size.width, size.height, middleProgress.value * size.width)
            }
            drawRect(
                Brush.verticalGradient(
                    0f to topScrim,
                    0.32f to Color.Transparent,
                    0.66f to Color.Transparent,
                    1f to bottomScrim,
                )
            )
        }

        // Countdown plate, top-end, the same position the source viewport uses.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
        ) {
            Surface(
                shape = CutCornerShape(4.dp),
                color = SurfaceDeepPurple.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, SurfaceStroke.copy(alpha = 0.72f)),
            ) {
                Text(
                    text = timerText,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimaryOnDark,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1,
                )
            }
        }

        if (runners.isNotEmpty()) {
            AdventureRunnerLayer(
                runners = runners,
                walkClock = walkClockState,
                animate = running && motion,
            )
        }

        // Square instrument frame with reinforced corners.
        Canvas(Modifier.fillMaxSize()) { drawViewportFrame(frameBase, frameAccent) }
    }
}

@Composable
private fun AdventureRunnerLayer(
    runners: List<CharacterDtos.CharacterWithSprites>,
    walkClock: State<Float>,
    animate: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val placements = RUNNER_SLOTS
        val phaseStep = 0.37f
        runners.take(AdventureMaxTeamSize).forEachIndexed { index, partner ->
            val placement = placements.getOrNull(index) ?: return@forEachIndexed
            val frames = remember(partner.id, partner.spriteRun1.size, partner.spriteRun2.size, partner.spriteIdle.size) {
                runnerFrames(partner)
            }
            val width = maxWidth * RUNNER_WIDTH_FRACTION
            val height = maxHeight * RUNNER_HEIGHT_FRACTION
            RunnerSprite(
                frames = frames,
                frameClock = walkClock,
                phase = index * phaseStep,
                animate = animate,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = maxWidth * placement.centerX - width / 2f,
                        y = maxHeight * placement.centerY - height / 2f,
                    )
                    .width(width)
                    .height(height),
            )
        }
    }
}

/** Exact source RectTransform centres inside the 952x592 Adventure View panel. */
private val RUNNER_SLOTS = listOf(
    RunnerPlacement(0.4265f, 0.3936f),
    RunnerPlacement(0.7416f, 0.3936f),
    RunnerPlacement(0.5168f, 0.6976f),
    RunnerPlacement(0.8319f, 0.6976f),
)

private data class RunnerPlacement(val centerX: Float, val centerY: Float)

private fun runnerFrames(partner: CharacterDtos.CharacterWithSprites): List<ImageBitmap> {
    val frames = buildList {
        listOf(partner.spriteRun1, partner.spriteRun2).forEach { bytes ->
            if (bytes.isEmpty()) return@forEach
            runCatching {
                BitmapData(bytes, partner.spriteWidth, partner.spriteHeight).getBitmap().asImageBitmap()
            }.getOrNull()?.let(::add)
        }
        if (isEmpty()) {
            runCatching {
                BitmapData(partner.spriteIdle, partner.spriteWidth, partner.spriteHeight).getBitmap().asImageBitmap()
            }.getOrNull()?.let(::add)
        }
    }
    return frames
}

@Composable
private fun RunnerSprite(
    frames: List<ImageBitmap>,
    frameClock: State<Float>,
    phase: Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val clock = frameClock.value
    val frameIndex = when {
        frames.size < 2 || !animate -> 0
        else -> {
            val cycle = ((clock + phase) % 1f + 1f) % 1f
            floor(cycle * frames.size).toInt().coerceIn(0, frames.size - 1)
        }
    }
    BoxWithConstraints(modifier) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxWidth * 0.04f)
                .width(maxWidth * 0.8f)
                .height(maxWidth * 0.15f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
                    )
                )
        )
        frames.getOrNull(frameIndex)?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = maxWidth * 0.05f),
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

private fun DrawScope.drawViewportFrame(base: Color, accent: Color) {
    val stroke = 1.5.dp.toPx()
    val accentStroke = 2.5.dp.toPx()
    val tick = 18.dp.toPx()
    // Solid keyline like the Storage card edge, so the viewport reads as a framed surface.
    drawRect(color = base, style = Stroke(width = stroke))
    // Reinforced corners in the palette signal, the same cyber-frame vocabulary as the partner cards.
    val corners = listOf(
        Offset(0f, 0f), Offset(size.width, 0f), Offset(0f, size.height), Offset(size.width, size.height),
    )
    corners.forEachIndexed { index, corner ->
        val dx = if (index % 2 == 0) tick else -tick
        val dy = if (index < 2) tick else -tick
        drawLine(accent, corner, Offset(corner.x + dx, corner.y), strokeWidth = accentStroke)
        drawLine(accent, corner, Offset(corner.x, corner.y + dy), strokeWidth = accentStroke)
    }
}

// ---------------------------------------------------------------------------
// Character list slot — the Storage card with an order badge on the right
// ---------------------------------------------------------------------------

@Composable
fun AdventurePartnerSlot(
    partner: CharacterDtos.CharacterWithSprites,
    state: PartnerStageState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = partner.nickname ?: partner.speciesName ?: "#${partner.id}"
    if (partner.spriteIdle.isEmpty()) {
        Box(modifier.aspectRatio(1f))
        return
    }
    val icon = remember(partner.id, partner.spriteIdle.size) {
        BitmapData(partner.spriteIdle, partner.spriteWidth, partner.spriteHeight)
    }
    val idleFrame2 = remember(partner.id, partner.spriteIdle2.size) {
        if (partner.spriteIdle2.isEmpty()) null
        else BitmapData(partner.spriteIdle2, partner.spriteWidth, partner.spriteHeight)
    }
    val statusLabel = when (state) {
        is PartnerStageState.Idle -> stringResource(R.string.adventure_status_in_progress)
        is PartnerStageState.Selected -> stringResource(R.string.adventure_selected_order, state.order)
        is PartnerStageState.Away -> if (state.ready) {
            stringResource(R.string.adventure_status_complete)
        } else {
            stringResource(R.string.adventure_status_in_progress)
        }
    }
    val detail = when (state) {
        is PartnerStageState.Away -> if (state.ready) {
            stringResource(R.string.adventure_scene_ready)
        } else {
            formatCountdown(state.remainingSeconds)
        }
        else -> displayName
    }
    val description = stringResource(
        R.string.adventure_scene_slot_description,
        displayName,
        statusLabel,
        detail,
    )
    val caption = when (state) {
        is PartnerStageState.Away -> if (state.ready) stringResource(R.string.adventure_scene_ready) else formatCountdown(state.remainingSeconds)
        else -> null
    }
    Box(modifier) {
        CharacterEntry(
            icon = icon,
            idleFrame2 = idleFrame2,
            animationKey = partner.spriteIdle,
            selected = state is PartnerStageState.Selected,
            statusText = null,
            iconDescription = description,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        when (state) {
            is PartnerStageState.Selected -> SlotBadge(
                text = state.order.toString(),
                color = VitalCyan,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            is PartnerStageState.Away -> SlotBadge(
                text = state.order.toString(),
                color = if (state.ready) StatusGreen else VitalCyan,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            PartnerStageState.Idle -> Unit
        }
        if (caption != null) {
            Text(
                text = caption,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        shape = CutCornerShape(3.dp),
                    )
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (state is PartnerStageState.Away && state.ready) StatusGreen else TextPrimaryOnDark,
                fontWeight = if (state is PartnerStageState.Away && state.ready) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SlotBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(14.dp)
            .size(22.dp)
            .background(color, CutCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = OnVitalAccent,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// Bottom action
// ---------------------------------------------------------------------------

@Composable
fun AdventureActionBar(
    action: AdventureAction,
    enabled: Boolean,
    hint: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (action) {
        AdventureAction.DEPART -> R.string.adventure_depart
        AdventureAction.COLLECT -> R.string.adventure_collect
        AdventureAction.WITHDRAW -> R.string.adventure_withdraw
    }
    Column(modifier.fillMaxWidth()) {
        hint?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryOnDark,
            )
        }
        VitalButton(
            onClick = onAction,
            enabled = enabled,
            style = if (action == AdventureAction.WITHDRAW) VitalButtonStyle.SECONDARY else VitalButtonStyle.PRIMARY,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text(stringResource(label))
        }
    }
}

// ---------------------------------------------------------------------------
// Departure check dialog (team preview + duration, matching the source CHECK dialog)
// ---------------------------------------------------------------------------

@Composable
fun AdventureDepartDialog(
    team: List<CharacterDtos.CharacterWithSprites>,
    onConfirm: (minutes: Long) -> Unit,
    onDismissRequest: () -> Unit,
) {
    var selectedHours by rememberSaveable { mutableIntStateOf(AdventureDurationsHours.first()) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.adventure_depart_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    team.forEachIndexed { index, partner ->
                        DeparturePlate(partner = partner, number = index + 1)
                    }
                }
                Text(
                    text = stringResource(R.string.adventure_depart_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.app_choose_duration),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.selectableGroup()) {
                    AdventureDurationsHours.forEach { hours ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = selectedHours == hours,
                                    role = Role.RadioButton,
                                    onClick = { selectedHours = hours },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(selected = selectedHours == hours, onClick = null)
                            Text(stringResource(R.string.app_duration_hours, hours))
                        }
                    }
                }
            }
        },
        confirmButton = {
            VitalButton(
                onClick = { onConfirm(selectedHours * 60L) },
                style = VitalButtonStyle.PRIMARY,
            ) {
                Text(stringResource(R.string.adventure_depart))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

@Composable
private fun DeparturePlate(partner: CharacterDtos.CharacterWithSprites, number: Int) {
    Box(
        modifier = Modifier.width(64.dp).height(72.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painter = painterResource(R.drawable.vb_adv_plate_ground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .aspectRatio(292f / 168f),
            contentScale = ContentScale.FillBounds,
            colorFilter = ColorFilter.tint(VitalCyan),
        )
        if (partner.spriteIdle.isNotEmpty()) {
            val sprite = remember(partner.id, partner.spriteIdle.size) {
                runCatching {
                    BitmapData(partner.spriteIdle, partner.spriteWidth, partner.spriteHeight)
                        .getBitmap()
                        .asImageBitmap()
                }.getOrNull()
            }
            if (sprite != null) {
                Image(
                    bitmap = sprite,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 10.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            }
        }
        SlotBadge(
            text = number.toString(),
            color = VitalCyan,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

// ---------------------------------------------------------------------------
// Withdraw confirmation
// ---------------------------------------------------------------------------

@Composable
fun AdventureWithdrawDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.adventure_withdraw_title)) },
        text = {
            Text(
                text = stringResource(R.string.adventure_withdraw_body, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            VitalButton(
                onClick = onConfirm,
                style = VitalButtonStyle.DESTRUCTIVE,
            ) {
                Text(stringResource(R.string.adventure_withdraw))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.adventure_keep)) }
        },
    )
}

// ---------------------------------------------------------------------------
// Theme-aware parallax
// ---------------------------------------------------------------------------

@Composable
private fun parallaxProgress(periodMillis: Int, enabled: Boolean, label: String): State<Float> {
    if (!enabled) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = label,
    )
}

/** Continuous 0..1 walk clock; one full cycle covers both walk poses (2 × 20/60 s in the source). */
@Composable
private fun walkClock(enabled: Boolean, label: String): State<Float> {
    if (!enabled) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = label)
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween((WALK_POSE_MILLIS * 2).toInt(), easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = label,
    )
}

/**
 * Luminance -> palette gradient used to recolour the extracted city artwork.
 *
 * The stops are built from the active saved palette: deep ground, the theme's
 * dim/primary/accent roles and a bright highlight (or, in the light palettes,
 * the dark ink shadow plus primary, tertiary and white). Every pixel keeps its
 * luminance ordering, so the city retains many distinct colours while all of
 * them belong to the theme, and the scene stays dark-on-light instead of
 * inverting in VB Arena / Digimon.net.
 */
@Composable
private fun cityGradientStops(darkPalette: Boolean): List<Pair<Float, Color>> {
    if (darkPalette) {
        return listOf(
            0.00f to DeepPurpleBg,
            0.30f to VitalPurpleDim,
            0.55f to VitalPurple,
            0.78f to VitalCyan,
            1.00f to TextPrimaryOnDark,
        )
    }
    return when (LocalAppTheme.current) {
        AppTheme.DIGIMON_NET -> {
            // Digimon.net owns a blue/white/gold palette whose primary and primary-bright are the
            // same blue and whose tertiary is a muddy gold, so the generic ramp reads grey. Use a
            // navy shell derived from the ink, the theme blue, its pale blue and its yellow.
            val shadow = androidx.compose.ui.graphics.lerp(TextPrimaryOnDark, VitalPurple, 0.35f)
            listOf(
                0.00f to shadow,
                0.32f to VitalPurpleBright,
                0.62f to VitalCyanDim,
                0.84f to VitalPurpleDim,
                1.00f to Color.White,
            )
        }
        else -> listOf(
            // VB Arena: the green/mint/blue ramp that matches its light surfaces.
            0.00f to TextPrimaryOnDark,
            0.32f to VitalPurpleBright,
            0.60f to VitalPurple,
            0.82f to VitalOrange,
            1.00f to Color.White,
        )
    }
}

private data class CityArtwork(val back: ImageBitmap, val middle: ImageBitmap)

private fun buildCityArtwork(context: Context, stops: List<Pair<Float, Color>>): CityArtwork? {
    val lut = buildCityLut(stops)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 2
        inScaled = false
    }
    fun tinted(@DrawableRes id: Int): ImageBitmap? {
        val source = BitmapFactory.decodeResource(context.resources, id, options) ?: return null
        return try {
            val width = source.width
            val height = source.height
            val pixels = IntArray(width * height)
            source.getPixels(pixels, 0, width, 0, 0, width, height)
            val out = IntArray(pixels.size)
            for (index in pixels.indices) {
                val pixel = pixels[index]
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha == 0) {
                    out[index] = 0
                } else {
                    val red = (pixel ushr 16) and 0xFF
                    val green = (pixel ushr 8) and 0xFF
                    val blue = pixel and 0xFF
                    val luminance = ((red * 77 + green * 150 + blue * 29) shr 8).coerceIn(0, 255)
                    out[index] = (alpha shl 24) or (lut[luminance] and 0x00FFFFFF)
                }
            }
            val (seamless, seamlessWidth) = makeSeamless(out, width, height)
            val tintedBitmap = Bitmap.createBitmap(seamlessWidth, height, Bitmap.Config.ARGB_8888)
            tintedBitmap.setPixels(seamless, 0, seamlessWidth, 0, 0, seamlessWidth, height)
            tintedBitmap.asImageBitmap()
        } finally {
            source.recycle()
        }
    }
    val back = tinted(R.drawable.vb_adv_bg_back) ?: return null
    val middle = tinted(R.drawable.vb_adv_bg_middle) ?: return null
    return CityArtwork(back = back, middle = middle)
}

private fun buildCityLut(stops: List<Pair<Float, Color>>): IntArray {
    val lut = IntArray(256)
    for (index in 0..255) {
        val value = index / 255f
        var lower = stops.first()
        var upper = stops.last()
        for (stopIndex in 0 until stops.lastIndex) {
            val current = stops[stopIndex]
            val next = stops[stopIndex + 1]
            if (value >= current.first && value <= next.first) {
                lower = current
                upper = next
                break
            }
        }
        val span = (upper.first - lower.first).takeIf { it > 0.0001f } ?: 1f
        val fraction = ((value - lower.first) / span).coerceIn(0f, 1f)
        val red = lerpChannel(lower.second.red, upper.second.red, fraction)
        val green = lerpChannel(lower.second.green, upper.second.green, fraction)
        val blue = lerpChannel(lower.second.blue, upper.second.blue, fraction)
        lut[index] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
    return lut
}

private fun lerpChannel(from: Float, to: Float, fraction: Float): Int =
    ((from + (to - from) * fraction) * 255f).roundToInt().coerceIn(0, 255)

private fun DrawScope.drawTiledLayer(
    image: ImageBitmap,
    panelWidth: Float,
    panelHeight: Float,
    offsetPx: Float,
) {
    if (panelWidth <= 0f || image.width <= 0) return
    // The source Unity Image maps the whole 2048x2048 texture into the 952x592 panel rect
    // (the BgScroll_Shader only offsets its UVs). Reproduce that exact mapping: no crop, no
    // extra zoom, and one panel-width tile so the horizontal wrap repeats like the shader.
    val displayWidth = panelWidth
    val displayHeight = panelHeight
    val shift = ((offsetPx % displayWidth) + displayWidth) % displayWidth
    val dstSize = IntSize(displayWidth.roundToInt(), displayHeight.roundToInt())
    var x = shift - displayWidth
    while (x < panelWidth) {
        drawImage(
            image = image,
            dstOffset = IntOffset(x.roundToInt(), 0),
            dstSize = dstSize,
            filterQuality = FilterQuality.Low,
        )
        x += displayWidth
    }
}

/**
 * Crops the right edge of the artwork into its left edge with a crossfade, producing a tile whose
 * horizontal wrap is continuous. This is the precomputed equivalent of the render-time mask and
 * removes the visible mirror seam the first implementation had.
 */
private fun makeSeamless(pixels: IntArray, width: Int, height: Int): Pair<IntArray, Int> {
    val band = (width * 0.12f).toInt().coerceIn(1, width / 4)
    val newWidth = width - band
    val result = IntArray(newWidth * height)
    for (y in 0 until height) {
        val sourceRow = y * width
        val targetRow = y * newWidth
        val tailStart = sourceRow + newWidth
        for (x in 0 until newWidth) {
            if (x < band) {
                val fraction = x.toFloat() / (band - 1).coerceAtLeast(1)
                result[targetRow + x] = blendArgb(pixels[tailStart + x], pixels[sourceRow + x], fraction)
            } else {
                result[targetRow + x] = pixels[sourceRow + x]
            }
        }
    }
    return result to newWidth
}

private fun blendArgb(from: Int, to: Int, fraction: Float): Int {
    val fromAlpha = (from ushr 24) and 0xFF
    val toAlpha = (to ushr 24) and 0xFF
    val alpha = (fromAlpha + (toAlpha - fromAlpha) * fraction).roundToInt().coerceIn(0, 255)
    val fromRed = (from ushr 16) and 0xFF
    val toRed = (to ushr 16) and 0xFF
    val red = (fromRed + (toRed - fromRed) * fraction).roundToInt().coerceIn(0, 255)
    val fromGreen = (from ushr 8) and 0xFF
    val toGreen = (to ushr 8) and 0xFF
    val green = (fromGreen + (toGreen - fromGreen) * fraction).roundToInt().coerceIn(0, 255)
    val fromBlue = from and 0xFF
    val toBlue = to and 0xFF
    val blue = (fromBlue + (toBlue - fromBlue) * fraction).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
}

// ---------------------------------------------------------------------------
// Formatting
// ---------------------------------------------------------------------------

/** Fixed HH:MM:SS plate used by the viewport countdown (the source shows 00:00:00 before departure). */
fun formatClock(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

/** Compact HH:MM:SS / MM:SS for the small slot captions. */
fun formatCountdown(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}











