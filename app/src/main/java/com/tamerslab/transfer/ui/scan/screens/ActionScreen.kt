package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.R
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/** A waiting step of the NFC flow: the device must be held against the reader. */
@Composable
fun ActionScreen(
    topBannerText: String,
    onClickCancel: () -> Unit,
    showHeader: Boolean = true,
) {
    ScanStepScaffold(title = topBannerText, showHeader = showHeader, onBack = onClickCancel) {
        CyberPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            active = true
        ) {
            Icon(
                imageVector = Icons.Default.Nfc,
                contentDescription = null,
                tint = VitalCyan,
                modifier = Modifier.size(40.dp).align(Alignment.CenterHorizontally)
            )
            Text(
                text = stringResource(R.string.action_place_near_reader),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark
            )
            Text(
                text = stringResource(R.string.scan_keep_still),
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryOnDark
            )
        }
        VitalButton(
            onClick = onClickCancel,
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
