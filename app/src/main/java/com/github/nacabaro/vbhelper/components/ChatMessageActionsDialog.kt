package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R

@Composable
fun ChatMessageActionsDialog(text: String, laterCount: Int, canRegenerate: Boolean, busy: Boolean,
    onDismiss: () -> Unit, onCopy: () -> Unit, onRegenerate: () -> Unit, onDelete: () -> Unit) {
    var decision by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(stringResource(when (decision) {
            "delete" -> R.string.audit_delete_from_here
            "regenerate" -> R.string.audit_resend_from_here
            else -> R.string.ui_message_actions
        })) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (decision != null) {
                    Text(stringResource(if (decision == "delete") R.string.audit_delete_branch else R.string.audit_resend_branch, laterCount))
                    Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                } else {
                    VitalButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_copy)) }
                    if (canRegenerate) VitalButton(onClick = { decision = "regenerate" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.audit_resend_from_here))
                    }
                    VitalButton(onClick = { decision = "delete" }, enabled = !busy, style = VitalButtonStyle.DESTRUCTIVE,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.audit_delete_from_here)) }
                }
            }
        },
        dismissButton = { TextButton(onClick = { if (decision == null) onDismiss() else decision = null }) { Text(stringResource(R.string.ui_cancel)) } },
        confirmButton = {
            if (decision != null) VitalButton(enabled = !busy,
                style = if (decision == "delete") VitalButtonStyle.DESTRUCTIVE else VitalButtonStyle.PRIMARY,
                onClick = { if (decision == "delete") onDelete() else onRegenerate() }) {
                Text(stringResource(if (decision == "delete") R.string.audit_delete_from_here else R.string.audit_confirm_regenerate))
            }
        })
}
