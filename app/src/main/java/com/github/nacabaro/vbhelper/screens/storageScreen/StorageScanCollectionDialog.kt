package com.github.nacabaro.vbhelper.screens.storageScreen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.dtos.DigimonScanEntry
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexNameImage
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexSpritePortrait
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DigimonScanConversionDialog
import com.github.nacabaro.vbhelper.source.DigimonScanPolicy
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private sealed interface ScanCollectionLoad {
    data object Loading : ScanCollectionLoad
    data class Loaded(val entries: List<DigimonScanEntry>) : ScanCollectionLoad
    data object Failed : ScanCollectionLoad
}

@Composable
internal fun StorageScanCollectionDialog(database: AppDatabase, onClose: () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val collectionFlow = remember(database, retry) {
        database.digimonScanDao().observeCollection()
            .map<List<DigimonScanEntry>, ScanCollectionLoad> { ScanCollectionLoad.Loaded(it) }
            .catch { failure ->
                if (failure is CancellationException) throw failure
                emit(ScanCollectionLoad.Failed)
            }
    }
    val state by collectionFlow.collectAsState(ScanCollectionLoad.Loading)
    val entries = (state as? ScanCollectionLoad.Loaded)?.entries
    // Retain the selected preview through the emission that removes a consumed scan.
    var selectedEntry by remember { mutableStateOf<DigimonScanEntry?>(null) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var messageIsError by rememberSaveable { mutableStateOf(false) }
    val maximumHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .88f }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.widthIn(max = 480.dp).fillMaxWidth(.92f).heightIn(max = maximumHeight),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            StorageScanCollectionContent(entries, state == ScanCollectionLoad.Failed,
                message?.let { stringResource(it) }, messageIsError,
                onConvert = { entry -> message = null; selectedEntry = entry },
                onRetry = { retry++ }, onClose = onClose)
        }
    }
    selectedEntry?.let { selected ->
        val preview = entries?.firstOrNull { it.character.id == selected.character.id } ?: selected
        DigimonScanConversionDialog(database, preview.character, preview.cardName,
            onDismiss = { selectedEntry = null }, onFinished = { resultMessage, isError ->
                message = resultMessage
                messageIsError = isError
                selectedEntry = null
            })
    }
}

@Composable
internal fun StorageScanCollectionContent(
    entries: List<DigimonScanEntry>?,
    loadFailed: Boolean,
    feedback: String?,
    feedbackIsError: Boolean,
    onConvert: (DigimonScanEntry) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.storage_scan_collection_title), style = MaterialTheme.typography.titleLarge)
        when {
            loadFailed -> {
                Text(stringResource(R.string.storage_scan_load_failed), color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.storage_scan_retry))
                }
            }
            entries == null -> Text(stringResource(R.string.digimon_scan_loading))
            entries.isEmpty() -> Text(stringResource(R.string.storage_scan_empty), style = MaterialTheme.typography.bodyMedium)
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(entries, key = { it.character.id }) { entry ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            DexSpritePortrait(BitmapData(entry.character.spriteIdle, entry.character.spriteWidth, entry.character.spriteHeight),
                                Modifier.size(64.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DexNameImage(entry.character, false)
                                Text(entry.speciesName ?: stringResource(R.string.digimon_scan_species_fallback, entry.character.charaIndex + 1),
                                    style = MaterialTheme.typography.titleSmall)
                                Text(entry.cardName, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(stringResource(R.string.digimon_scan_percentage, entry.percentage),
                            style = MaterialTheme.typography.titleMedium, color = VitalCyan)
                        LinearProgressIndicator(progress = { entry.percentage / DigimonScanPolicy.CONVERSION_PERCENTAGE.toFloat() },
                            modifier = Modifier.fillMaxWidth(), color = VitalCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                        if (entry.percentage == DigimonScanPolicy.CONVERSION_PERCENTAGE)
                            Text(stringResource(R.string.digimon_scan_ready), style = MaterialTheme.typography.labelMedium)
                        Button(onClick = { onConvert(entry) }, enabled = entry.percentage == DigimonScanPolicy.CONVERSION_PERCENTAGE,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("scan-convert-${entry.character.id}")) {
                            Text(stringResource(R.string.digimon_scan_convert))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        feedback?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = if (feedbackIsError) MaterialTheme.colorScheme.error else VitalCyan,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.dex_chara_close_button))
        }
    }
}
