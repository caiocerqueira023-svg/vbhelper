package com.tamerslab.transfer.ui.scan.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.components.motionEnabled
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan
import com.tamerslab.ui.theme.VitalPurple
import com.tamerslab.ui.theme.VitalPurpleBright

enum class TransferDirection { RECEIVE, SEND }

enum class TransferStagePhase { READY, WAITING, SUCCESS }

/** Everything the transfer stage needs to show the individual without loading it twice. */
data class TransferSubject(
    val characterId: Long? = null,
    val name: String? = null,
    val species: String? = null,
    val bitmap: ImageBitmap? = null,
    val cardName: String? = null,
    val cardBitmap: ImageBitmap? = null,
)

/**
 * Arena's two endpoints, used as they are: the original app mark and the original watch render,
 * with the original lightning line travelling between them. No invented badge frames.
 */
@Composable
fun ArenaEndpointRail(
    direction: TransferDirection,
    phase: TransferStagePhase,
    modifier: Modifier = Modifier,
    travelProgress: Float? = null,
) {
    val accent = VitalCyan
    val motion = motionEnabled()
    val pulse = if (phase == TransferStagePhase.WAITING && motion) {
        val transition = rememberInfiniteTransition(label = "arena-endpoint-rail")
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1050), RepeatMode.Reverse),
            label = "arena-endpoint-rail-pulse",
        ).value
    } else 1f
    val sourceActive = phase == TransferStagePhase.WAITING || phase == TransferStagePhase.SUCCESS
    val targetActive = phase == TransferStagePhase.SUCCESS
    val appActive = if (direction == TransferDirection.SEND) sourceActive else targetActive
    val watchActive = if (direction == TransferDirection.SEND) targetActive else sourceActive
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ArenaEndpoint(
            label = stringResource(R.string.transfer_endpoint_app),
            active = appActive,
            modifier = Modifier.width(108.dp),
        ) { tint ->
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(42.dp),
            )
        }
        SignalRail(
            direction = direction,
            phase = phase,
            pulse = pulse,
            travelProgress = travelProgress,
            modifier = Modifier.weight(1f).height(48.dp),
        )
        ArenaEndpoint(
            label = stringResource(R.string.transfer_endpoint_watch),
            active = watchActive,
            modifier = Modifier.width(108.dp),
        ) { tint ->
            Image(
                painter = painterResource(R.drawable.vb_lab_scan_icon),
                contentDescription = null,
                modifier = Modifier.size(46.dp).rotate(90f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
            )
        }
    }
}

@Composable
private fun SignalRail(
    direction: TransferDirection,
    phase: TransferStagePhase,
    pulse: Float,
    travelProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val accent = VitalCyan
    val idle = SurfaceStroke
    Canvas(modifier.fillMaxSize()) {
        val y = size.height / 2f
        val inset = 3.dp.toPx()
        val left = inset
        val right = size.width - inset
        val active = phase != TransferStagePhase.READY
        val base = if (active) accent else idle
        val alpha = when (phase) {
            TransferStagePhase.READY -> 0.6f
            TransferStagePhase.WAITING -> pulse
            TransferStagePhase.SUCCESS -> 1f
        }
        val stroke = 1.5.dp.toPx()
        drawLine(base.copy(alpha = alpha), Offset(left, y), Offset(right, y), stroke, StrokeCap.Round)
        val head = 7.dp.toPx()
        val wing = 4.dp.toPx()
        if (direction == TransferDirection.SEND) {
            drawLine(base.copy(alpha = alpha), Offset(right - head, y - wing), Offset(right, y), stroke, StrokeCap.Round)
            drawLine(base.copy(alpha = alpha), Offset(right - head, y + wing), Offset(right, y), stroke, StrokeCap.Round)
        } else {
            drawLine(base.copy(alpha = alpha), Offset(left + head, y - wing), Offset(left, y), stroke, StrokeCap.Round)
            drawLine(base.copy(alpha = alpha), Offset(left + head, y + wing), Offset(left, y), stroke, StrokeCap.Round)
        }
        if (travelProgress != null) {
            val p = travelProgress.coerceIn(0f, 1f)
            val x = if (direction == TransferDirection.SEND) left + (right - left) * p
            else right - (right - left) * p
            drawCircle(accent.copy(alpha = 0.20f), 6.dp.toPx(), Offset(x, y))
            drawCircle(accent, 2.5.dp.toPx(), Offset(x, y))
        }
    }
}

