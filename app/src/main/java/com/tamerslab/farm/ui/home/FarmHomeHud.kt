package com.tamerslab.farm.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.TransferNavigation
import com.tamerslab.navigation.TransferSection
import com.tamerslab.navigation.navigatePrimary
import com.tamerslab.farm.FarmSpriteFilter
import com.tamerslab.farm.ui.digifarm.FarmWorldResident
import com.tamerslab.farm.ui.digifarm.FarmWorldSummary
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Top status bar of the farm home (PRESENTATION_REDESIGN_PLAN.md §5.1).
 *
 * One solid shelf instead of four floating pills: a farm identity block on the left (home glyph,
 * resident count, "who is here" chevron) and the utility cluster on the right (bits, quests,
 * settings, menu). The shelf guarantees contrast over a moving 3D scene, and the utility actions use
 * real Material vectors — the previous hand-drawn menu glyph was the thing that could vanish.
 */
@Composable
fun HomeTopHud(
    residentCount: Int,
    capacity: Int,
    bits: Int,
    onFarmClick: () -> Unit,
    onQuestsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val barShape = CutCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.78f),
                    0.72f to MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                    1f to Color.Transparent,
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(barShape)
                .background(SurfaceElevatedPurple.copy(alpha = 0.84f))
                .border(1.dp, SurfaceStroke.copy(alpha = 0.9f), barShape)
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FarmIdentity(
                residentCount = residentCount,
                capacity = capacity,
                onClick = onFarmClick,
            )
            Spacer(Modifier.weight(1f))
            BitsReadout(bits = bits)
            HudBarButton(
                icon = Icons.Rounded.Flag,
                description = stringResource(R.string.home_quests_chip_description),
                onClick = onQuestsClick,
            )
            HudBarButton(
                icon = Icons.Rounded.Settings,
                description = stringResource(R.string.home_settings_description),
                onClick = onSettingsClick,
            )
            HudBarButton(
                icon = Icons.Rounded.Menu,
                description = stringResource(R.string.home_menu_description),
                onClick = onMenuClick,
            )
        }
    }
}

@Composable
private fun FarmIdentity(
    residentCount: Int,
    capacity: Int,
    onClick: () -> Unit,
) {
    val description = stringResource(R.string.home_farm_chip_description)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CutCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Home,
            contentDescription = null,
            tint = VitalCyan,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(R.string.home_farm_residents, residentCount, capacity),
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimaryOnDark,
            maxLines = 1,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = TextSecondaryOnDark,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BitsReadout(bits: Int) {
    val description = stringResource(R.string.items_store_credits, bits)
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CutCornerShape(8.dp))
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.MonetizationOn,
            contentDescription = null,
            tint = VitalCyan,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = bits.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = VitalCyan,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 24.dp),
        )
    }
}

@Composable
private fun HudBarButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CutCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondaryOnDark,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Who is currently in the diorama. Read-only by design: the farm is aesthetic, so this lists the
 * active partner and the favourites that fill the remaining places and lets the camera pick one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmRosterSheet(
    residents: List<FarmWorldResident>,
    followedRenderId: String?,
    residentLimit: Int,
    candidateCount: Int,
    spriteFilter: FarmSpriteFilter,
    onResidentLimitChange: (Int) -> Unit,
    onSpriteFilterChange: (FarmSpriteFilter) -> Unit,
    onFollow: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.home_roster_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
                modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
            )
            FarmSettingsControls(
                residentLimit = residentLimit,
                shownCount = residents.size,
                candidateCount = candidateCount,
                spriteFilter = spriteFilter,
                onResidentLimitChange = onResidentLimitChange,
                onSpriteFilterChange = onSpriteFilterChange,
            )
            Text(
                text = stringResource(
                    R.string.home_roster_sheet_note,
                    FarmWorldSummary.CAPACITY,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = TextMutedOnDark,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            residents.forEach { resident ->
                val followed = resident.renderId == followedRenderId
                MenuRow(
                    icon = R.drawable.baseline_mood_24,
                    label = resident.name,
                    detail = when {
                        followed -> stringResource(R.string.home_roster_followed)
                        resident.isActivePartner -> stringResource(R.string.home_roster_active_partner)
                        else -> resident.attribute
                    },
                    tint = if (followed) VitalCyan else TextSecondaryOnDark,
                    onClick = { onFollow(resident.renderId) },
                )
            }
        }
    }
}

/**
 * Owner-facing farm settings, kept inside the "who is here" sheet so the HUD stays two zones:
 * how many residents may appear (1..12, the active partner always keeps a place) and which artwork
 * the diorama is allowed to draw (mixed card/VB art, VB only, or Dot art only).
 */
