package com.tamerslab.transfer.ui.scan.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.motionEnabled
import com.tamerslab.utils.ImageBitmapData
import kotlinx.coroutines.delay

/** Whether the Digimon is materialising from the watch or dissolving back into it. */
enum class DigimonTransferMode {
    ARRIVING,
    DEPARTING,
}

/**
 * The Digimon hand-off animation shown while an NFC transfer runs.
 *
 * Built from the original extracted VB Lab scan assets, following the reverse-engineered usage:
 *  - the polling ring is the original `scan_images` ring texture (`vb_scan_ring_cyan`);
 *  - the character itself reuses the app's card artwork when known, otherwise the original
 *    `loadchara` two-frame pixel sprites (the same 64×64 frames the Unity `loadchara` Animator
 *    cycles);
 *  - the reveal is a mask wipe, matching the `mask1` / `mask_R1` mask animation names found in the
 *    shipped scene (their clip lengths are 1.667 s and 3.667 s respectively; the departure mask is
 *    shortened here so the waiting screen does not linger).
 *
 * No character or ring is redrawn procedurally. Motion respects the system reduce-motion setting.
 */
@Composable
fun DigimonTransferAura(
    mode: DigimonTransferMode,
    modifier: Modifier = Modifier,
    sprite: ImageBitmapData? = null,
) {
    val motion = motionEnabled()
    val arriving = mode == DigimonTransferMode.ARRIVING
    val durationMillis = if (arriving) 1667 else 2400

    val progress = remember(mode) { Animatable(0f) }
    LaunchedEffect(mode, motion) {
        if (!motion) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing),
            )
        }
    }

    // The shipped `loadchara` clip is a two-frame, ~3 fps loop; keep the same cadence.
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(motion) {
        if (motion) {
            while (true) {
                delay(150)
                frame = 1 - frame
            }
        }
    }

    val reveal = progress.value
    val density = LocalDensity.current
    val driftPx = with(density) {
        val drift = if (arriving) 12.dp * (1f - reveal) else (-18).dp * reveal
        drift.toPx()
    }

    val description = stringResource(
        if (arriving) R.string.scan_guide_digimon_arriving else R.string.scan_guide_digimon_departing,
    )

    Box(
        modifier = modifier
            .size(136.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Original cyan polling ring behind the character: breathing while the hand-off runs.
        Image(
            painter = painterResource(R.drawable.vb_scan_ring_cyan),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (motion) 0.35f + 0.45f * (1f - reveal) else 0.45f
                    val pulse = if (motion) 1f + 0.06f * reveal else 1f
                    scaleX = pulse
                    scaleY = pulse
                },
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        Box(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    translationY = driftPx
                    alpha = if (motion) 0.25f + 0.75f * reveal else 1f
                    val pop = if (motion) 0.92f + 0.08f * reveal else 1f
                    scaleX = pop
                    scaleY = pop
                }
                // Mask wipe: arriving reveals left-to-right, departing hides left-to-right.
                .drawWithContent {
                    val left = if (arriving) 0f else size.width * reveal
                    val right = if (arriving) size.width * reveal else size.width
                    clipRect(left = left, top = 0f, right = right, bottom = size.height) {
                        this@drawWithContent.drawContent()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (sprite != null) {
                Image(
                    bitmap = sprite.imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(sprite.dpWidth, sprite.dpHeight)
                )
            } else {
                Image(
                    painter = painterResource(
                        if (frame == 0) R.drawable.vb_scan_loadchara_0 else R.drawable.vb_scan_loadchara_1,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(84.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }
}
