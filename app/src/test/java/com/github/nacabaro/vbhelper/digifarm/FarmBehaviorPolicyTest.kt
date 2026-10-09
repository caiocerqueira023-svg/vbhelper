package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.domain.digifarm.FarmResident
import com.github.nacabaro.vbhelper.domain.personality.*
import org.junit.Assert.*
import org.junit.Test

class FarmBehaviorPolicyTest {
    @Test fun foregroundNeedDecayMatchesABatchedAdvanceWithoutLosingFractions() {
        var stepped = FarmResident("a", "farm", positionX = 0f, positionY = 0f, targetX = 0f, targetY = 0f,
            energy = 90, satiety = 90, social = 90, funLevel = 90,
            activity = "EXPLORE", activityStartedAt = 0, updatedAt = 0)
        val batched = FarmBehaviorPolicy.advanceNeeds(stepped, 360_000)
        for (now in 900L..360_000L step 900L) stepped = FarmBehaviorPolicy.advanceNeeds(stepped, now)
        assertEquals(batched.energy, stepped.energy)
        assertEquals(batched.satiety, stepped.satiety)
        assertEquals(batched.social, stepped.social)
        assertEquals(batched.funLevel, stepped.funLevel)
        assertTrue(stepped.energy < 90 && stepped.social < 90 && stepped.funLevel < 90)
    }

    @Test fun activitiesReflectPersonalityAndStillMeetImmediateNeeds() {
        val resident = FarmResident("a", "farm", positionX = 0f, positionY = 0f, targetX = 0f, targetY = 0f,
            activity = "EXPLORE", activityStartedAt = 0, updatedAt = 0)
        val sociable = DigimonSocialProfile.forIndividual("a", DigimonPersonalityType.SOCIABLE)
        val strategic = DigimonSocialProfile.forIndividual("a", DigimonPersonalityType.STRATEGIC)
        val socialChoices = (1L..200L).map { FarmBehaviorPolicy.activity(resident, sociable, 60_000, it) }
        val strategicChoices = (1L..200L).map { FarmBehaviorPolicy.activity(resident, strategic, 60_000, it) }
        assertTrue(socialChoices.count { it == "SOCIALIZE" } > strategicChoices.count { it == "SOCIALIZE" })
        assertEquals("REST", FarmBehaviorPolicy.activity(resident.copy(energy = 12), sociable, 60_000, 1))
        assertEquals("EAT", FarmBehaviorPolicy.activity(resident.copy(satiety = 12), strategic, 60_000, 1))
    }
}
