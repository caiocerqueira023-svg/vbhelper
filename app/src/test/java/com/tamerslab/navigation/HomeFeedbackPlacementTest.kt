package com.tamerslab.navigation

import androidx.compose.ui.unit.dp
import com.tamerslab.farm.ui.home.PlaceRowBandHeight
import com.tamerslab.home.ui.PartnerDetent
import com.tamerslab.home.ui.PartnerPeekHeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The farm Home is edge-to-edge, so its Material3 Scaffold reserves no contentWindowInsets bottom and
 * the snackbar host has to clear the system navigation bar and Home's own bottom chrome itself.
 *
 * These cases pin that placement contract: the user-reported bug was a feedback message drawn at the
 * very bottom of the screen, almost entirely covered by the 3-button navigation bar (the deck's place
 * dock and partner peek slab cover the rest). If the bottom chrome geometry or the detent model
 * changes, this test should be updated together with [homeSnackbarLift].
 */
class HomeFeedbackPlacementTest {

    @Test
    fun peekLiftsTheSnackbarAboveTheDockAndThePartnerPeekSlab() {
        val lift = homeSnackbarLift(PartnerDetent.Peek)

        // The lift must be real (not the buggy zero that put the snackbar behind the system bar) and
        // exactly the bottom chrome that is visible at Peek.
        assertTrue("Peek must lift the snackbar above the bottom chrome, was $lift", lift > 0.dp)
        assertEquals(PlaceRowBandHeight + PartnerPeekHeight, lift)
        // 70.dp place dock band + 96.dp partner peek slab, as measured in the bug report.
        assertEquals(166.dp, lift)
    }

    @Test
    fun halfAndExpandedRelyOnTheNavigationBarInsetOnly() {
        // At Half/Expanded the sheet owns the bottom of the screen and the dock has yielded its band,
        // so the snackbar may float over the sheet: only the host's navigationBarsPadding applies.
        assertEquals(0.dp, homeSnackbarLift(PartnerDetent.Half))
        assertEquals(0.dp, homeSnackbarLift(PartnerDetent.Expanded))
    }

    @Test
    fun otherDestinationsNeverGetTheHomeLift() {
        // Non-Home routes keep their previous snackbar placement: their Scaffold already reserves
        // safeDrawing's bottom, so a Home lift here would double-apply the navigation-bar inset.
        for (detent in PartnerDetent.entries) {
            assertEquals(
                "destination outside Home must not lift for $detent",
                0.dp,
                homeSnackbarLift(detent, isFarmHome = false),
            )
        }
    }
}
