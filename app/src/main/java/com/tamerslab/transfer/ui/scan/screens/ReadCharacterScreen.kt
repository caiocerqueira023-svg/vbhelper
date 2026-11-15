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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tamerslab.components.CyberPanel
import com.tamerslab.components.VitalButton
import com.tamerslab.R
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark
import com.tamerslab.ui.theme.VitalCyan

/** The read step's own instruction page, before the reader starts. */
@Composable
fun ReadCharacterScreen(
    onClickCancel: () -> Unit,
    onClickConfirm: () -> Unit,
    showHeader: Boolean = true,
) {
    ScanStepScaffold(
        title = stringResource(R.string.read_character_title),
        showHeader = showHeader,
        onBack = onClickCancel,
    ) {
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
                text = stringResource(R.string.read_character_prepare_device),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark
            )
            Text(
                text = stringResource(R.string.read_character_go_to_connect),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark
            )
        }
        VitalButton(
            onClick = onClickConfirm,
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.read_character_confirm))
        }
        // Inside the Transfer hub this page has no header of its own, so the step must offer its own
        // way back; standalone the header's back key already owns that action.
        if (!showHeader) {
            TextButton(onClick = onClickCancel) { Text(stringResource(R.string.ui_cancel)) }
        }
    }
}
