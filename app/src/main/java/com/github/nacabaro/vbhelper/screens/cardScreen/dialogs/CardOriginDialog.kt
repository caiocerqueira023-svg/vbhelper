package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus

@Composable
fun CardOriginDialog(cardName: String, onDismiss: () -> Unit, onSelect: (OfficialStatus) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.ui_official_card, cardName), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Official cards have their species recognized automatically. " +
                        "For custom cards, you provide the species when opening chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onSelect(OfficialStatus.OFFICIAL) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_official))
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onSelect(OfficialStatus.CUSTOM) }, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.ui_custom))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_cancel)) }
            }
        }
    }
}
