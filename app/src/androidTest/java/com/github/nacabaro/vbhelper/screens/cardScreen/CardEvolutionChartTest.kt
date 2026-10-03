package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CardEvolutionGraph
import com.github.nacabaro.vbhelper.dtos.CardEvolutionLink
import com.github.nacabaro.vbhelper.dtos.CharacterDtos
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardEvolutionChartTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int, vararg args: Any) = context.getString(id, *args)
    private val pixels = byteArrayOf(0x00, 0x7c) // One synthetic RGB565 pixel; no imported assets.
    private fun card(id: Long) = CardDtos.CardProgress(id, "Test Dim $id", pixels, 1, 1, OfficialStatus.CUSTOM, 3, 2)
    private fun character(id: Long, index: Int, stage: Int, discovered: Long?, available: Boolean) =
        CharacterDtos.CardCharaProgress(id, pixels, byteArrayOf(0x1f, 0), 1, 1,
            pixels, 1, 1, discovered, 100, 100, 100, stage, NfcCharacter.Attribute.Data, available, index)
    private fun graph(cardId: Long = 7) = CardEvolutionGraph(cardId, listOf(
        character(101, 0, 0, null, false), character(102, 1, 1, 10, false),
        character(103, 2, 2, 20, true)), listOf(CardEvolutionLink(101, 102), CardEvolutionLink(102, 103, "Data")))

    @Test fun ownershipStatesAndNodeTapsUseTheDisplayedCharacterAfterZoom() {
        var selected: Long? = null
        compose.setContent {
            VBHelperTheme {
                CardEvolutionPanel(card(7), listOf(card(7)), graph(),
                    onSelectCard = {}, onSelectCharacter = { selected = it }, modifier = Modifier.size(320.dp, 560.dp))
            }
        }
        compose.onNodeWithContentDescription(text(R.string.dex_chart_character, 1, "I"))
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                text(R.string.dex_status_never_obtained)))
        compose.onNodeWithContentDescription(text(R.string.dex_chart_character, 2, "II"))
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                text(R.string.dex_status_previously_obtained)))
        compose.onNodeWithContentDescription(text(R.string.dex_chart_character, 3, "III"))
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                text(R.string.dex_status_currently_available)))
        compose.onNodeWithContentDescription(text(R.string.dex_chart_zoom_in)).performClick()
        compose.onNodeWithContentDescription(text(R.string.dex_chart_character, 1, "I")).performClick()
        compose.runOnIdle { assertEquals(101L, selected) }
    }

    @Test fun fusionTogglePreservesNodePositionsAndIncludesFusionOnlyCharacters() {
        compose.setContent {
            VBHelperTheme {
                CardEvolutionPanel(card(7), listOf(card(7)), graph(),
                    onSelectCard = {}, onSelectCharacter = {}, modifier = Modifier.size(320.dp, 560.dp))
            }
        }
        val node = compose.onNodeWithContentDescription(text(R.string.dex_chart_character, 3, "III"))
        val before = node.fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNodeWithText(text(R.string.dex_chart_fusions))
        toggle.assertIsNotSelected().performClick().assertIsSelected()
        assertEquals(before, node.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun dimNavigationIsBoundedAndClearsThePreviousCardsFusionSelection() {
        val displayed = mutableStateOf(7L)
        val cards = listOf(card(7), card(12))
        compose.setContent {
            VBHelperTheme {
                androidx.compose.runtime.key(displayed.value) {
                    CardEvolutionPanel(cards.first { it.cardId == displayed.value }, cards, graph(displayed.value),
                        onSelectCard = { displayed.value = it }, onSelectCharacter = {}, modifier = Modifier.size(320.dp, 560.dp))
                }
            }
        }
        compose.onNodeWithContentDescription(text(R.string.dex_chart_previous_card)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.dex_chart_fusions)).performClick()
        compose.onNodeWithContentDescription(text(R.string.dex_chart_next_card)).performClick()
        compose.onNodeWithText("Test Dim 12").assertExists()
        compose.onNodeWithContentDescription(text(R.string.dex_chart_next_card)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.dex_chart_fusions)).assertIsNotSelected()
    }

    @Test fun loadingAndAnEmptyImportedCardHaveDistinctStates() {
        val data = mutableStateOf<CardEvolutionGraph?>(null)
        compose.setContent {
            VBHelperTheme {
                CardEvolutionPanel(card(7), listOf(card(7)), data.value,
                    onSelectCard = {}, onSelectCharacter = {}, modifier = Modifier.size(320.dp, 560.dp))
            }
        }
        compose.onNodeWithContentDescription(text(R.string.dex_chart_loading)).assertExists()
        compose.runOnIdle { data.value = CardEvolutionGraph(7, emptyList(), emptyList()) }
        compose.onNodeWithText(text(R.string.dex_chart_empty)).assertExists()
    }

    @Test fun consecutiveScrollCallbacksAccumulateBeforeRecompositionAndCenteredAxesReportZero() {
        val tall = CardEvolutionGraph(7, (0..6).map {
            character(101L + it, it, it, null, false)
        }, emptyList())
        compose.setContent {
            VBHelperTheme {
                CardEvolutionPanel(card(7), listOf(card(7)), tall,
                    onSelectCard = {}, onSelectCharacter = {}, modifier = Modifier.size(320.dp, 560.dp))
            }
        }
        val chart = compose.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.ScrollBy))
        chart.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, 10f)
            scroll(0f, 10f)
        }
        val semantics = chart.fetchSemanticsNode().config
        assertEquals(0f, semantics[androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange].value(), .01f)
        assertEquals(20f, semantics[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value(), .01f)
    }

    @Test fun jogressToggleIsVisibleEvenWithoutRoutesAndControlsOverlayTheChart() {
        compose.setContent {
            VBHelperTheme {
                CardEvolutionPanel(card(7), listOf(card(7)), graph().copy(links = emptyList()),
                    onSelectCard = {}, onSelectCharacter = {}, modifier = Modifier.size(320.dp, 560.dp))
            }
        }
        compose.onNodeWithText(text(R.string.dex_chart_fusions)).assertExists().assertIsNotEnabled()
        val chart = compose.onNodeWithTag("dex-evolution-viewport").fetchSemanticsNode().boundsInRoot
        val fit = compose.onNodeWithContentDescription(text(R.string.dex_chart_fit)).fetchSemanticsNode().boundsInRoot
        assertTrue(chart.contains(fit.center))
        val footer = compose.onNodeWithTag("dex-card-navigation").fetchSemanticsNode().boundsInRoot
        assertEquals(chart.bottom, footer.top, 1f)
    }
}
