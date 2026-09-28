package com.github.nacabaro.vbhelper.screens.lorebookScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import com.github.nacabaro.vbhelper.ui.theme.SpaceBlack
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright

@Composable
fun LorebookScreen(
    navController: NavController,
    controller: LorebookScreenControllerImpl
) {
    val entries by controller.getEntries().collectAsState(initial = emptyList())
    var editingEntry by remember { mutableStateOf<LorebookEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopBanner(
                text = stringResource(R.string.ui_lorebook_title),
                onBackClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                shape = CutCornerShape(10.dp),
                containerColor = VitalPurpleBright,
                contentColor = SpaceBlack,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.ui_add)
                )
            }
        }
    ) { contentPadding ->
        Column(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.ui_lorebook_description),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryOnDark,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (entries.isEmpty()) {
                CyberEmptyState(
                    message = stringResource(R.string.ui_lorebook_empty),
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
                        CyberPanel(
                            modifier = Modifier.fillMaxWidth(),
                            active = entry.enabled
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        entry.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimaryOnDark
                                    )
                                    Text(
                                        entry.triggerKeys.joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (entry.enabled) VitalCyan else TextSecondaryOnDark
                                    )
                                }
                                Switch(
                                    checked = entry.enabled,
                                    onCheckedChange = { controller.setEnabled(entry, it) }
                                )
                                IconButton(onClick = { editingEntry = entry }) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.ui_edit)
                                    )
                                }
                                IconButton(onClick = { controller.deleteEntry(entry) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.ui_delete)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        LorebookEntryDialog(
            entry = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, keys, content, priority ->
                controller.saveEntry(null, title, keys, content, priority) {
                    showAddDialog = false
                }
            }
        )
    }
    editingEntry?.let { entry ->
        LorebookEntryDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { title, keys, content, priority ->
                controller.saveEntry(entry.id, title, keys, content, priority) {
                    editingEntry = null
                }
            }
        )
    }
}
