package com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import androidx.compose.ui.window.DialogProperties

@Composable
fun LlmSettingsDialog(
    currentApiKey: String?,
    currentModel: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, model: String) -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey.orEmpty()) }
    var model by remember(currentModel) { mutableStateOf(currentModel) }
    var showKey by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Configurações de chat (OpenRouter)",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer2()

                Text(
                    text = stringResource(R.string.ui_api_key_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer2()

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(stringResource(R.string.ui_api_key)) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(onClick = { showKey = !showKey }) {
                    Text(stringResource(if (showKey) R.string.ui_hide_key else R.string.ui_show_key))
                }

                Spacer2()

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.ui_model)) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ui_model_example)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer2()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                    Button(
                        enabled = apiKey.isNotBlank(),
                        onClick = { onSave(apiKey, model) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer2() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
}