package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R

@Composable
fun SpeciesPickerDialog(
    speciesNames: List<String>,
    onDismiss: () -> Unit,
    onSpeciesSelected: (String) -> Unit,
    isLoading: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredSpecies = remember(speciesNames, searchQuery) {
        speciesNames.filter { it.contains(searchQuery.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_choose_species_from_dim)) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(stringResource(R.string.ui_search_species)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLoading && filteredSpecies.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(24.dp),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filteredSpecies) { name ->
                        TextButton(
                            onClick = { onSpeciesSelected(name) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name)
                        }
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
}
