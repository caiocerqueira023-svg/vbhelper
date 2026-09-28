package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.TopBanner
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R


@Composable
fun CardAdventureScreen(
    navController: NavController,
    cardScreenController: CardScreenControllerImpl,
    cardId: Long
) {
    val cardAdventureMissions by cardScreenController
        .getCardAdventureMissions(cardId)
        .collectAsState(emptyList())
    val currentCardAdventure by cardScreenController
        .getCardProgress(cardId)
        .collectAsState(0)

    Scaffold (
        topBar = {
            TopBanner(
                text = stringResource(R.string.card_adventure_missions_title),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    ) { contentPadding ->
        if (cardAdventureMissions.isEmpty()) {
            CyberEmptyState(
                message = stringResource(R.string.adventure_empty_state),
                modifier = Modifier.fillMaxSize().padding(contentPadding)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(cardAdventureMissions) { index, mission ->
                    CardAdventureEntry(
                        cardAdventureEntry = mission,
                        obscure = index > currentCardAdventure - 1
                    )
                }
            }
        }
    }
}
