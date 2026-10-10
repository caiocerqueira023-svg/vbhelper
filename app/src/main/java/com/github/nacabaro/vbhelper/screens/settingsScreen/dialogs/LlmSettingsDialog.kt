package com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.chat.ChatApiProvider
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.cyberFrame
import com.github.nacabaro.vbhelper.ui.theme.SurfaceElevatedPurple
import com.github.nacabaro.vbhelper.source.DEFAULT_ROLEPLAY_TEMPERATURE
import com.github.nacabaro.vbhelper.source.LlmProviderSettings
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.components.editorDialogBounds
import androidx.compose.foundation.layout.FlowRow

@Composable
fun LlmSettingsDialog(
    currentApiKey: String?,
    currentModel: String,
    currentTemperature: Double,
    currentBaseUrl: String,
    currentProvider: ChatApiProvider,
    savedProviderSettings: Map<ChatApiProvider, LlmProviderSettings>,
    onDismiss: () -> Unit,
    onSave: (provider: ChatApiProvider, apiKey: String, model: String, baseUrl: String, temperature: Double) -> Unit
) {
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey.orEmpty()) }
    var model by remember(currentModel) { mutableStateOf(currentModel) }
    var temperature by remember(currentTemperature) { mutableStateOf(currentTemperature.toString()) }
    var baseUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }
    val parsedTemperature = temperature.toDoubleOrNull()
    var showKey by remember { mutableStateOf(false) }
    var selectedProvider by remember(currentProvider) {
        mutableStateOf(currentProvider)
    }
    var showProviderPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false,
        )
    ) {
        Card(
            modifier = Modifier.editorDialogBounds().fillMaxWidth().cyberFrame(active = true),
            shape = RectangleShape,
            colors = CardDefaults.cardColors(containerColor = SurfaceElevatedPurple)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.ui_chat_api_settings),
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer2()

                VitalButton(
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                selectedProvider.suggestedModel?.let { suggestedModel ->
                    TextButton(
                        onClick = { model = suggestedModel },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.ui_use_recommended_model, suggestedModel))
                    }
                }

                Spacer2()

                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it },
                    label = { Text(stringResource(R.string.ui_roleplay_temperature)) },
                    supportingText = { Text(stringResource(R.string.ui_roleplay_temperature_help)) },
                    singleLine = true,
                    isError = parsedTemperature == null || parsedTemperature !in 0.0..2.0,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer2()

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                    VitalButton(
                        style = com.github.nacabaro.vbhelper.components.VitalButtonStyle.PRIMARY,
                        enabled = apiKey.isNotBlank() &&
                            model.isNotBlank() &&
                            baseUrl.startsWith("https://") &&
                            parsedTemperature != null &&
                            parsedTemperature in 0.0..2.0,
                        onClick = {
                            onSave(
                                selectedProvider,
                                apiKey,
                                model,
                                baseUrl,
                                parsedTemperature ?: DEFAULT_ROLEPLAY_TEMPERATURE
                            )
                        },
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
                                model = saved?.model ?: provider.suggestedModel.orEmpty()
                                temperature = (saved?.temperature ?: DEFAULT_ROLEPLAY_TEMPERATURE).toString()
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
