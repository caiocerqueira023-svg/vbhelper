package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.domain.transfer.NativeCanonicalMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeMappingContinuityTest {
    private val reviewed = NativeCanonicalMapping(snapshotId = 41, nativeSpeciesId = 6,
        canonicalSpeciesId = "digimon.agumon", authority = "USER_CONFIRMED", reviewedAt = 1234)

    @Test fun theSameSpeciesCarriesTheReviewToTheRefreshedSnapshot() {
        assertEquals(reviewed.copy(snapshotId = 42), NativeMappingContinuity.carried(reviewed, 6, 42))
    }

    @Test fun anEvolvedSpeciesRequiresAFreshReview() {
        assertNull(NativeMappingContinuity.carried(reviewed, 7, 42))
    }

    @Test fun nothingCarriesWithoutAPreviousUserReviewedMapping() {
        assertNull(NativeMappingContinuity.carried(null, 6, 42))
        assertNull(NativeMappingContinuity.carried(reviewed.copy(authority = "SUGGESTED"), 6, 42))
    }
}
