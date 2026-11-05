package com.tamerslab.farm.ui.home

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.tamerslab.home.ui.PartnerSlabAlpha
import com.tamerslab.ui.theme.SurfaceElevatedPurple

/**
 * Chrome primitives for the home HUD.
 *
 * Home is an overlay on a live 3D world, not a page: the status cluster and the place row float
 * directly over the Digifarm with no enclosing shelf or deck. Their grounds are the partner slab's own
 * surface tone ([SurfaceElevatedPurple] at [PartnerSlabAlpha]), so all three zones read as one system
 * instead of three different greys; contrast comes from that ground plus HUD ink with a soft shadow.
 * Nothing is boxed unless it is genuinely a surface (the partner slab).
 */

/**
 * Top ground: the slab's tone at the status bar, fading down into the sky. Both ends of the HUD use the
 * same tone so the top and bottom read as the same material.
 */
@Composable
internal fun Modifier.hudTopScrim(): Modifier {
    val ground = SurfaceElevatedPurple
    return background(
        Brush.verticalGradient(
            0f to ground.copy(alpha = PartnerSlabAlpha),
            0.55f to ground.copy(alpha = PartnerSlabAlpha * 0.55f),
            1f to Color.Transparent,
        )
    )
}

/**
 * Bottom ground for the place row: starts at the slab's exact tone where the sheet meets it - so the
 * junction has no seam and no stroked line - holds that tone under the places, and fades out at the
 * bottom edge the way the top zone fades into the sky.
 */
@Composable
internal fun Modifier.hudBottomScrim(): Modifier {
    val ground = SurfaceElevatedPurple
    // The row starts at the sheet's exact tone so the two fit together with no seam, then firms up
    // toward the bottom edge so the places sit on their most solid ground.
    return background(
        Brush.verticalGradient(
            0f to ground.copy(alpha = PartnerSlabAlpha),
            0.55f to ground.copy(alpha = PartnerSlabAlpha + 0.08f),
            1f to ground.copy(alpha = PartnerSlabAlpha + 0.14f),
        )
    )
}

/** Soft drop shadow for HUD text over a live scene: offset plus blur, never a flat halo. */
@Composable
internal fun hudTextShadow(): Shadow {
    val density = LocalDensity.current
    return Shadow(
        color = Color.Black.copy(alpha = 0.3f),
        offset = Offset(0f, with(density) { 1.dp.toPx() }),
        blurRadius = with(density) { 3.dp.toPx() },
    )
}

