package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.R

@Composable
fun CardRenameDialog(
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    currentName: String
) {
    var cardName by remember { mutableStateOf(currentName) }

    Dialog(
        onDismissRequest = onDismiss

    ) {
        CyberPanel(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.card_rename_title),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimaryOnDark
            )
            OutlinedTextField(
                value = cardName,
                onValueChange = { cardName = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
                    onClick = {
                        onRename(cardName)
                        onDismiss()
                    },
                    enabled = cardName.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.ui_save))
                }
            }
        }
    }
}
