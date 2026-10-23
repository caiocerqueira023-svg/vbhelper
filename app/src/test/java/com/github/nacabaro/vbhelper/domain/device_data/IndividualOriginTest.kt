package com.github.nacabaro.vbhelper.domain.device_data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndividualOriginTest {
    @Test fun knownOriginsAreValid() {
        assertTrue(IndividualOrigin.isValid(IndividualOrigin.CARD))
        assertTrue(IndividualOrigin.isValid(IndividualOrigin.EXPANDED))
        assertTrue(IndividualOrigin.isValid(IndividualOrigin.IMPORTED))
    }

    @Test fun unknownOrNullOriginsAreRejected() {
        assertFalse(IndividualOrigin.isValid(null))
        assertFalse(IndividualOrigin.isValid(""))
        assertFalse(IndividualOrigin.isValid("SOMETHING_ELSE"))
    }
}