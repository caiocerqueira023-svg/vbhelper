package com.tamerslab.farm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tamerslab.R
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Height of the whole place band, including its console edge. The partner sheet yields exactly this
 * much while it opens, which is why the row and the sheet can never overlap or leave a gap.
 */
internal val PlaceRowBandHeight: Dp = 70.dp

private val PlaceRowHeight: Dp = 62.dp

/** The row stops reading as one cluster past this width, so it stays centred on wide windows. */
private val PlaceDeckMaxWidth = 460.dp

/**
 * The five places (PRESENTATION_REDESIGN_PLAN.md §5.3) as pure overlay, sitting under the partner
 * sheet's peek slab.
 *
 * No tiles, no container: the places are icon plus caption floating on a bottom scrim that fades up
 * out of the world, closed by one hairline console edge. The sheet slides down into this band as it
 * opens, so the row is never covered or clipped - it yields its place. The caption sits under the icon
 * in HUD ink with a soft shadow, so it reads over chasm or sky, and auto-sizes down rather than
 * truncating in longer locales.
 */
@Composable
fun PlaceDock(
    selected: PlaceDestination?,
    onPlaceClick: (PlaceDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The ground runs full-bleed to the screen edge, under the system navigation bar, so the bar
    // composites its own scrim on the row's tone instead of on raw scene; only the content is inset.
    val navBottom = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PlaceRowBandHeight + navBottom),
    ) {
        // Ground: the same device the top cluster relies on, mirrored, fading up into the world.
        Box(Modifier.matchParentSize().hudBottomScrim())
        Column(Modifier.fillMaxSize().padding(bottom = navBottom)) {
            // The same soft hairline the top zone uses to separate itself from the world: here it marks
            // the junction with the partner sheet, inset so it never crosses the whole screen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(TextPrimaryOnDark.copy(alpha = 0.16f))
            )
            Row(
                modifier = Modifier
                    .widthIn(max = PlaceDeckMaxWidth)
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaceDestination.entries.forEach { place ->
                    PlaceKey(
                        place = place,
                        selected = selected == place,
                        onClick = { onPlaceClick(place) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceKey(
    place: PlaceDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(place.label)
    val subtitle = stringResource(place.subtitle)
    val shadow = hudTextShadow()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val lit = pressed || selected
    val markTint = if (lit) VitalCyan else TextPrimaryOnDark.copy(alpha = 0.92f)
    val captionTint = if (lit) VitalCyan else TextPrimaryOnDark.copy(alpha = 0.85f)
    Column(
        modifier = modifier
            .heightIn(min = PlaceRowHeight)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // The place announces itself as one Tab node; the visible caption stays a drawing, not a
            // second announcement.
            .semantics(mergeDescendants = true) { contentDescription = "$label, $subtitle" }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(place.icon),
            contentDescription = null,
            tint = markTint,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(shadow = shadow),
            color = captionTint,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 8.sp,
                maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
                stepSize = 0.25.sp,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
        // The current place keeps one accent mark: an underline under its caption.
        Box(
            Modifier
                .width(16.dp)
                .heightIn(min = 2.dp)
                .background(if (selected) VitalCyan else Color.Transparent)
        )
    }
}
