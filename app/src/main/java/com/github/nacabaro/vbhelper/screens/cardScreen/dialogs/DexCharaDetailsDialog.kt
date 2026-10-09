package com.github.nacabaro.vbhelper.screens.cardScreen.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.github.nacabaro.vbhelper.components.SpeciesPickerDialog
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.source.DexRepository
import com.github.nacabaro.vbhelper.species.SpeciesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
fun DexCharaDetailsDialog(currentChara: CharacterDtos.CardCharaProgress, obscure: Boolean,
                         onClickClose: () -> Unit, onClickCharacter: (Long) -> Unit) {
    val application = LocalContext.current.applicationContext as VBHelper
    val database = application.container.db
    val repository = remember { DexRepository(database) }
    val speciesRepository = remember { SpeciesRepository(database, application.container.speciesSettingsRepository) }
    val scope = rememberCoroutineScope()
    var showJogress by remember(currentChara.id) { mutableStateOf(false) }
    var showSpeciesPicker by remember(currentChara.id) { mutableStateOf(false) }
    var speciesNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var speciesNamesLoading by remember(currentChara.id) { mutableStateOf(false) }
    var isCustomCard by remember(currentChara.id) { mutableStateOf(false) }
    var cardName by remember(currentChara.id) { mutableStateOf("") }
    var showConversion by rememberSaveable(currentChara.id) { mutableStateOf(false) }
    var conversionMessage by remember(currentChara.id) { mutableStateOf<Int?>(null) }
    var conversionError by remember(currentChara.id) { mutableStateOf(false) }
    val scanFlow = remember(currentChara.id) { database.digimonScanDao().observeProgress(currentChara.id).map { it ?: 0 } }
    val scanPercentage by scanFlow.collectAsState(initial = null)
    val characterFlow = remember(currentChara.id) { repository.getCharacterProgress(currentChara.id) }
    val latestCharacter by characterFlow.collectAsState(initial = currentChara)
    val displayedCharacter = latestCharacter ?: currentChara
    val profileFlow = remember(currentChara.id) { database.speciesProfileDao().getByCardCharacterIdFlow(currentChara.id) }
    val profile by profileFlow.collectAsState(null)
    val evolutionFlow = remember(currentChara.id) { repository.getCharacterPossibleTransformations(currentChara.id) }
    val evolutions by evolutionFlow.collectAsState(emptyList())
    val attributeFlow = remember(currentChara.id) { repository.getCharacterPossibleFusions(currentChara.id) }
    val attributeJogress by attributeFlow.collectAsState(emptyList())
    val specificFlow = remember(currentChara.id) { repository.getCharacterSpecificJogress(currentChara.id) }
    val specificJogress by specificFlow.collectAsState(emptyList())
    LaunchedEffect(currentChara.id) {
        val card = withContext(Dispatchers.IO) { database.cardDao().getCardByCardCharacterId(currentChara.id) }
        isCustomCard = card?.officialStatus == OfficialStatus.CUSTOM
        cardName = card?.name.orEmpty()
    }
    fun ensureSpeciesNames() {
        if (speciesNames.isNotEmpty() || speciesNamesLoading) return
        scope.launch(Dispatchers.IO) {
            speciesNamesLoading = true
            try {
                speciesNames = speciesRepository.getAllSpeciesNames()
            } finally {
                speciesNamesLoading = false
            }
        }
    }
    val maximumHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .88f }
    Dialog(onDismissRequest = onClickClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(.92f).heightIn(max = maximumHeight),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
            DexCharacterDetailsContent(displayedCharacter, obscure, profile, evolutions,
                attributeJogress.isNotEmpty() || specificJogress.isNotEmpty(), isCustomCard, true,
                onClose = onClickClose, onJogress = { showJogress = true }, onSelectCharacter = onClickCharacter,
                onPickSpecies = {
                    ensureSpeciesNames()
                    showSpeciesPicker = true
                },
                scanContent = { DigimonScanProgressContent(scanPercentage, conversionMessage?.let { stringResource(it) }, conversionError) },
                primaryAction = { DigimonScanConvertButton(scanPercentage, false) {
                    conversionMessage = null
                    showConversion = true
                } })
        }
    }
    if (showConversion) DigimonScanConversionDialog(database, displayedCharacter, cardName, obscure,
        onDismiss = { showConversion = false }, onFinished = { message, isError ->
            conversionMessage = message
            conversionError = isError
            showConversion = false
        })
    if (showJogress) DexCharaFusionsDialog(currentChara, attributeJogress, obscure,
        onClickDismiss = { showJogress = false }, specificJogress = specificJogress)
    if (showSpeciesPicker) SpeciesPickerDialog(speciesNames = speciesNames, isLoading = speciesNamesLoading,
        onDismiss = { showSpeciesPicker = false }, onSpeciesSelected = { selectedName ->
            showSpeciesPicker = false
            scope.launch(Dispatchers.IO) {
                speciesRepository.saveManualProfile(currentChara.id, selectedName, null, null, null, emptyList())
            }
        })
}
