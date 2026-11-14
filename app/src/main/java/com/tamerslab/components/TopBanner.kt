package com.tamerslab.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
 * It is the same top-cluster type Home, the Radar place and the battle surface use: a palette-resolved
 * scrim instead of the old gradient shelf, a left-aligned title carrying one quiet context line, a
 * single hairline junction and bare 48dp keys (icon only, no corner frame) like Home's HUD.
 *
 * The back key always owns the left edge. When a caller does not pass [onBackClick] the header uses the
 * activity's back dispatcher, so it behaves exactly like the system back gesture for that screen
 * (including screens with their own back handling). Every other action the header is given sits on the
 * right, in the order below. The shell already insets non-Home screens, so the header reserves no
 * system insets of its own.
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
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val backAction: (() -> Unit)? = onBackClick
        ?: dispatcher?.let { owner -> { owner.onBackPressed() } }

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
            if (backAction != null) {
                HudKey(
                    icon = R.drawable.baseline_arrow_back_24,
                    description = stringResource(R.string.ui_back_icon),
                    onClick = backAction,
                )
            }
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                onTransferClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_file_upload_24,
                        description = stringResource(R.string.nav_transfer),
                        onClick = it,
                    )
                }
                onScanDataClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_data_24,
                        description = stringResource(R.string.digimon_scan_title),
                        onClick = it,
                    )
                }
                onImportClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_file_upload_24,
                        description = stringResource(R.string.cards_import),
                        onClick = it,
                        enabled = importEnabled,
                    )
                }
                onScanClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_nfc_24,
                        description = stringResource(R.string.ui_scan),
                        onClick = it,
                    )
                }
                onModifyClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_edit_24,
                        description = stringResource(R.string.cards_edit),
                        onClick = it,
                    )
                }
                onAdventureClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_fort_24,
                        description = stringResource(R.string.ui_adventure),
                        onClick = it,
                    )
                }
                onGearClick?.let {
                    HudKey(
                        icon = R.drawable.baseline_settings_24,
                        description = stringResource(R.string.ui_settings),
                        onClick = it,
                    )
                }
                trailingAction?.let { Box { it() } }
            }
        }
        HudHairline()
    }
}

/** Extra header actions inherit the cluster key geometry instead of opening a second toolbar style. */
@Composable
fun TopBannerAction(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    HudKey(icon = icon, description = description, onClick = onClick, enabled = enabled)
}
