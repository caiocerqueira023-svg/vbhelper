package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.source.CardBatchImportState

@Composable
internal fun CardSelectionScaffold(
    onImport: () -> Unit,
    onModify: () -> Unit,
    state: CardBatchImportState,
    onStop: () -> Unit,
    onDismissResult: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0), topBar = {
        TopBanner(stringResource(R.string.cards_my_cards_title), onModifyClick = onModify,
            onImportClick = onImport, importEnabled = !state.isRunning)
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            CardBatchImportPanel(state, onStop, onDismissResult)
            content(Modifier.weight(1f).fillMaxWidth())
        }
    }
}
