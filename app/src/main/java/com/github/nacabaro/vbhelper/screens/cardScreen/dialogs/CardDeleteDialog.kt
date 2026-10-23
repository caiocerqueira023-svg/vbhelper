package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.*

@Composable
fun CardDeleteDialog(cardName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    BoundedActionDialog(onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        VitalButton(onClick = onConfirm, style = VitalButtonStyle.DESTRUCTIVE) { Text(stringResource(R.string.ui_delete)) }
    }) {
        Text(stringResource(R.string.card_delete_title, cardName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.card_delete_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
