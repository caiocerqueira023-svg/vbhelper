package com.tamerslab.settings.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.ui.theme.TextPrimaryOnDark
import com.tamerslab.ui.theme.TextSecondaryOnDark

enum class TransferSimulationMode { RECEIVE, SEND }

@Composable
fun TransferSimulatorDialog(
    mode: TransferSimulationMode,
    characters: List<CharacterDtos.CharacterWithSprites>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var selectedId by remember(characters) {
        mutableStateOf(
            characters.firstOrNull { it.active }?.id ?: characters.firstOrNull()?.id,
        )
    }
    val send = mode == TransferSimulationMode.SEND
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.settings_debug_simulator_title)) },
        text = {
            Column {
                Text(stringResource(R.string.settings_debug_simulator_prompt))
                if (characters.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_debug_simulator_empty),
                        modifier = Modifier.padding(top = 12.dp),
                        color = TextSecondaryOnDark,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                        items(characters, key = { it.id }) { character ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selectedId == character.id,
                                        role = Role.RadioButton,
                                        onClick = { selectedId = character.id },
                                    )
                                    .padding(vertical = 6.dp),
                            ) {
                                RadioButton(
                                    selected = selectedId == character.id,
                                    onClick = null,
                                )
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(
                                        text = character.nickname
                                            ?: character.speciesName
                                            ?: "#${character.id}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimaryOnDark,
                                    )
                                    Text(
                                        text = "#${character.id}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondaryOnDark,
                                    )
                                }
                            }
                        }
                    }
                }
                if (send) {
                    Text(
                        stringResource(R.string.settings_debug_simulator_send_warning),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryOnDark,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedId != null && !busy,
                onClick = { selectedId?.let(onConfirm) },
            ) {
                Text(
                    if (busy) {
                        stringResource(R.string.settings_debug_simulator_busy)
                    } else {
                        stringResource(
                            if (send) {
                                R.string.settings_debug_simulator_send_action
                            } else {
                                R.string.settings_debug_simulator_receive_action
                            },
                        )
                    },
                )
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.ui_cancel))
            }
        },
    )
}
