package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.ui.theme.VBHelperTheme
import com.github.nacabaro.vbhelper.utils.BitmapData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CardEntryLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun logo() = BitmapData(bitmap = ByteArray(8 * 8 * 2), width = 8, height = 8)

    private fun setEntry(displayModify: Boolean) {
        compose.setContent {
            VBHelperTheme {
                Box(Modifier.size(360.dp, 480.dp)) {
                    CardEntry(
                        name = "Beelzemon DIM",
                        logo = logo(),
                        obtainedCharacters = 0,
                        totalCharacters = 17,
                        officialStatus = OfficialStatus.CUSTOM,
                        onClick = {},
                        displayModify = displayModify,
                        onClickModify = {},
                        onClickDelete = {},
                        onClickSetOrigin = {},
                        onClickRetrySpeciesMatch = {}
                    )
                }
            }
        }
    }

    @Test fun modifyModeStacksActionsBelowTheCardTextInsteadOfSqueezingIt() {
        setEntry(displayModify = true)
        val name = compose.onNodeWithText("Beelzemon DIM").fetchSemanticsNode().boundsInRoot
        val actions = compose.onNodeWithTag("card-entry-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("Edit actions must sit below the card text, not beside it", actions.top >= name.bottom - 1f)
        val retry = compose.onNodeWithText(context.getString(R.string.card_entry_retry_species_match))
        retry.assertIsDisplayed()
        val retryWidth = retry.fetchSemanticsNode().boundsInRoot.width
        with(compose.density) { assertTrue("Retry action must keep readable width", retryWidth > 140.dp.toPx()) }
    }

    @Test fun browseModeShowsNoEditActions() {
        setEntry(displayModify = false)
        compose.onNodeWithTag("card-entry-actions").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.card_entry_retry_species_match)).assertDoesNotExist()
    }
}
