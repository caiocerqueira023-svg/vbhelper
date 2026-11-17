package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.tamerslab.transfer.ui.scan.components.DigimonTransferAura
import com.tamerslab.transfer.ui.scan.components.NfcTransferDirection
import com.tamerslab.transfer.ui.scan.components.toTransferMode
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.utils.ImageBitmapData

/**
 * A waiting step of the NFC flow: the device must be held against the reader.
 *
 * [direction] selects the Digimon hand-off animation: receiving a character from the watch plays the
 * "arriving" variant, sending one back plays the "departing" one. [characterPreview] is optional and
 * lets the hand-off animate the real card artwork instead of the original loading sprites.
 */
@Composable
fun ActionScreen(
    topBannerText: String,
    onClickCancel: () -> Unit,
    direction: NfcTransferDirection,
    showHeader: Boolean = true,
    characterPreview: ImageBitmapData? = null,
) {
    ScanStepScaffold(title = topBannerText, showHeader = showHeader, onBack = onClickCancel) {
        CyberPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            active = true
        ) {
            DigimonTransferAura(
                mode = direction.toTransferMode(),
                sprite = characterPreview,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(8.dp))
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
