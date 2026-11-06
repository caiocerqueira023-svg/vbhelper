package com.tamerslab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark

/**
 * Page header for every screen that is not one of the full-bleed worlds.
 *
 * This is the same top-cluster type Home, the Radar place and the battle surface use, applied to a
 * page: a palette-resolved scrim instead of the old gradient shelf, one leading key, a left-aligned
 * title carrying a single quiet context line, cut-corner 48dp keys at the trailing edge, and one
 * hairline junction. The shell already insets non-Home screens, so the header reserves no system
 * insets of its own.
 */
@Composable
fun TopBanner(
    text: String,
    modifier: Modifier = Modifier,
    onGearClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    onScanClick: (() -> Unit)? = null,
    onAdventureClick: (() -> Unit)? = null,
    onModifyClick: (() -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    importEnabled: Boolean = true,
    onScanDataClick: (() -> Unit)? = null,
    subtitle: String? = null,
    trailingAction: (@Composable () -> Unit)? = null,
    onTransferClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .hudTopScrim(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerLeadingKey(
                onTransferClick = onTransferClick,
                onScanDataClick = onScanDataClick,
                onImportClick = onImportClick,
                importEnabled = importEnabled,
                onScanClick = onScanClick,
                onBackClick = onBackClick,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = text,
                    color = TextPrimaryOnDark,
                    style = MaterialTheme.typography.titleMedium.hudInk(),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = TextSecondaryOnDark,
                        style = MaterialTheme.typography.labelMedium.hudInk(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            BannerTrailingKey(
                onGearClick = onGearClick,
                onAdventureClick = onAdventureClick,
                onModifyClick = onModifyClick,
                trailingAction = trailingAction,
            )
        }
        HudHairline()
    }
}

@Composable
private fun BannerLeadingKey(
    onTransferClick: (() -> Unit)?,
    onScanDataClick: (() -> Unit)?,
    onImportClick: (() -> Unit)?,
    importEnabled: Boolean,
    onScanClick: (() -> Unit)?,
    onBackClick: (() -> Unit)?,
) {
    when {
        onTransferClick != null -> HudKey(
            icon = R.drawable.baseline_file_upload_24,
            description = stringResource(R.string.nav_transfer),
            onClick = onTransferClick,
        )
        onScanDataClick != null -> HudKey(
            icon = R.drawable.baseline_data_24,
            description = stringResource(R.string.digimon_scan_title),
            onClick = onScanDataClick,
        )
        onImportClick != null -> HudKey(
            icon = R.drawable.baseline_file_upload_24,
            description = stringResource(R.string.cards_import),
            onClick = onImportClick,
            enabled = importEnabled,
        )
        onScanClick != null -> HudKey(
            icon = R.drawable.baseline_nfc_24,
            description = stringResource(R.string.ui_scan),
            onClick = onScanClick,
        )
        onBackClick != null -> HudKey(
            icon = R.drawable.baseline_arrow_back_24,
            description = stringResource(R.string.ui_back_icon),
            onClick = onBackClick,
        )
    }
}

@Composable
private fun BannerTrailingKey(
    onGearClick: (() -> Unit)?,
    onAdventureClick: (() -> Unit)?,
    onModifyClick: (() -> Unit)?,
    trailingAction: (@Composable () -> Unit)?,
) {
    val gap = Modifier.padding(start = 6.dp)
    when {
        onGearClick != null -> HudKey(
            icon = R.drawable.baseline_settings_24,
            description = stringResource(R.string.ui_settings),
            onClick = onGearClick,
            modifier = gap,
        )
        onAdventureClick != null -> HudKey(
            icon = R.drawable.baseline_fort_24,
            description = stringResource(R.string.ui_adventure),
            onClick = onAdventureClick,
            modifier = gap,
        )
        onModifyClick != null -> HudKey(
            icon = R.drawable.baseline_edit_24,
            description = stringResource(R.string.cards_edit),
            onClick = onModifyClick,
            modifier = gap,
        )
        trailingAction != null -> Box(gap) { trailingAction() }
    }
}

/** Extra header actions inherit the cluster key geometry instead of opening a second toolbar style. */
@Composable
fun TopBannerAction(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    HudKey(icon = icon, description = description, onClick = onClick, enabled = enabled)
}
