package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.*

@Composable
fun CardRenameDialog(onDismiss: () -> Unit, onRename: (String) -> Unit, currentName: String) {
    var name by rememberSaveable(currentName) { mutableStateOf(currentName) }
    BoundedActionDialog(onDismiss, actions = {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        VitalButton(onClick = { onRename(name); onDismiss() }, enabled = name.isNotBlank(), style = VitalButtonStyle.PRIMARY) {
            Text(stringResource(R.string.ui_save))
        }
    }) {
        Text(stringResource(R.string.card_rename_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.immersion_card_name)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
