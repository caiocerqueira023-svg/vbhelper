package com.github.nacabaro.vbhelper.screens.homeScreens.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R

@Composable
fun DegenerateDialog(
    targetStage: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_degenerate)) },
        text = {
            Text(
                stringResource(R.string.ui_degenerate_description, targetStage)
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ui_degenerate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    )
}
