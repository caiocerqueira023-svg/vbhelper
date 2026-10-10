package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.VitalButtonStyle

@Composable
fun StorageAdventureTimeDialog(onClickSendToAdventure: (Long) -> Unit, onDismissRequest: () -> Unit) {
    val times = listOf(360, 720, 1440)
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.app_choose_duration)) },
        text = {
            Column(Modifier.selectableGroup()) {
                times.forEachIndexed { index, minutes ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = selected == index, role = Role.RadioButton, onClick = { selected = index })
                        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = selected == index, onClick = null)
                        Text(stringResource(R.string.app_duration_hours, minutes / 60))
                    }
                }
            }
        },
        confirmButton = {
            VitalButton(style = VitalButtonStyle.PRIMARY, enabled = selected in times.indices, onClick = {
                if (selected in times.indices) { onClickSendToAdventure(times[selected].toLong()); onDismissRequest() }
            }) { Text(stringResource(R.string.storage_send_on_adventure)) }
        },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.ui_cancel)) } },
    )
}
