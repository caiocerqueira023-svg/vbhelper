package com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.chat.DigimonPersonaBuilder
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton

@Composable
fun PromptTemplateDialog(
    currentTemplate: String?,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
    title: String = stringResource(R.string.ui_digimon_prompt),
    defaultTemplate: String = DigimonPersonaBuilder.DEFAULT_SYSTEM_PROMPT_TEMPLATE.trimIndent()
) {
    var template by remember(currentTemplate) {
        mutableStateOf(currentTemplate ?: defaultTemplate)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(dismissOnClickOutside = false)) {
        Card {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.ui_prompt_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "{Tamer}, {species_name}, {matched_name}, {species_profile}, {species_profile_block}, {species_level}, {species_type}, {special_moves}, {temperament}, {social_style}, {speech_quirk}, {personality_block}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                OutlinedTextField(
                    value = template,
                    onValueChange = { template = it },
                    label = { Text(stringResource(R.string.ui_system_prompt)) },
                    minLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { template = defaultTemplate }) {
                        Text(stringResource(R.string.ui_restore_default))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    VitalButton(onClick = { onSave(template.trim().ifBlank { null }) }) { Text(stringResource(R.string.ui_save)) }
                }
            }
        }
    }
}
