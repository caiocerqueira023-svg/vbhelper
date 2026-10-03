package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.source.CardBatchImportState
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CardSelectionImportUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun importIsOppositeEditAndTheBodyMeetsItsParentsBottomBar() {
        var imports = 0
        var edits = 0
        compose.setContent {
            VBHelperTheme {
                Scaffold(modifier = Modifier.size(320.dp, 560.dp), contentWindowInsets = WindowInsets(0, 0, 0, 32),
                    bottomBar = { Box(Modifier.fillMaxWidth().height(64.dp).testTag("parent-bottom-bar")) }) { padding ->
                    CardSelectionScaffold(onImport = { imports++ }, onModify = { edits++ },
                        state = CardBatchImportState(), onStop = {}, onDismissResult = {}, modifier = Modifier.padding(padding)) { modifier ->
                        Box(modifier.fillMaxSize().testTag("selection-body"))
                    }
                }
            }
        }
        val add = compose.onNodeWithContentDescription(context.getString(R.string.cards_import))
        val edit = compose.onNodeWithContentDescription(context.getString(R.string.cards_edit))
        assertTrue(add.fetchSemanticsNode().boundsInRoot.center.x < edit.fetchSemanticsNode().boundsInRoot.center.x)
        add.performClick()
        edit.performClick()
        compose.runOnIdle { assertEquals(1, imports); assertEquals(1, edits) }
        val body = compose.onNodeWithTag("selection-body").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithTag("parent-bottom-bar").fetchSemanticsNode().boundsInRoot
        assertEquals(bottom.top, body.bottom, 1f)
    }

    @Test fun runningImportDisablesAnotherPickerAndOffersStop() {
        val state = mutableStateOf(CardBatchImportState(total = 20, isRunning = true))
        var stops = 0
        compose.setContent {
            VBHelperTheme {
                CardSelectionScaffold(onImport = {}, onModify = {}, state.value, onStop = { stops++ },
                    onDismissResult = {}, modifier = Modifier.size(320.dp, 560.dp)) { modifier -> Box(modifier) }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.cards_import)).assertIsNotEnabled()
        compose.onNodeWithText(context.getString(R.string.card_import_stop)).performClick()
        compose.runOnIdle { assertEquals(1, stops); state.value = CardBatchImportState(total = 20, completed = 20, added = 10, updated = 10) }
        compose.onNodeWithContentDescription(context.getString(R.string.cards_import)).assertIsEnabled()
    }
}