@Composable
private fun FarmSettingsControls(
    residentLimit: Int,
    shownCount: Int,
    candidateCount: Int,
    spriteFilter: FarmSpriteFilter,
    onResidentLimitChange: (Int) -> Unit,
    onSpriteFilterChange: (FarmSpriteFilter) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clip(CutCornerShape(10.dp))
            .background(SurfaceElevatedPurple.copy(alpha = 0.55f))
            .border(1.dp, SurfaceStroke.copy(alpha = 0.7f), CutCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.home_farm_settings_title),
            style = MaterialTheme.typography.labelLarge,
            color = VitalCyan,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_farm_resident_limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimaryOnDark,
                )
                Text(
                    text = stringResource(
                        R.string.home_farm_showing,
                        shownCount,
                        candidateCount.coerceAtLeast(shownCount),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMutedOnDark,
                )
            }
            StepperButton(
                icon = Icons.Rounded.Remove,
                description = stringResource(R.string.home_farm_decrease),
                enabled = residentLimit > 1,
                onClick = { onResidentLimitChange(residentLimit - 1) },
            )
            Text(
                text = residentLimit.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
                modifier = Modifier.widthIn(min = 28.dp),
                textAlign = TextAlign.Center,
            )
            StepperButton(
                icon = Icons.Rounded.Add,
                description = stringResource(R.string.home_farm_increase),
                enabled = residentLimit < FarmWorldSummary.CAPACITY,
                onClick = { onResidentLimitChange(residentLimit + 1) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.home_farm_artwork),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimaryOnDark,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtFilterChip(
                    label = stringResource(R.string.home_farm_art_mixed),
                    selected = spriteFilter == FarmSpriteFilter.MIXED,
                    onClick = { onSpriteFilterChange(FarmSpriteFilter.MIXED) },
                )
                ArtFilterChip(
                    label = stringResource(R.string.home_farm_art_vb),
                    selected = spriteFilter == FarmSpriteFilter.VB_ONLY,
                    onClick = { onSpriteFilterChange(FarmSpriteFilter.VB_ONLY) },
                )
                ArtFilterChip(
                    label = stringResource(R.string.home_farm_art_dot),
                    selected = spriteFilter == FarmSpriteFilter.DOT_ONLY,
                    onClick = { onSpriteFilterChange(FarmSpriteFilter.DOT_ONLY) },
                )
            }
        }
    }
}

@Composable
private fun StepperButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(SurfaceElevatedPurple.copy(alpha = if (enabled) 0.9f else 0.4f))
            .border(1.dp, SurfaceStroke.copy(alpha = if (enabled) 0.9f else 0.4f), shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) VitalCyan else TextMutedOnDark,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ArtFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = CutCornerShape(8.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(
                if (selected) VitalCyan.copy(alpha = 0.16f) else SurfaceElevatedPurple.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (selected) VitalCyan else SurfaceStroke.copy(alpha = 0.8f),
                shape,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) VitalCyan else TextSecondaryOnDark,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeMenuSheet(
    navController: NavController,
    onDismiss: () -> Unit,
) {
    val entries = remember(navController) { menuEntries(navController) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.home_menu_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            )
            entries.forEach { entry ->
                MenuRow(
                    icon = entry.icon,
                    label = stringResource(entry.label),
                    detail = null,
                    tint = TextSecondaryOnDark,
                    onClick = {
                        onDismiss()
                        entry.onClick()
                    },
                )
            }
        }
    }
}

private data class MenuEntry(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

private fun menuEntries(navController: NavController): List<MenuEntry> {
    fun primary(item: NavigationItems): MenuEntry =
        MenuEntry(item.label, item.icon) { navController.navigatePrimary(item) }
    return listOf(
        // Settings first: it is the one entry people look for by name, and burying it under Credits
        // made the menu read as if settings did not exist. The gear in the bar is the fast path.
        primary(NavigationItems.Settings),
        primary(NavigationItems.Quests),
        primary(NavigationItems.Dex),
        primary(NavigationItems.Items),
        primary(NavigationItems.Adventure),
        MenuEntry(R.string.nav_transfer, R.drawable.baseline_nfc_24) {
            navController.navigate(TransferNavigation.route(TransferSection.DEVICES))
        },
        primary(NavigationItems.Lorebook),
        primary(NavigationItems.Viewer),
        primary(NavigationItems.Credits),
    )
}

@Composable
private fun MenuRow(
    @DrawableRes icon: Int,
    label: String,
    detail: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimaryOnDark,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        detail?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = TextMutedOnDark)
        }
    }
}