@Composable
private fun ArenaEndpoint(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit,
) {
    val tint = if (active) VitalCyan else TextMutedOnDark
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
            icon(tint)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            color = tint,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** The transfer illustration; the phone+watch render is the original Arena asset. */
@Composable
fun ArenaTransferStage(
    direction: TransferDirection,
    phase: TransferStagePhase,
    subject: TransferSubject?,
    modifier: Modifier = Modifier,
) {
    val accent = VitalCyan
    val second = VitalPurpleBright
    val motion = motionEnabled()
    val waiting = phase == TransferStagePhase.WAITING
    val pulse = if (waiting && motion) {
        val transition = rememberInfiniteTransition(label = "arena-transfer-stage")
        transition.animateFloat(
            initialValue = 0.72f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1250), RepeatMode.Reverse),
            label = "arena-transfer-stage-pulse",
        ).value
    } else 1f
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArenaEndpointRail(direction = direction, phase = phase)
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(264.dp),
            contentAlignment = Alignment.Center,
        ) {
            TransferGlow(Modifier.fillMaxSize())
            Image(
                painter = painterResource(R.drawable.vb_arena_ring),
                contentDescription = null,
                modifier = Modifier
                    .size(198.dp)
                    .graphicsLayer {
                        alpha = (if (waiting) 0.26f else 0.18f) * pulse
                        scaleX = 1f
                        scaleY = 1f
                        rotationZ = if (waiting) 2f * pulse else 0f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
            )
            Image(
                painter = painterResource(R.drawable.vb_arena_ring_thin),
                contentDescription = null,
                modifier = Modifier
                    .size(238.dp)
                    .graphicsLayer {
                        alpha = (if (waiting) 0.46f else 0.32f) * pulse
                        rotationZ = if (waiting) -4f * pulse else 0f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(second.copy(alpha = 0.85f), BlendMode.SrcIn),
            )
            Image(
                painter = painterResource(R.drawable.vb_arena_glow),
                contentDescription = null,
                modifier = Modifier.size(164.dp).graphicsLayer { alpha = 0.24f * pulse },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
            )
            if (subject?.bitmap != null) {
                Image(
                    bitmap = subject.bitmap,
                    contentDescription = subject.name ?: subject.species,
                    modifier = Modifier.size(136.dp),
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.vb_lab_scan_icon),
                    contentDescription = null,
                    modifier = Modifier.size(116.dp).rotate(90f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
                )
            }
        }
    }
}

@Composable
private fun TransferGlow(modifier: Modifier = Modifier) {
    val accent = VitalCyan
    val second = VitalPurple
    Canvas(modifier) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.52f,
            ),
            radius = size.minDimension * 0.52f,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(second.copy(alpha = 0.08f), Color.Transparent),
                center = center,
                radius = size.minDimension * 0.34f,
            ),
            radius = size.minDimension * 0.34f,
            center = center,
        )
    }
}

/**
 * One authored moment. Receiving materializes the Digimon; sending keeps the Digimon visible at
 * the start and lets it leave toward the watch, so the final stage is empty on purpose.
 */
