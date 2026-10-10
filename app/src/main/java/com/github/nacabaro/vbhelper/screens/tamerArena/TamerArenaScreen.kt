package com.github.nacabaro.vbhelper.screens.tamerArena

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineBattleSessionViewModel
import com.github.nacabaro.vbhelper.screens.offlineBattle.OfflineTrainingBattleScreen
import com.github.nacabaro.vbhelper.screens.storageScreen.StorageCharacterPickerDialog

/** Session owner stays outside the scrollable lobby; navigating never reconstructs a live battle. */
@Composable
fun TamerArenaScreen(viewModel: TamerArenaViewModel, battleViewModel: OfflineBattleSessionViewModel,
    onBack: () -> Unit, onBattleActiveChanged: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    val battle by battleViewModel.state.collectAsState()
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var series by rememberSaveable { mutableStateOf<String?>(null) }
    var availableOnly by rememberSaveable { mutableStateOf(true) }
    var pickerSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    val match = state.currentMatch
    LaunchedEffect(Unit) { viewModel.reload() }
    LaunchedEffect(match?.id) {
        onBattleActiveChanged(match != null)
        if (match != null) battleViewModel.startArena(context, match) else battleViewModel.finishSession()
    }
    DisposableEffect(Unit) { onDispose { onBattleActiveChanged(false) } }
    if (match != null) {
        OfflineTrainingBattleScreen(battleViewModel, onExit = { viewModel.closeMatch() }, modifier = modifier,
            battleTitle = stringResource(R.string.arena_vs, match.right.name),
            battleSubtitle = "${match.left.members.size} × ${match.right.members.size} · ${arenaDifficultyName(match.difficulty)}",
            resultContent = { snapshot ->
                ArenaResult(snapshot, match, battle.arenaSettlement, battle.recordingArenaResult,
                    battle.arenaRecordError, state.busy, state.activeRun?.championId == com.github.nacabaro.vbhelper.battle.offline.tamers.ArenaRepository.PLAYER_ID,
                    onRetryRecord = battleViewModel::retryArenaRecord,
                    onContinue = {
                        // Keep the result in place while preparing; no flash back through the roster.
                        if (match.tournamentId != null) { tab = 1; viewModel.resumeTournament(match.tournamentId) }
                        else match.right.tamerId?.let(viewModel::startExhibition)
                    },
                    onReturn = { viewModel.closeMatch(); if (match.tournamentId != null) tab = 1 })
            })
        return
    }
    TamerArenaLobby(state, ArenaUiActions(onBack, viewModel::reload, viewModel::selectTamer,
        viewModel::setFormat, viewModel::setDifficulty, { pickerSlot = it }, viewModel::startExhibition,
        viewModel::startTournament, viewModel::resumeTournament, viewModel::showBracket, viewModel::withdrawTournament,
        viewModel::clearError), tab, { tab = it }, query, { query = it }, series, { series = it },
        availableOnly, { availableOnly = it }, modifier)
    pickerSlot?.let { slot ->
        val other = state.selectedPartnerIds.getOrNull(if (slot == 0) 1 else 0)
        val options = state.partners.filter { it.stableId != other }
        StorageCharacterPickerDialog(options.mapNotNull { it.character }, stringResource(R.string.arena_choose_partner),
            stringResource(R.string.arena_no_partners), onDismiss = { pickerSlot = null }, onCharacterSelected = { id ->
                options.firstOrNull { it.character?.id == id }?.let { viewModel.selectPartner(slot, it.stableId) }
                pickerSlot = null
            })
    }
}
