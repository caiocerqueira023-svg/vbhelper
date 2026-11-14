package com.tamerslab.farm.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.tamerslab.R
import com.tamerslab.components.VitalButton
import com.tamerslab.components.VitalButtonStyle
import com.tamerslab.navigation.NavigationItems
import com.tamerslab.navigation.TransferNavigation
import com.tamerslab.navigation.TransferSection
import com.tamerslab.navigation.navigatePrimary
import com.tamerslab.farm.FarmSpriteFilter
import com.tamerslab.farm.ui.digifarm.FarmMemberOption
import com.tamerslab.farm.ui.digifarm.FarmWorldResident
import com.tamerslab.farm.ui.digifarm.FarmWorldSummary
import com.tamerslab.storage.ui.StorageCharacterPickerDialog
import com.tamerslab.ui.theme.SurfaceElevatedPurple
import com.tamerslab.ui.theme.SurfaceStroke
import com.tamerslab.ui.theme.TextMutedOnDark
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/**
 * Top status zone of the farm home (PRESENTATION_REDESIGN_PLAN.md §5.1).
 *
 * No shelf, no box: the HUD is two detached clusters on the world - status on the left (home glyph,
 * resident count with its place gauge, and live bits) and the three utility keys on the right. A soft
 * scrim and a hairline ground line carry the contrast that a panel used to carry, so the rail reads as
 * an overlay on the Digifarm instead of a toolbar sitting on top of it.
 */
@Composable
fun HomeTopHud(
    residentCount: Int,
    capacity: Int,
    bits: Int,
    onFarmClick: () -> Unit,
    onTransferClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hudTopScrim()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status cluster keeps its natural width on the left; the rest of the bar is the separator,
            // and the three actions sit together on the right - starting past the middle of the screen,
            // so the currency and the farm count are never crowded by them. Settings lives in the menu
            // sheet (its first entry), which is why the bar carries three keys rather than four.
            FarmIdentity(
                residentCount = residentCount,
                capacity = capacity,
                onClick = onFarmClick,
            )
            Spacer(Modifier.width(12.dp))
            BitsReadout(bits = bits)
            Spacer(Modifier.weight(1f))
            HudKey(
                icon = R.drawable.baseline_nfc_24,
                description = stringResource(R.string.nav_transfer),
                onClick = onTransferClick,
            )
            Spacer(Modifier.width(6.dp))
            HudKey(
                icon = Icons.Rounded.Settings,
                description = stringResource(R.string.home_settings_description),
                onClick = onSettingsClick,
            )
            Spacer(Modifier.width(6.dp))
            HudKey(
                icon = Icons.Rounded.Menu,
                description = stringResource(R.string.home_menu_description),
                onClick = onMenuClick,
            )
        }
        HudGroundLine()
    }
}

@Composable
private fun FarmIdentity(
    residentCount: Int,
    capacity: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.home_farm_chip_description)
    // Over the world the clusters have to hold up at any text size: past 1.2 the count drops one type
    // role, which is what keeps the action keys on screen on a 360dp phone.
    val compact = LocalDensity.current.fontScale > 1.2f
    val shadow = hudTextShadow()
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(CutCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!compact) {
            Icon(
                imageVector = Icons.Rounded.Home,
                contentDescription = null,
                tint = VitalCyan,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_farm_residents, residentCount, capacity),
                    style = if (compact) {
                        MaterialTheme.typography.titleMedium.copy(shadow = shadow)
                    } else {
                        MaterialTheme.typography.titleLarge.copy(shadow = shadow)
                    },
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryOnDark,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = 14.sp,
                        maxFontSize = if (compact) {
                            MaterialTheme.typography.titleMedium.fontSize
                        } else {
                            MaterialTheme.typography.titleLarge.fontSize
                        },
                        stepSize = 0.5.sp,
                    ),
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = TextSecondaryOnDark,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            ResidentPlaceGauge(
                filled = residentCount,
                capacity = capacity,
                modifier = Modifier.fillMaxWidth().height(4.dp),
            )
        }
    }
}

/**
 * The count as an instrument reading: "3/12" alone is cryptic, so the same fraction is drawn as one
 * slot per farm place. Occupied slots are accent; empty slots are ink at low alpha, which keeps the
 * gauge readable on any sky without a panel behind it.
 */
