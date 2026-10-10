package com.github.nacabaro.vbhelper.screens.homeScreens

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeReadinessTest {
    @Test fun unresolvedCollectionNeverLooksLikeAnEmptyCollection() {
        assertEquals(HomeReadiness.LOADING, homeReadiness(null, null, null, false, false))
    }

    @Test fun firstUseOffersSetupButAnExistingCollectionOffersPartnerSelection() {
        assertEquals(HomeReadiness.SETUP, homeReadiness(0, 0, false, false, false))
        assertEquals(HomeReadiness.CHOOSE_PARTNER, homeReadiness(2, 1, false, false, false))
    }

    @Test fun fullyConfiguredEmptyCollectionOffersTheFirstScan() {
        assertEquals(HomeReadiness.EMPTY, homeReadiness(0, 2, true, false, false))
    }

    @Test fun anActivePartnerWithIncompleteDataIsNotReportedAsAnEmptyCollection() {
        assertEquals(HomeReadiness.PROFILE_UNAVAILABLE, homeReadiness(1, 1, true, true, false))
        assertEquals(HomeReadiness.READY, homeReadiness(1, 1, true, true, true))
    }

    @Test fun anExistingPartnerDoesNotWaitForWatchSetupOrTheDexCatalog() {
        assertEquals(HomeReadiness.READY, homeReadiness(1, null, null, true, true))
        assertEquals(HomeReadiness.CHOOSE_PARTNER, homeReadiness(2, null, null, false, false))
    }
}
