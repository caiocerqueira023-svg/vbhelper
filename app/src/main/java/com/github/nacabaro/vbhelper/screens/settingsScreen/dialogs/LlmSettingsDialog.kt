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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
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
import com.github.nacabaro.vbhelper.chat.ChatApiProvider
import com.github.nacabaro.vbhelper.source.LlmProviderSettings
import androidx.compose.ui.window.DialogProperties

@Composable
fun LlmSettingsDialog(
    currentApiKey: String?,
    currentModel: String,
    currentBaseUrl: String,
    currentProvider: ChatApiProvider,
    savedProviderSettings: Map<ChatApiProvider, LlmProviderSettings>,
    onDismiss: () -> Unit,
    onSave: (provider: ChatApiProvider, apiKey: String, model: String, baseUrl: String) -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey.orEmpty()) }
    var model by remember(currentModel) { mutableStateOf(currentModel) }
    var baseUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var selectedProvider by remember(currentProvider) {
        mutableStateOf(currentProvider)
    }
    var showProviderPicker by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.ui_chat_api_settings),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer2()

                OutlinedButton(
                    onClick = { showProviderPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${stringResource(R.string.ui_chat_api_provider)}: ${selectedProvider.displayName}")
                }

                Spacer2()

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        if (selectedProvider != ChatApiProvider.LITELLM) {
                            selectedProvider = ChatApiProvider.CUSTOM
                        }
                    },
                    label = { Text(stringResource(R.string.ui_chat_api_base_url)) },
                    supportingText = { Text(stringResource(R.string.ui_chat_api_base_url_help)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
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
                        enabled = apiKey.isNotBlank() && baseUrl.startsWith("https://"),
                        onClick = { onSave(selectedProvider, apiKey, model, baseUrl) },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }

    if (showProviderPicker) {
        AlertDialog(
            onDismissRequest = { showProviderPicker = false },
            title = { Text(stringResource(R.string.ui_chat_api_provider)) },
            text = {
                Column {
                    ChatApiProvider.entries.forEach { provider ->
                        TextButton(
                            onClick = {
                                selectedProvider = provider
                                val saved = savedProviderSettings[provider]
                                apiKey = saved?.apiKey.orEmpty()
                                model = saved?.model ?: provider.suggestedModel ?: "openrouter/auto"
                                baseUrl = saved?.baseUrl ?: provider.baseUrl.orEmpty()
                                showProviderPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(provider.displayName, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderPicker = false }) {
                    Text(stringResource(R.string.ui_close))
                }
            }
        )
    }
}

@Composable
private fun Spacer2() {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(4.dp))
}
