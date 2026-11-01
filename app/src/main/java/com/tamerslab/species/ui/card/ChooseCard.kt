package com.tamerslab.species.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tamerslab.components.TopBanner
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.domain.card.Card
import com.tamerslab.transfer.ui.scan.cardSelect.ScanCardEntry
import com.tamerslab.utils.BitmapData
import com.tamerslab.R

@Composable
fun ChooseCard(
    cards: List<Card>,
    onCardSelected: (Card) -> Unit
) {
    Scaffold (
        topBar = {
            TopBanner(
                text = stringResource(R.string.choose_card_title),
            )
        }
    ) { contentPadding ->
        if (cards.isEmpty()) {
            CyberEmptyState(
                stringResource(R.string.choose_card_empty),
                Modifier.fillMaxSize().padding(contentPadding)
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
                items(cards) {
                    ScanCardEntry(
                        name = it.name,
                        logo = BitmapData(
                            it.logo,
                            it.logoWidth,
                            it.logoHeight
                        ),
                        onClick = { onCardSelected(it) }
                    )
                }
            }
        }
    }
}
