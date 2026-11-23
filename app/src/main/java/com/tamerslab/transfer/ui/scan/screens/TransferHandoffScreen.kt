package com.tamerslab.transfer.ui.scan.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.components.motionEnabled
import com.tamerslab.transfer.ui.scan.components.VbLabMaterializeEffect
import com.tamerslab.transfer.ui.scan.components.NfcTransferDirection
import com.tamerslab.transfer.ui.scan.components.toTransferMode
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.utils.ImageBitmapData
import kotlinx.coroutines.delay
import com.tamerslab.transfer.ui.scan.components.DigimonTransferMode

/**
 * The successful hand-off beat after the watch confirms a read/write.
 *
 * The original VB Lab played the character reveal/dissolve mask over the scan ring as the
 * character left or arrived. The waiting step already uses the same extracted assets; this screen
 * keeps them on-screen for the arrival/departure mask length before handing control back, instead
 * of cutting straight to the destination.
 */
@Composable
fun TransferHandoffScreen(
    topBannerText: String,
    direction: NfcTransferDirection,
    showHeader: Boolean,
    characterPreview: ImageBitmapData? = null,
    onFinished: () -> Unit,
) {
    val motion = motionEnabled()
    val mode = direction.toTransferMode()
    val finished by rememberUpdatedState(onFinished)
    val holdMillis = if (motion) {
        if (mode == DigimonTransferMode.ARRIVING) 2_650L else 3_050L
    } else {
        750L
    }

    LaunchedEffect(Unit) {
        delay(holdMillis)
        finished()
    }

    ScanStepScaffold(title = topBannerText, showHeader = showHeader, onBack = null) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            VbLabMaterializeEffect(
                mode = mode,
                sprite = characterPreview,
                modifier = Modifier,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(
                if (mode == DigimonTransferMode.ARRIVING) {
                    R.string.scan_received_character_success
                } else {
                    R.string.scan_sent_character_success
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimaryOnDark,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                if (mode == DigimonTransferMode.ARRIVING) {
                    R.string.scan_handoff_received_note
                } else {
                    R.string.scan_handoff_sent_note
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondaryOnDark,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(24.dp),
        )
    }
}
