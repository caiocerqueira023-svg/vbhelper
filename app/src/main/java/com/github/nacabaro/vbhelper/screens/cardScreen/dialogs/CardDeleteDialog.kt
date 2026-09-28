package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.R

@Composable
fun CardDeleteDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss

    ) {
        CyberPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.card_delete_title, cardName),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark
            )
            Text(
                text = stringResource(R.string.card_delete_body),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VitalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.ui_cancel))
                }
                VitalButton(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    borderColor = StatusRed,
                    contentColor = StatusRed
                ) {
                    Text(text = stringResource(R.string.ui_delete))
                }
            }
        }
    }
}
