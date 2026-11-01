package com.tamerslab.ui

import org.junit.Assert.*
import org.junit.Test

class PartnerConditionTest {
    @Test fun contactSummaryUsesTheActualAboveSeventyFiveUnlockBoundary() {
        assertEquals(RapportCondition.DEVELOPING, PartnerCondition.rapport(75))
        assertEquals(RapportCondition.ESTABLISHED, PartnerCondition.rapport(76))
    }

    @Test fun lowSatietyMeansFeedingAndEnergyHasTheSamePriorityAsFarmCare() {
        assertEquals(CarePriority.REST, PartnerCondition.care(19, 19, 80, 80))
        assertEquals(CarePriority.FEED, PartnerCondition.care(20, 19, 80, 80))
        assertEquals(CarePriority.SOCIALIZE, PartnerCondition.care(80, 80, 24, 80))
        assertEquals(CarePriority.PLAY, PartnerCondition.care(80, 80, 80, 24))
        assertEquals(CarePriority.NONE, PartnerCondition.care(80, 80, 80, 80))
    }
}
