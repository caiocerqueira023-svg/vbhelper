package com.github.nacabaro.vbhelper.screens.settingsScreen.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R

private data class LanguageOption(
    val tag: String,
    val label: String
)

@Composable
fun LanguageDialog(
    selectedLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val languages = listOf(
        LanguageOption("system", stringResource(R.string.immersion_system_language)),
        LanguageOption("en", "English"),
        LanguageOption("pt-BR", "Português (Brasil)"),
        LanguageOption("ja", "日本語")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_language)) },
        text = {
            Column {
                languages.forEach { language ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = selectedLanguage == language.tag, role = Role.RadioButton,
                                onClick = { onLanguageSelected(language.tag) })
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedLanguage == language.tag,
                            onClick = null
                        )
                        Text(language.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_close_dialog))
            }
        }
    )
}
