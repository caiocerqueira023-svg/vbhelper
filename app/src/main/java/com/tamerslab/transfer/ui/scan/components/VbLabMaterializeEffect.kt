package com.tamerslab.transfer.ui.scan.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.motionEnabled
import com.tamerslab.utils.ImageBitmapData
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Materialisation of a Digimon for the receive/send hand-off, built from the original VB Lab
 * `scan_FromToy` / `scan_ReturnToy` effect bundle:
 *
 * - `degimon_scan_line` supplies the three curved light arcs;
 * - `degimon_scan` supplies the scan rings, the bottom arcs and the cyan light band;
 * - the light column/flash/particles are composed to match the additive SpriteStudio effect.
 *
 * The Digimon card art is the character; the original `loadchara` frames remain the fallback.
 * Reduced motion freezes the tableau at a readable frame instead of playing the timeline.
 */
@Composable
fun VbLabMaterializeEffect(
    mode: DigimonTransferMode,
    modifier: Modifier = Modifier,
    sprite: ImageBitmapData? = null,
) {
    val motion = motionEnabled()
    val arriving = mode == DigimonTransferMode.ARRIVING
    val progress = remember(mode) { Animatable(if (motion) 0f else 0.62f) }
    LaunchedEffect(mode, motion) {
        if (motion) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 2300, easing = LinearOutSlowInEasing),
            )
        } else {
            progress.snapTo(if (arriving) 1f else 0.22f)
        }
    }

    val p = progress.value
    val appear = if (arriving) smoothstep(0.30f, 0.72f, p) else 1f - smoothstep(0.28f, 0.62f, p)
    val beam = if (arriving) {
        smoothstep(0f, 0.24f, p) * (1f - smoothstep(0.72f, 1f, p))
    } else {
        smoothstep(0.12f, 0.55f, p) * (1f - smoothstep(0.88f, 1f, p))
    }
    val flash = if (arriving) flashPeak(p, 0.54f, 0.17f) else flashPeak(p, 0.42f, 0.15f)
    val arcSpread = if (arriving) 1f - smoothstep(0.12f, 0.68f, p) else smoothstep(0.18f, 0.82f, p)
    val ringSpin = if (arriving) 140f * p else -100f * p
    val charAlpha = if (motion) appear.coerceIn(0f, 1f) else 1f
    val charScale = if (arriving) 0.68f + 0.36f * appear else 1.06f - 0.28f * p
    val density = LocalDensity.current
    val arcGapPx = with(density) { 25.dp.toPx() }
    val arcDriftPx = with(density) { 22.dp.toPx() } * arcSpread
    val charDriftPx = with(density) {
        (if (arriving) (1f - appear) * 28.dp.toPx() else -34.dp.toPx() * p)
    }
    val description = stringResource(
        if (arriving) R.string.scan_received_character_success else R.string.scan_sent_character_success,
    )

    Box(
        modifier = modifier
            .size(width = 320.dp, height = 360.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 0.50f

            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF2EE9FF).copy(alpha = 0.05f + 0.17f * beam),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = size.maxDimension * 0.72f,
                ),
            )

            if (beam > 0.01f) {
                val beamWidth = size.width * (0.05f + 0.34f * beam)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFFA9F7FF).copy(alpha = 0.55f * beam),
                            Color.White.copy(alpha = 0.9f * beam),
                            Color(0xFF6DEBFF).copy(alpha = 0.55f * beam),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset(cx - beamWidth / 2f, 0f),
                    size = Size(beamWidth, size.height),
                )
                drawRect(
                    color = Color.White.copy(alpha = 0.85f * beam),
                    topLeft = Offset(cx - 1.5f, 0f),
                    size = Size(3f, size.height),
                )
                val flareWidth = size.width * (0.16f + 0.62f * beam)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0xFFB9FBFF).copy(alpha = 0.55f * beam), Color.Transparent),
                    ),
                    topLeft = Offset(cx - flareWidth / 2f, cy - 2f),
                    size = Size(flareWidth, 4f),
                )
            }

            // Deterministic spark field; converges while arriving, disperses while departing.
            val sparkCount = 30
            for (index in 0 until sparkCount) {
                val angle = (index * 137.508f) * PI.toFloat() / 180f
                val radial = size.minDimension * (0.14f + ((index * 37) % 100) / 100f * 0.55f)
                val distance = if (arriving) radial * (1f - 0.55f * appear) else radial * (0.45f + 0.75f * p)
                val sparkAlpha = (0.15f + 0.85f * beam) * (0.35f + 0.65f * ((index % 5) / 4f))
                drawCircle(
                    color = Color(0xFFC9FBFF).copy(alpha = sparkAlpha.coerceIn(0f, 0.8f)),
                    radius = 1.1f + (index % 4) * 0.7f,
                    center = Offset(cx + cos(angle) * distance, cy + sin(angle) * distance * 0.86f),
                )
            }

            if (flash > 0.01f) {
                drawRect(Color.White.copy(alpha = flash * 0.65f))
            }
        }

        // Back glow and the two original scan rings.
        Image(
            painter = painterResource(R.drawable.vb_scan_glow2),
            contentDescription = null,
            modifier = Modifier
                .size(310.dp)
                .graphicsLayer {
                    alpha = 0.10f + 0.30f * beam + 0.25f * flash
                    scaleX = 0.9f + 0.25f * beam
                    scaleY = 0.9f + 0.25f * beam
                },
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.vb_scan_ring_target),
            contentDescription = null,
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    rotationZ = ringSpin
                    alpha = (0.22f + 0.55f * beam).coerceIn(0f, 0.8f)
                    scaleX = 0.85f + 0.25f * p
                    scaleY = 0.85f + 0.25f * p
                },
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.vb_scan_ring_disc),
            contentDescription = null,
            modifier = Modifier
                .size(268.dp)
                .graphicsLayer {
                    rotationZ = -ringSpin * 0.6f
                    alpha = (0.18f + 0.55f * beam).coerceIn(0f, 0.75f)
                    scaleX = 1.1f - 0.18f * appear
                    scaleY = 1.1f - 0.18f * appear
                },
            contentScale = ContentScale.Fit,
        )
        Image(
            painter = painterResource(R.drawable.vb_scan_arcs_bottom),
            contentDescription = null,
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer {
                    alpha = (0.10f + 0.35f * beam).coerceIn(0f, 0.55f)
                    translationY = arcDriftPx * 1.8f
                },
            contentScale = ContentScale.Fit,
        )

        // Three original curved light arcs, counter-rotating around the character.
        val arcResources = intArrayOf(
            R.drawable.vb_scan_arc_0,
            R.drawable.vb_scan_arc_1,
            R.drawable.vb_scan_arc_2,
        )
        arcResources.forEachIndexed { index, resource ->
            Image(
                painter = painterResource(resource),
                contentDescription = null,
                modifier = Modifier
                    .size(width = 306.dp, height = 70.dp)
                    .graphicsLayer {
                        translationY = (index - 1) * arcGapPx + (if (arriving) arcDriftPx else -arcDriftPx)
                        rotationZ = (if (arriving) 1f else -1f) * arcSpread * 42f + (index - 1) * 10f
                        alpha = (0.12f + 0.78f * beam).coerceIn(0f, 0.95f)
                        scaleX = 0.72f + 0.30f * beam
                        scaleY = 0.9f
                    },
                contentScale = ContentScale.Fit,
            )
        }

        // The light sheet, rotated from the original horizontal cyan band to a vertical column.
        Image(
            painter = painterResource(R.drawable.vb_scan_beam),
            contentDescription = null,
            modifier = Modifier
                .size(width = 300.dp, height = 54.dp)
                .graphicsLayer {
                    rotationZ = 90f
                    alpha = (0.25f + 0.55f * beam).coerceIn(0f, 0.85f)
                    scaleX = 0.5f + 0.7f * beam
                    scaleY = 1f
                },
            contentScale = ContentScale.FillBounds,
        )

        // Character, with a white flash silhouette at the materialisation peak.
        Box(
            modifier = Modifier
                .size(158.dp)
                .graphicsLayer {
                    alpha = charAlpha
                    translationY = charDriftPx
                    scaleX = charScale
                    scaleY = charScale
                },
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(
                    bitmap = sprite.imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    bitmap = sprite.imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = flash * 0.9f },
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(Color.White),
                )
            } else {
                Image(
                    painter = painterResource(
                        if (p < 0.5f) R.drawable.vb_scan_loadchara_0 else R.drawable.vb_scan_loadchara_1,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(112.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
    if (edge0 == edge1) return if (value < edge0) 0f else 1f
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun flashPeak(value: Float, center: Float, width: Float): Float {
    val distance = abs((value - center) / width)
    return (1f - distance).coerceIn(0f, 1f)
}
