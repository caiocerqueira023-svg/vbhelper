package com.tamerslab.storage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tamerslab.R
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalButton
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.storage.data.BraceletConversionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Exact-slot conversion for catalog-defined partners. Lists only loaded DIM/BEM
 * slots of the same canonical species and form; the partner's app identity,
 * history and training are never rewritten by converting.
 */
@Composable
fun BraceletConversionDialog(
    characterId: Long,
    onDismiss: () -> Unit,
    onConverted: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as TamersLabApplication
    val scope = rememberCoroutineScope()
    var targets by remember { mutableStateOf<List<BraceletConversionService.CompatibleTarget>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var converting by remember { mutableStateOf(false) }

    LaunchedEffect(characterId) {
        try {
            targets = withContext(Dispatchers.IO) {
                BraceletConversionService(application.container.db).compatibleTargets(characterId)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TopBanner(text = stringResource(R.string.conversion_title), onBackClick = onDismiss)
            Text(
                stringResource(R.string.conversion_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when {
                failed -> Text(
                    stringResource(R.string.app_load_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                targets == null -> CircularProgressIndicator()
                targets!!.isEmpty() -> Text(
                    stringResource(R.string.conversion_empty),
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(targets!!, key = { it.cardCharacter.id }) { target ->
                        VitalButton(
                            enabled = !converting,
                            onClick = {
                                converting = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        runCatching {
                                            BraceletConversionService(application.container.db)
                                                .convert(characterId, target.cardCharacter.id)
                                        }
                                    }
                                    result.onSuccess { onConverted() }
                                        .onFailure {
                                            converting = false
                                            failed = true
                                        }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "${target.speciesName} · ${target.cardName} · " +
                                    if (target.isBEm) "BEM" else "DiM"
                            )
                        }
                    }
                }
            }
        }
    }
}
