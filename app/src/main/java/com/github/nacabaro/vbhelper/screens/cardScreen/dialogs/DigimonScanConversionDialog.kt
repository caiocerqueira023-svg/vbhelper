package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.screens.cardScreen.stageLabel
import com.github.nacabaro.vbhelper.source.DigimonScanPolicy
import com.github.nacabaro.vbhelper.source.DigimonScanRepository
import com.github.nacabaro.vbhelper.source.ScanConversionResult
import com.github.nacabaro.vbhelper.utils.BitmapData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The same preview and guarded conversion flow serves Dex details and Storage scans. */
@Composable
internal fun DigimonScanConversionDialog(
    database: AppDatabase,
    character: CharacterDtos.CardCharaProgress,
    cardName: String,
    obscure: Boolean = false,
    onDismiss: () -> Unit,
    onFinished: (messageResource: Int, isError: Boolean) -> Unit,
) {
    val repository = remember(database) { DigimonScanRepository(database) }
    val scanFlow = remember(database, character.id) { database.digimonScanDao().observeProgress(character.id).map { it ?: 0 } }
    val percentage by scanFlow.collectAsState(initial = null)
    var nickname by rememberSaveable(character.id) { mutableStateOf("") }
    var converting by remember(character.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!converting) onDismiss() },
        title = { Text(stringResource(R.string.digimon_scan_convert)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DexSpritePortrait(BitmapData(character.spriteIdle, character.spriteWidth, character.spriteHeight),
                    Modifier.size(72.dp), obscure = obscure)
                DexNameImage(character, obscure)
                Text(stringResource(R.string.digimon_scan_conversion_description, cardName, stageLabel(character.stage)))
                OutlinedTextField(value = nickname, onValueChange = { nickname = it.take(40) },
                    enabled = !converting, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.digimon_scan_nickname)) })
            }
        },
        confirmButton = {
            TextButton(enabled = !converting && percentage == DigimonScanPolicy.CONVERSION_PERCENTAGE, onClick = {
                converting = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { repository.convert(character.id, nickname) }
                        onFinished(when (result) {
                            is ScanConversionResult.Converted -> R.string.digimon_scan_converted
                            ScanConversionResult.NotReady -> R.string.digimon_scan_not_ready
                            ScanConversionResult.Unavailable -> R.string.digimon_scan_unavailable
                        }, result !is ScanConversionResult.Converted)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        onFinished(R.string.digimon_scan_conversion_failed, true)
                    } finally {
                        converting = false
                    }
                }
            }) { Text(stringResource(if (converting) R.string.digimon_scan_converting else R.string.digimon_scan_convert)) }
        },
        dismissButton = { TextButton(enabled = !converting, onClick = onDismiss) {
            Text(stringResource(R.string.dex_chara_close_button))
        } },
    )
}
