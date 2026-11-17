package com.tamerslab.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.tamerslab.R
import com.tamerslab.ui.theme.DeepPurpleBg
import com.tamerslab.ui.theme.TextPrimaryOnDark
import kotlin.math.roundToInt

/** A quiet, non-interactive device pattern placed behind all application content. */
@Composable
fun TechBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(DeepPurpleBg)) {
        TechGears(Modifier.fillMaxSize())
        content()
    }
}

/**
 * The three VB Lab mechanism rings, drawn directly on a full-screen canvas so the placement is
 * exact in pixels: no layout/offset chain can shift them. The cluster is centred on the
 * bottom-right corner with its own diameters (the preview-4 framing) and every ring spins flat
 * around that shared axis, exactly like the old two-semicircle background did.
 *
 * Ring art and the 20 s `homebg_anime` loop come from the shipped VB Lab bundle; the home-screen
 * instance transform lives in a remote Addressables scene that was never downloaded, so the
 * framing is reconstructed from a shipped screenshot.
 */
@Composable
private fun TechGears(modifier: Modifier = Modifier) {
    val ink = TextPrimaryOnDark
    val motion = motionEnabled()
    // Dark ink on a bright background reads weaker than light ink on a dark one, so the rings get
    // a little extra presence on the light palettes.
    val lightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val presence = if (lightTheme) 1.7f else 1f
    val tint = ColorFilter.tint(ink)
    val resources = LocalContext.current.resources
    val inner = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.vb_bg_gear_c1).asImageBitmap()
    }
    val middle = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.vb_bg_gear_c2).asImageBitmap()
    }
    val outer = remember(resources) {
        BitmapFactory.decodeResource(resources, R.drawable.vb_bg_gear_c3).asImageBitmap()
    }

    // Same movement language as the old semicircles: linear, one direction, slow spins.
    val innerRotation = if (motion) {
        rememberInfiniteTransition(label = "gearInner").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing)),
            label = "gearInnerRotation",
        ).value
    } else 0f
    val midRotation = if (motion) {
        rememberInfiniteTransition(label = "gearMid").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(32_000, easing = LinearEasing)),
            label = "gearMidRotation",
        ).value
    } else 0f
    val outerRotation = if (motion) {
        rememberInfiniteTransition(label = "gearOuter").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "gearOuterRotation",
        ).value
    } else 0f

    Canvas(modifier) {
        val center = Offset(size.width, size.height)

        fun ring(image: ImageBitmap, diameter: Float, rotation: Float, alpha: Float) {
            if (diameter <= 1f) return
            val side = diameter.roundToInt()
            val half = diameter / 2f
            withTransform({
                translate(center.x, center.y)
                rotate(rotation, pivot = Offset.Zero)
                translate(-half, -half)
            }) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(side, side),
                    alpha = alpha,
                    colorFilter = tint,
                    filterQuality = FilterQuality.Medium,
                )
            }
        }

        ring(inner, size.width * 1.20f, innerRotation, 0.085f * presence)
        ring(middle, size.width * 1.60f, midRotation, 0.060f * presence)
        ring(outer, size.width * 2.00f, outerRotation, 0.045f * presence)
    }
}
