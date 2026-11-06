package com.tamerslab.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.tamerslab.home.ui.PartnerSlabAlpha
import com.tamerslab.ui.theme.SceneTextShadow
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * One top-cluster material for the whole app.
 *
 * Home, the Radar place and the battle surface established this vocabulary over their live 3D
 * worlds: a palette-resolved scrim instead of a shelf or a pill, cut-corner 48dp keys closed by a
 * hairline stroke, HUD ink with one soft drop shadow, and a single junction line. Every boxed page
 * header is built from the same primitives so the app reads as one instrument instead of a set of
 * screens with their own toolbars.
 */

/** 48dp key, the same target the reference clusters use. */
val HudKeySize = 48.dp

/** Cut-corner radius shared by every key in a top cluster. */
val HudKeyCorner = 10.dp

/**
 * Ground under a top cluster: the partner slab's own surface tone, fading from the top edge into the
 * page or scene. Applied by the cluster itself, never by a wrapping shelf.
 */
@Composable
fun Modifier.hudTopScrim(): Modifier {
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
 * Soft drop shadow for HUD ink: a 1dp offset plus a 3dp blur. [SceneTextShadow] flips dark/light with
 * the palette, so the same ink role holds over a dark scene and a light page.
 */
@Composable
fun hudInkShadow(): Shadow {
    val density = LocalDensity.current
    return Shadow(
        color = SceneTextShadow.copy(alpha = 0.35f),
        offset = Offset(0f, with(density) { 1.dp.toPx() }),
        blurRadius = with(density) { 3.dp.toPx() },
    )
}

/** HUD-ink variant of a text style: same type role, soft shadow attached. */
@Composable
fun TextStyle.hudInk(): TextStyle = copy(shadow = hudInkShadow())

/** Junction line that ends a top cluster without enclosing it. */
@Composable
fun HudHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(TextPrimaryOnDark.copy(alpha = 0.12f))
    )
}

/** One cut-corner key with a vector icon. */
@Composable
fun HudKey(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    HudKeySlot(description, onClick, modifier, enabled, accent) { active ->
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = hudKeyInk(active, enabled),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** One cut-corner key with a drawable icon, for the imported res/drawable icon set. */
@Composable
fun HudKey(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    HudKeySlot(description, onClick, modifier, enabled, accent) { active ->
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = hudKeyInk(active, enabled),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun hudKeyInk(active: Boolean, enabled: Boolean): Color = when {
    !enabled -> TextPrimaryOnDark.copy(alpha = 0.38f)
    active -> VitalCyan
    else -> TextPrimaryOnDark.copy(alpha = 0.92f)
}

@Composable
private fun HudKeySlot(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    accent: Boolean,
    content: @Composable (active: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = accent || pressed
    val shape = CutCornerShape(HudKeyCorner)
    Box(
        modifier = modifier
            .size(HudKeySize)
            .clip(shape)
            .background(SurfaceElevatedPurple.copy(alpha = if (active) 0.95f else 0.70f))
            .border(
                width = 1.dp,
                color = if (active) VitalCyan else SurfaceStroke.copy(alpha = 0.85f),
                shape = shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content(active)
    }
}
