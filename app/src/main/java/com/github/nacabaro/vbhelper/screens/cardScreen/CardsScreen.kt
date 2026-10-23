package com.github.nacabaro.vbhelper.screens.cardScreen

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.navigation.NavigationItems
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.CardDeleteDialog
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.CardRenameDialog
import com.github.nacabaro.vbhelper.source.DexRepository
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.CardOriginDialog
import com.github.nacabaro.vbhelper.source.CardBatchImportState
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.components.rememberCollectionLoad
import com.github.nacabaro.vbhelper.components.CollectionStatePanel
import com.github.nacabaro.vbhelper.components.CollectionRefreshNotice
import androidx.compose.foundation.layout.Column

@Composable
fun CardsScreen(
    navController: NavController,
    cardScreenController: CardScreenControllerImpl,
    onImportCards: () -> Unit,
    importState: CardBatchImportState,
    onStopImport: () -> Unit,
    onDismissImportResult: () -> Unit,
) {
    val application = LocalContext.current.applicationContext as VBHelper
    val context = LocalContext.current
    val dexRepository = remember { DexRepository(application.container.db) }
    val collection = rememberCollectionLoad(dexRepository) { dexRepository.getAllDims() }
    val cardList = collection.data.orEmpty()

    val selectedCard = remember { mutableStateOf<CardDtos.CardProgress?>(null) }
    var clickedDelete by remember { mutableStateOf(false) }
    var clickedRename by remember { mutableStateOf(false) }
    var originDialogCard by remember { mutableStateOf<CardDtos.CardProgress?>(null) }
    val matchFailedText = stringResource(R.string.immersion_species_match_failed)
    val technicalDetailsText = stringResource(R.string.immersion_technical_details)
    val matchedPattern = stringResource(R.string.immersion_species_matched)
    val noMatchText = stringResource(R.string.immersion_species_no_match)
    val customText = stringResource(R.string.card_status_custom)

    var modifyCards by remember { mutableStateOf(false) }

    CardSelectionScaffold(onImport = onImportCards, onModify = { modifyCards = !modifyCards }, state = importState,
        onStop = onStopImport, onDismissResult = onDismissImportResult) { bodyModifier ->
        if (collection.blocking) CollectionStatePanel(collection, bodyModifier)
        else if (cardList.isEmpty()) {
            CyberEmptyState(
                message = stringResource(R.string.cards_empty_state),
                modifier = bodyModifier.fillMaxSize()
            )
        } else {
            Column(bodyModifier) {
            CollectionRefreshNotice(collection)
            LazyColumn (
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = cardList,
                    key = { it.cardId },
                    contentType = { "card-progress" }
                ) {
                    CardEntry(
                    name = it.cardName,
                    logo = BitmapData(
                        bitmap = it.cardLogo,
                        width = it.logoWidth,
                        height = it.logoHeight
                    ),
                    onClick = {
                        navController
                            .navigate(
                                NavigationItems
                                    .CardView.route
                                    .replace("{cardId}", "${it.cardId}")
                            )
                    },
                    obtainedCharacters = it.obtainedCharacters,
                    totalCharacters = it.totalCharacters,
                    officialStatus = it.officialStatus,
                    modifier = Modifier.fillMaxWidth(),
                    displayModify = modifyCards,
                    onClickModify = {
                        selectedCard.value = it
                        clickedRename = true
                    },
                    onClickDelete = {
                        selectedCard.value = it
                        clickedDelete = true
                    },
                    onClickSetOrigin = {
                        originDialogCard = it
                    },
                    onClickRetrySpeciesMatch = {
                        cardScreenController.retrySpeciesMatch(it.cardId) { matchedCount, errorMessage ->
                            val message = errorMessage?.let { error ->
                                com.github.nacabaro.vbhelper.source.DiagnosticText.feedback(
                                    matchFailedText, error, application.container.debugSettingsRepository.enabled, technicalDetailsText)
                            } ?: if (matchedCount > 0) {
                                String.format(java.util.Locale.getDefault(), matchedPattern, matchedCount)
                            } else {
                                noMatchText
                            }
                            context.showAppFeedback(message)
                        }
                    }
                    )
                }
            }
            }
        }
    }

    if (clickedRename) {
        CardRenameDialog(
            onDismiss = {
                clickedRename = false
                selectedCard.value = null
            },
            onRename = { newName ->
                Log.d("CardsScreen", "New name: $newName")
                Log.d("CardsScreen", "Card: ${selectedCard.value.toString()}")
                cardScreenController
                    .renameCard(
                        cardId = selectedCard.value!!.cardId,
                        newName = newName,
                        onRenamed = {
                            clickedRename = false
                            selectedCard.value = null
                        }
                    )
            },
            currentName = selectedCard.value!!.cardName
        )
    }

    if (clickedDelete) {
        CardDeleteDialog(
            cardName = selectedCard.value!!.cardName,
            onDismiss = {
                clickedDelete = false
                selectedCard.value = null
            },
            onConfirm = {
                cardScreenController
                    .deleteCard(
                        cardId = selectedCard.value!!.cardId,
                        onDeleted = {
                            clickedDelete = false
                            selectedCard.value = null
                        }
                    )
            }
        )
    }

    originDialogCard?.let { card ->
        CardOriginDialog(
            cardName = card.cardName,
            onDismiss = { originDialogCard = null },
            onSelect = { status ->
                cardScreenController.setCardOfficialStatus(card.cardId, status) { matchedCount ->
                    originDialogCard = null
                    val message = when (status) {
                        OfficialStatus.OFFICIAL -> if (matchedCount > 0) {
                            String.format(java.util.Locale.getDefault(), matchedPattern, matchedCount)
                        } else {
                            noMatchText
                        }
                        OfficialStatus.CUSTOM -> customText
                        OfficialStatus.UNKNOWN -> ""
                    }
                    if (message.isNotEmpty()) Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
