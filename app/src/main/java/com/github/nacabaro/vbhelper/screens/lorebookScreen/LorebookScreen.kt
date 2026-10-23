package com.github.nacabaro.vbhelper.screens.lorebookScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.rememberCollectionLoad
import com.github.nacabaro.vbhelper.components.CollectionStatePanel
import com.github.nacabaro.vbhelper.components.CollectionRefreshNotice
import com.github.nacabaro.vbhelper.domain.lorebook.LorebookEntry
import com.github.nacabaro.vbhelper.ui.theme.OnVitalPrimary
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalPurpleBright

@Composable
fun LorebookScreen(
    navController: NavController,
    controller: LorebookScreenControllerImpl
) {
    val collection = rememberCollectionLoad(controller) { controller.getEntries() }
    val entries = collection.data.orEmpty()
    var editingEntry by remember { mutableStateOf<LorebookEntry?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingEntry by remember { mutableStateOf<LorebookEntry?>(null) }

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
                contentColor = OnVitalPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.ui_add)
                )
            }
        }
    ) { contentPadding ->
        LazyColumn(
            Modifier
                .padding(contentPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.ui_lorebook_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryOnDark,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            item {
                com.github.nacabaro.vbhelper.components.ResearchLibrary(Modifier.padding(bottom = 4.dp))
            }
            item { CollectionRefreshNotice(collection) }
            if (collection.blocking) item { CollectionStatePanel(collection, Modifier.fillMaxWidth().heightIn(min = 160.dp)) }
            else if (entries.isEmpty()) {
                item {
                    CyberEmptyState(
                        message = stringResource(R.string.ui_lorebook_empty),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                    items(entries, key = { it.id }) { entry ->
                        val noteLabel = stringResource(R.string.audit_enable_note, entry.title)
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
                                        style = MaterialTheme.typography.titleMedium,
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
                                    onCheckedChange = { controller.setEnabled(entry, it) },
                                    modifier = Modifier.semantics { contentDescription = noteLabel }
                                )
                            }
                            Text(entry.content, style = MaterialTheme.typography.bodyMedium, color = TextSecondaryOnDark,
                                maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { editingEntry = entry }) { Text(stringResource(R.string.ui_edit)) }
                                TextButton(onClick = { deletingEntry = entry }) { Text(stringResource(R.string.ui_delete), color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }

    deletingEntry?.let { entry -> AlertDialog(
        onDismissRequest = { deletingEntry = null },
        title = { Text(stringResource(R.string.immersion_delete_note, entry.title)) },
        text = { Text(stringResource(R.string.immersion_delete_note_description)) },
        confirmButton = { TextButton(onClick = { controller.deleteEntry(entry); deletingEntry = null }) { Text(stringResource(R.string.ui_delete)) } },
        dismissButton = { TextButton(onClick = { deletingEntry = null }) { Text(stringResource(R.string.ui_cancel)) } }
    ) }
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
