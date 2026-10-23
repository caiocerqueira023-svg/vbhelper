package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.*
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus

@Composable
fun CardOriginDialog(cardName: String, onDismiss: () -> Unit, onSelect: (OfficialStatus) -> Unit, isImporting: Boolean = false) {
    BoundedActionDialog(onDismiss, actions = {
        VitalButton(onClick = { onSelect(OfficialStatus.OFFICIAL) }) { Text(stringResource(R.string.ui_official)) }
        VitalButton(onClick = { onSelect(OfficialStatus.CUSTOM) }) { Text(stringResource(R.string.ui_custom)) }
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
    }) {
        Text(stringResource(R.string.ui_official_card, cardName), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.card_origin_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (isImporting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.ui_dim_importing))
        }
    }
}