@Composable
private fun ResidentPlaceGauge(
    filled: Int,
    capacity: Int,
    modifier: Modifier = Modifier,
) {
    val slots = capacity.coerceIn(1, 24)
    val on = VitalCyan
    val off = TextPrimaryOnDark.copy(alpha = 0.22f)
    Canvas(modifier) {
        val gap = 1.dp.toPx()
        val slotWidth = ((size.width - gap * (slots - 1)) / slots).coerceAtLeast(1f)
        for (index in 0 until slots) {
            drawRect(
                color = if (index < filled) on else off,
                topLeft = Offset(index * (slotWidth + gap), 0f),
                size = Size(slotWidth, size.height),
            )
        }
    }
}

/** Live currency, floating: the accent carries the numerals so the eye lands on the number. */
@Composable
private fun BitsReadout(bits: Int) {
    val description = stringResource(R.string.items_store_credits, bits)
    val shadow = hudTextShadow()
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = bits.toString(),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFeatureSettings = "tnum",
                shadow = shadow,
            ),
            color = VitalCyan,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HudKey(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    HudKeySlot(description = description, onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextPrimaryOnDark.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HudKey(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
) {
    HudKeySlot(description = description, onClick = onClick) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = TextPrimaryOnDark.copy(alpha = 0.92f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HudKeyCell(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun HudKeySlot(
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CutCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** The HUD's ground line: a single hairline that ends the status zone without enclosing it. */
@Composable
private fun HudGroundLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp)
            .height(1.dp)
            .background(TextPrimaryOnDark.copy(alpha = 0.12f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FarmRosterSheet(
    options: List<FarmMemberOption>,
    residentLimit: Int,
    spriteFilter: FarmSpriteFilter,
    onResidentLimitChange: (Int) -> Unit,
    onSpriteFilterChange: (FarmSpriteFilter) -> Unit,
    onToggleMember: (characterId: Long, member: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Members only, plus the same full-screen partner picker the Arena uses for choosing a partner:
    // adding someone to the island is a collection choice, not a different list to learn.
    val members = options.filter { it.isMember }
    val candidates = options.filterNot { it.isMember }
    val capacity = residentLimit.coerceAtMost(FarmWorldSummary.CAPACITY)
    val room = (capacity - members.size).coerceAtLeast(0)
    var picking by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (picking) picking = false else onDismiss() },
    ) {
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
            // Quantity and membership live together: this is the one place that decides how the
            // island is populated, so the limit, the artwork and the partner list never disagree.
            FarmSettingsControls(
                residentLimit = residentLimit,
                shownCount = members.size,
                candidateCount = options.size,
                spriteFilter = spriteFilter,
                onResidentLimitChange = onResidentLimitChange,
                onSpriteFilterChange = onSpriteFilterChange,
            )
            Text(
                text = stringResource(R.string.home_roster_members_title),
                style = MaterialTheme.typography.labelLarge,
                color = VitalCyan,
                modifier = Modifier.padding(top = 10.dp),
            )
            members.forEach { option ->
                MenuRow(
                    icon = R.drawable.baseline_mood_24,
                    label = option.name,
                    detail = if (option.isActive) {
                        stringResource(R.string.home_roster_active_partner)
                    } else {
                        stringResource(R.string.home_roster_in_farm)
                    },
                    tint = if (option.isActive) VitalCyan else TextSecondaryOnDark,
                    onClick = {
                        // The active partner always lives on the island.
                        if (!option.isActive) onToggleMember(option.characterId, false)
                    },
                    trailingIcon = if (option.isActive) null else Icons.Rounded.Remove,
                )
            }
            if (room > 0) {
                VitalButton(
                    onClick = { picking = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = 8.dp),
                    style = VitalButtonStyle.SECONDARY,
                ) {
                    Text(stringResource(R.string.home_roster_add_member))
                }
            }
        }
    }

    if (picking) {
        StorageCharacterPickerDialog(
            characters = candidates.map { it.character },
            title = stringResource(R.string.home_roster_add_member),
            emptyMessage = stringResource(R.string.home_roster_pick_empty),
            onDismiss = { picking = false },
            onCharacterSelected = { characterId ->
                if (room > 0) onToggleMember(characterId, true)
                // Close on the last free slot; the island must never promise more than it can show.
                if (room <= 1) picking = false
            },
        )
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
    trailingIcon: ImageVector? = null,
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
        trailingIcon?.let { glyph ->
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
