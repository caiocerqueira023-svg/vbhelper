package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Persist actions when room permits; in a very short window every action remains scroll-reachable. */
@Composable
fun BoundedActionDialog(onDismiss: () -> Unit, actions: @Composable () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(decorFitsSystemWindows = false)) {
        val density = LocalDensity.current
        val available = with(density) { (LocalWindowInfo.current.containerSize.height - WindowInsets.ime.getBottom(density)).coerceAtLeast(0).toDp() }
        val shortWindow = available < 280.dp || density.fontScale > 1.5f
        CyberPanel(Modifier.editorDialogBounds().fillMaxWidth()) {
            if (shortWindow) Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
            } else {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) { actions() }
            }
        }
    }
}
