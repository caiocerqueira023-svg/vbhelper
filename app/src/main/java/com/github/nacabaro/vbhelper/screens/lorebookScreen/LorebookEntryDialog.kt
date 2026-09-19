package com.github.nacabaro.vbhelper.screens.lorebookScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.components.VitalButton
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry

@Composable
fun LorebookEntryDialog(
    entry: LorebookEntry?,
    onDismiss: () -> Unit,
    onSave: (String, List<String>, String, Int) -> Unit
) {
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var keys by remember { mutableStateOf(entry?.triggerKeys?.joinToString(", ").orEmpty()) }
    var content by remember { mutableStateOf(entry?.content.orEmpty()) }
    var priority by remember { mutableStateOf((entry?.priority ?: 0).toString()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Card {
            Column(
                Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(
                        if (entry == null) R.string.lorebook_new_entry
                        else R.string.lorebook_edit_entry
                    ),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.lorebook_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keys,
                    onValueChange = { keys = it },
                    label = { Text(stringResource(R.string.lorebook_keys)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.lorebook_content)) },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = priority,
                    onValueChange = { priority = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.lorebook_priority)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.ui_cancel))
                    }
                    VitalButton(
                        enabled = title.isNotBlank() && keys.isNotBlank() && content.isNotBlank(),
                        onClick = {
                            onSave(
                                title.trim(),
                                keys.split(",").map(String::trim).filter(String::isNotEmpty),
                                content.trim(),
                                priority.toIntOrNull() ?: 0
                            )
                        }
                    ) {
                        Text(stringResource(R.string.ui_save))
                    }
                }
            }
        }
    }
}
