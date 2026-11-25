package com.tamerslab.species.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tamerslab.components.CyberEmptyState
import com.tamerslab.components.TopBanner
import com.tamerslab.components.VitalSectionBar
import com.tamerslab.domain.card.Card
import com.tamerslab.transfer.ui.scan.cardSelect.ScanCardEntry
import com.tamerslab.utils.BitmapData
import com.tamerslab.R

/**
 * Card chooser for the read step when the bracelet exposes more than one card.
 *
 * Inside the Transfer hub the hub owns the header, so the chooser renders its title as an in-flow
 * heading instead of a second banner and offers the cancel the dropped banner used to carry.
 */
@Composable
fun ChooseCard(
    cards: List<Card>,
    onCardSelected: (Card) -> Unit,
    showHeader: Boolean = true,
    onCancel: (() -> Unit)? = null,
) {
    Scaffold(
        contentWindowInsets = if (showHeader) ScaffoldDefaults.contentWindowInsets else WindowInsets(0, 0, 0, 0),
        topBar = { if (showHeader) TopBanner(text = stringResource(R.string.choose_card_title)) },
    ) { contentPadding ->
        Column(Modifier.fillMaxSize()) {
            if (!showHeader) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    VitalSectionBar(
                        title = stringResource(R.string.choose_card_title),
                        modifier = Modifier.weight(1f),
                    )
                    onCancel?.let {
                        TextButton(onClick = it) { Text(stringResource(R.string.ui_cancel)) }
                    }
                }
            }
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
}
