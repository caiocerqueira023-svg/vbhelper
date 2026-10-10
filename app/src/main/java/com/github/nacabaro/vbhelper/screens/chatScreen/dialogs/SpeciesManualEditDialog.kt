package com.github.nacabaro.vbhelper.screens.chatScreen.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.editorDialogBounds
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import androidx.compose.ui.window.DialogProperties

data class SpeciesManualEditResult(
    val name: String,
    val level: String?,
    val type: String?,
    val profile: String?,
    val specialMoves: List<String>
)

@Composable
fun SpeciesManualEditDialog(
    cardName: String,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onSave: (SpeciesManualEditResult) -> Unit
) {
    var name by rememberSaveable(cardName) { mutableStateOf("") }
    var level by rememberSaveable(cardName) { mutableStateOf("") }
    var type by rememberSaveable(cardName) { mutableStateOf("") }
    var profile by rememberSaveable(cardName) { mutableStateOf("") }
    var specialMoves by rememberSaveable(cardName) { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, decorFitsSystemWindows = false)
    ) {
        Card(modifier = Modifier.editorDialogBounds()) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState())
            ) {
                Text(stringResource(R.string.ui_species_question), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.app_species_card_hint, cardName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.ui_species_name_required)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(level, { level = it }, label = { Text(stringResource(R.string.ui_optional_level)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(type, { type = it }, label = { Text(stringResource(R.string.ui_optional_type)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(specialMoves, { specialMoves = it }, label = { Text(stringResource(R.string.ui_special_moves)) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = profile,
                    onValueChange = { profile = it },
                    label = {                     Text(stringResource(R.string.ui_optional_profile)) },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.ui_skip)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
                    VitalButton(
                        style = com.github.nacabaro.vbhelper.components.VitalButtonStyle.PRIMARY,
                        enabled = name.isNotBlank(),
                        onClick = {
                            onSave(
                                SpeciesManualEditResult(
                                    name = name.trim(),
                                    level = level.trim().ifBlank { null },
                                    type = type.trim().ifBlank { null },
                                    profile = profile.trim().ifBlank { null },
                                    specialMoves = specialMoves.split(',').map(String::trim).filter(String::isNotEmpty)
                                )
                            )
                        }
                    ) { Text(stringResource(R.string.ui_save)) }
                }
            }
        }
    }
}
