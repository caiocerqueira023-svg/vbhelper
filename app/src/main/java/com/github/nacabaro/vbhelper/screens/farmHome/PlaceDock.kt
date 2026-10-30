package com.github.nacabaro.vbhelper.screens.farmHome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurple

/**
 * The five-place command deck that sits above the partner slab (PRESENTATION_REDESIGN_PLAN.md §5.3).
 *
 * Five 56dp cut-corner tiles, one selected place at most, labels always visible so the deck never
 * becomes an icon-only guessing game. No shadows: selection is a cyan rim plus a translucent fill.
 */
@Composable
fun PlaceDock(
    selected: PlaceDestination?,
    onPlaceClick: (PlaceDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PlaceDestination.entries.forEach { place ->
            PlaceButton(
                place = place,
                selected = selected == place,
                onClick = { onPlaceClick(place) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaceButton(
    place: PlaceDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(place.label)
    val subtitle = stringResource(place.subtitle)
    val shape = CutCornerShape(10.dp)
    val tileFill = if (selected) VitalPurple.copy(alpha = 0.34f) else SurfaceElevatedPurple.copy(alpha = 0.88f)
    val rim = if (selected) VitalCyan.copy(alpha = 0.92f) else SurfaceStroke.copy(alpha = 0.86f)
    val contentTint = if (selected) VitalCyan else TextSecondaryOnDark
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "$label, $subtitle" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier
                .size(56.dp)
                .clip(shape)
                .background(tileFill)
                .border(1.dp, rim, shape),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                painter = painterResource(place.icon),
                contentDescription = null,
                tint = contentTint,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentTint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Shared transparent fill for chips that float over the 3D scene. */
@Composable
internal fun hudChipColor(): Color = SurfaceElevatedPurple.copy(alpha = 0.88f)

/** Shared rim for chips that float over the 3D scene. */
@Composable
internal fun hudRimColor(): Color = SurfaceStroke.copy(alpha = 0.84f)
