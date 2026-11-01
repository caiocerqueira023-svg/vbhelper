package com.tamerslab.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tamerslab.R
import com.tamerslab.di.TamersLabApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpeciesPickerDialog(
    speciesNames: List<String>,
    onDismiss: () -> Unit,
    onSpeciesSelected: (String) -> Unit,
    isLoading: Boolean = false,
    editOnSelect: Boolean = false,
    title: String? = null,
) {
    val application = LocalContext.current.applicationContext as? TamersLabApplication
    val repository = application?.container?.speciesRepository
    val changes by (repository?.catalogChanges ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsStateWithLifecycle(emptyList())
    var refreshedNames by remember { mutableStateOf<List<String>?>(null) }
    var internallyLoading by remember { mutableStateOf(repository != null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingName by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(changes, speciesNames) {
        if (repository != null) {
            internallyLoading = true
            try { refreshedNames = withContext(Dispatchers.IO) { repository.getAllSpeciesNames() } }
            catch (failure: Exception) { if (failure is CancellationException) throw failure }
            finally { internallyLoading = false }
        }
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val names = refreshedNames ?: speciesNames
    val filteredSpecies = remember(names, searchQuery) {
        names.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val editLabel = stringResource(R.string.species_edit)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title ?: stringResource(R.string.ui_choose_species_from_dim)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.ui_search_species)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(enabled = application != null, onClick = { editingName = null; showEditor = true }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Add, stringResource(R.string.species_add))
                }
                }
                if ((isLoading || internallyLoading) && filteredSpecies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filteredSpecies) { name ->
                        Text(name, Modifier.fillMaxWidth().combinedClickable(role = Role.Button,
                            onClick = {
                                if (editOnSelect && application != null) { editingName = name; showEditor = true }
                                else onSpeciesSelected(name)
                            }, onLongClickLabel = editLabel,
                            onLongClick = { if (application != null) { editingName = name; showEditor = true } })
                            .padding(horizontal = 12.dp, vertical = 14.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ui_cancel))
            }
        }
    )
    if (showEditor && application != null) SpeciesCatalogEditorDialog(editingName,
        onDismiss = { showEditor = false }, onSaved = {
            showEditor = false
            scope.launch { refreshedNames = withContext(Dispatchers.IO) { application.container.speciesRepository.getAllSpeciesNames() } }
        })
}

