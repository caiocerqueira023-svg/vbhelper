package com.github.nacabaro.vbhelper.screens.homeScreens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.components.showAppFeedback
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.dtos.ItemDtos
import com.github.nacabaro.vbhelper.screens.cardScreen.dialogs.DexCharaDetailsDialog
import com.github.nacabaro.vbhelper.screens.homeScreens.dialogs.DegenerateDialog
import com.github.nacabaro.vbhelper.screens.itemsScreen.ObtainedItemDialog
import com.github.nacabaro.vbhelper.source.DexRepository

/**
 * The four blocking alerts the partner overview can raise.
 *
 * Both hosts (the legacy Home page and the farm partner deck) own the saveable ids, but the dialog
 * bodies and the degenerate transaction are implemented once here so the two surfaces cannot drift.
 */
@Composable
fun PartnerOverlayDialogs(
    state: PartnerOverviewState,
    homeScreenController: HomeScreenController,
    detailsCharacterId: Long?,
    onDetailsCharacterIdChange: (Long?) -> Unit,
    selectedTransformation: CharacterDtos.TransformationHistory?,
    onDismissTransformation: () -> Unit,
    collectedItem: ItemDtos.PurchasedItem?,
    collectedCurrency: Int?,
    onDismissCollected: () -> Unit,
) {
    val context = LocalContext.current

    if (collectedItem != null) {
        ObtainedItemDialog(
            obtainedItem = collectedItem,
            obtainedCurrency = collectedCurrency ?: 0,
            onClickDismiss = onDismissCollected,
        )
    }

    if (detailsCharacterId != null) {
        val application = context.applicationContext as VBHelper
        val dexRepository = remember(application) { DexRepository(application.container.db) }
        val detailsCharacterFlow = remember(detailsCharacterId, dexRepository) {
            dexRepository.getCharacterProgress(detailsCharacterId)
        }
        val character by detailsCharacterFlow.collectAsState(initial = null)
        character?.let {
            DexCharaDetailsDialog(
                currentChara = it,
                obscure = false,
                onClickClose = { onDetailsCharacterIdChange(null) },
                onClickCharacter = { newId -> onDetailsCharacterIdChange(newId) },
            )
        }
    }

    val transformation = selectedTransformation
    val activeMon = state.activeMon
    if (transformation != null && activeMon != null) {
        DegenerateDialog(
            targetStage = transformation.stage,
            onDismiss = onDismissTransformation,
            onConfirm = {
                homeScreenController.degenerate(
                    characterId = activeMon.id,
                    transformation = transformation,
                ) { result ->
                    onDismissTransformation()
                    result.onFailure {
                        context.showAppFeedback(R.string.app_partner_update_failed, important = true)
                    }
                }
            },
        )
    }

    if (state.adventureMissionsFinished) {
        Dialog(onDismissRequest = state.acknowledgeAdventureFinished) {
            Card {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.home_adventure_mission_finished),
                        textAlign = TextAlign.Center,
                    )
                    VitalButton(
                        onClick = state.acknowledgeAdventureFinished,
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.beta_warning_button_dismiss))
                    }
                }
            }
        }
    }
}
