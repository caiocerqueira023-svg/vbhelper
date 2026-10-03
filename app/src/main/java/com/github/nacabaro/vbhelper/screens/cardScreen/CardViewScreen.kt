package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CardEvolutionGraph
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharaDetailsDialog
import com.github.nacabaro.vbhelper.source.DexRepository
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@Composable
fun CardViewScreen(navController: NavController, cardId: Long) {
    val application = LocalContext.current.applicationContext as VBHelper
    val repository = remember { DexRepository(application.container.db) }
    var displayedCardId by rememberSaveable(cardId) { mutableLongStateOf(cardId) }
    var retry by remember { mutableIntStateOf(0) }
    var cardsFailed by remember { mutableStateOf(false) }
    val cards by produceState<List<CardDtos.CardProgress>?>(null, repository, retry) {
        value = null
        cardsFailed = false
        try {
            repository.getAllDims().collect { value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to load Dim catalog")
            cardsFailed = true
        }
    }
    val currentCard = cards?.firstOrNull { it.cardId == displayedCardId }
    LaunchedEffect(cards, displayedCardId) {
        if (cards?.isNotEmpty() == true && currentCard == null) displayedCardId = cards!!.first().cardId
    }
    // The app navigation Scaffold already reserves both system bars and its bottom bar.
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopBanner(
            text = currentCard?.cardName ?: stringResource(R.string.dex_chart_title),
            onBackClick = { navController.popBackStack() },
            onAdventureClick = currentCard?.let { card -> {
                navController.navigate(NavigationItems.CardAdventure.route.replace("{cardId}", card.cardId.toString()))
            } },
        )
    }) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            cardsFailed -> Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.dex_chart_load_error))
                VitalButton(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.dex_chart_retry))
                }
            }
            cards == null -> DexChartLoading(modifier)
            cards!!.isEmpty() -> CyberEmptyState(stringResource(R.string.cards_empty_state), modifier)
            currentCard == null -> DexChartLoading(modifier)
            else -> key(displayedCardId) {
                CardEvolutionDetails(currentCard, cards!!, repository, modifier,
                    onSelectCard = { displayedCardId = it })
            }
        }
    }
}

@Composable
private fun CardEvolutionDetails(
    card: CardDtos.CardProgress,
    cards: List<CardDtos.CardProgress>,
    repository: DexRepository,
    modifier: Modifier,
    onSelectCard: (Long) -> Unit,
) {
    var selectedCharacterId by rememberSaveable { mutableStateOf<Long?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val graph by produceState<CardEvolutionGraph?>(null, repository, card.cardId, retry) {
        value = null
        loadFailed = false
        try {
            repository.getCardEvolutionGraph(card.cardId).collect { value = it }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Unable to load evolution chart for card %s", card.cardId)
            loadFailed = true
        }
    }
    CardEvolutionPanel(card, cards, graph, onSelectCard, { selectedCharacterId = it }, modifier,
        selectedCharacterId = selectedCharacterId, loadFailed = loadFailed, onRetry = { retry++ })
    val selected = graph?.characters?.firstOrNull { it.id == selectedCharacterId }
    selected?.let { character ->
        // Resolve selection against each fresh snapshot so discovery/availability remain reactive.
        DexCharaDetailsDialog(currentChara = character, obscure = false,
            onClickClose = { selectedCharacterId = null },
            onClickCharacter = { selectedCharacterId = it })
    }
}
