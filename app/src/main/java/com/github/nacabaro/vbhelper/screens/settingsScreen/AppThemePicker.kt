package com.github.nacabaro.vbhelper.screens.settingsScreen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.AppTheme

@Composable
fun AppThemePicker(selectedTheme: AppTheme, onThemeSelected: (AppTheme) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup()) {
        Text(stringResource(R.string.settings_theme_title), style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.settings_theme_description), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 3.dp, bottom = 8.dp))
        AppTheme.entries.forEach { theme ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .selectable(selected = theme == selectedTheme, role = Role.RadioButton,
                        onClick = { onThemeSelected(theme) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = theme == selectedTheme, onClick = null)
                Text(theme.displayName, modifier = Modifier.weight(1f).padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    theme.previewColors.forEach { color ->
                        Box(Modifier.size(16.dp).background(color, MaterialTheme.shapes.extraSmall))
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
    }
}
