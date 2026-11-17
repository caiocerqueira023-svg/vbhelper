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
import com.tamerslab.transfer.ui.scan.components.NfcGuidePhase
import com.tamerslab.transfer.ui.scan.components.NfcTransferDirection
import com.tamerslab.transfer.ui.scan.components.NfcTransferGuide
import com.tamerslab.transfer.ui.scan.components.NfcTransferGuideLogic
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.utils.ImageBitmapData

/**
 * A waiting step of the NFC flow: the device must be held against the reader.
 *
 * [direction] selects the explainer illustration and the hand-off animation: receiving a Digimon
 * from the watch uses the "arriving" variant, sending one to the watch uses the "departing" one.
 * [characterPreview] is optional; when the character artwork is available the hand-off animates the
 * real sprite, otherwise the component draws its DigiCore fallback.
 */
@Composable
fun ActionScreen(
    topBannerText: String,
    onClickCancel: () -> Unit,
    direction: NfcTransferDirection,
    showHeader: Boolean = true,
    phase: NfcGuidePhase = NfcGuidePhase.WAITING,
    characterPreview: ImageBitmapData? = null,
) {
    ScanStepScaffold(title = topBannerText, showHeader = showHeader, onBack = onClickCancel) {
        CyberPanel(
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
            active = true
        ) {
            NfcTransferGuide(
                direction = direction,
                phase = phase,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            DigimonTransferAura(
                mode = NfcTransferGuideLogic.modeFor(direction),
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
