package com.github.nacabaro.vbhelper.screens.homeScreens.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun DegenerateDialog(
    targetStage: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Regredir Digimon") },
        text = {
            Text(
                "Regredir para o estágio $targetStage custa 5.000 bits. " +
                    "Os vitais do Digimon serão zerados."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Regredir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