@Composable
fun ArenaTransferSuccessVisual(
    direction: TransferDirection,
    subject: TransferSubject?,
    modifier: Modifier = Modifier,
) {
    val accent = VitalCyan
    val second = VitalPurpleBright
    val motion = motionEnabled()
    val sending = direction == TransferDirection.SEND
    val progress = remember(direction) { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(direction, motion) {
        if (motion) {
            progress.snapTo(0f)
            progress.animateTo(1f, animationSpec = tween(2200, easing = FastOutSlowInEasing))
        } else {
            progress.snapTo(1f)
        }
    }
    val p = progress.value
    val ringIn = ((p - 0.02f) / 0.55f).coerceIn(0f, 1f)
    val materialize = ((p - 0.22f) / 0.50f).coerceIn(0f, 1f)
    val depart = ((p - 0.12f) / 0.58f).coerceIn(0f, 1f)
    val ringAlpha = if (sending) 0.28f - ringIn * 0.10f else 0.04f + ringIn * 0.26f
    val ringScale = if (sending) 1.02f - ringIn * 0.08f else 0.82f + ringIn * 0.24f
    val heroAlpha = if (sending) 1f - depart else materialize
    val heroScale = if (sending) 1f - depart * 0.30f else 0.70f + materialize * 0.30f
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArenaEndpointRail(
            direction = direction,
            phase = TransferStagePhase.SUCCESS,
            travelProgress = p,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(278.dp),
            contentAlignment = Alignment.Center,
        ) {
            TransferGlow(Modifier.fillMaxSize())
            Image(
                painter = painterResource(R.drawable.vb_arena_ring),
                contentDescription = null,
                modifier = Modifier
                    .size(198.dp)
                    .graphicsLayer {
                        alpha = ringAlpha
                        scaleX = ringScale
                        scaleY = ringScale
                        rotationZ = if (sending) 0f else -4f + ringIn * 6f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
            )
            Image(
                painter = painterResource(R.drawable.vb_arena_ring_thin),
                contentDescription = null,
                modifier = Modifier
                    .size(238.dp)
                    .graphicsLayer {
                        alpha = if (sending) 0.34f - ringIn * 0.10f else 0.10f + ringIn * 0.40f
                        scaleX = if (sending) 1.02f - ringIn * 0.04f else 0.96f + ringIn * 0.06f
                        scaleY = scaleX
                        rotationZ = if (sending) 0f else 12f - ringIn * 8f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(second.copy(alpha = 0.85f), BlendMode.SrcIn),
            )
            Image(
                painter = painterResource(R.drawable.vb_arena_glow),
                contentDescription = null,
                modifier = Modifier
                    .size(180.dp)
                    .graphicsLayer {
                        alpha = if (sending) 0.36f - depart * 0.14f else 0.10f + materialize * 0.32f
                    },
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(accent, BlendMode.SrcIn),
            )
            if (subject?.bitmap != null) {
                Image(
                    bitmap = subject.bitmap,
                    contentDescription = subject.name ?: subject.species,
                    modifier = Modifier
                        .size(148.dp)
                        .graphicsLayer {
                            alpha = heroAlpha
                            scaleX = heroScale
                            scaleY = heroScale
                            translationX = if (sending) 48.dp.toPx() * depart else 0f
                            translationY = if (sending) 0f else (1f - materialize) * 14.dp.toPx()
                        },
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.None,
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.vb_lab_scan_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(108.dp)
                        .rotate(90f)
                        .graphicsLayer {
                            alpha = if (sending) 0.55f * heroAlpha else heroAlpha
                            scaleX = heroScale
                            scaleY = heroScale
                            translationX = if (sending) 48.dp.toPx() * depart else 0f
                            translationY = if (sending) 0f else (1f - materialize) * 14.dp.toPx()
                        },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(
                        accent.copy(alpha = if (sending) 0.55f * heroAlpha else heroAlpha),
                        BlendMode.SrcIn,
                    ),
                )
            }
        }
    }
}

@Composable
fun TransferActionDock(
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    primaryStyle: VitalButtonStyle = VitalButtonStyle.PRIMARY,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    debugLabel: String? = null,
    onDebug: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        VitalButton(
            onClick = onPrimary,
            enabled = primaryEnabled,
            style = primaryStyle,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(primaryLabel, style = MaterialTheme.typography.titleSmall)
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = TextSecondaryOnDark)
            }
        }
        if (debugLabel != null && onDebug != null) {
            TextButton(onClick = onDebug) {
                Text(
                    debugLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedOnDark,
                )
            }
        }
    }
}

/** Bare direction row using the original Arena app/watch art; no invented route icon. */
@Composable
fun TransferDirectionOption(
    direction: TransferDirection,
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = VitalCyan
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 14.dp)
            .alpha(if (enabled) 1f else 0.62f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ArenaMiniRoute(direction = direction, width = 54.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark,
                maxLines = 2,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (enabled) accent else TextMutedOnDark,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ArenaMiniRoute(direction: TransferDirection, width: Dp) {
    Row(
        modifier = Modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val app = @Composable {
            Icon(
                imageVector = Icons.Outlined.Smartphone,
                contentDescription = null,
                tint = VitalCyan,
                modifier = Modifier.size(22.dp),
            )
        }
        val watch = @Composable {
            Image(
                painter = painterResource(R.drawable.vb_lab_scan_icon),
                contentDescription = null,
                modifier = Modifier.size(22.dp).rotate(90f),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(VitalCyan, BlendMode.SrcIn),
            )
        }
        if (direction == TransferDirection.RECEIVE) {
            watch()
        } else {
            app()
        }
        Image(
            painter = painterResource(R.drawable.vb_arena_signal),
            contentDescription = null,
            modifier = Modifier.width(16.dp).height(18.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(VitalCyan, BlendMode.SrcIn),
        )
        if (direction == TransferDirection.RECEIVE) {
            app()
        } else {
            watch()
        }
    }
}
