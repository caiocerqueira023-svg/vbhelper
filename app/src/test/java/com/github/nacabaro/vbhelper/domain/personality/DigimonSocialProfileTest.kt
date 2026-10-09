package com.github.nacabaro.vbhelper.domain.personality

import org.junit.Assert.*
import org.junit.Test

class DigimonSocialProfileTest {
    @Test fun profilesUseSemanticPersonalityDifferencesAndStableIndividualVariation() {
        val sociable = DigimonSocialProfile.forIndividual("same", DigimonPersonalityType.SOCIABLE)
        val strategic = DigimonSocialProfile.forIndividual("same", DigimonPersonalityType.STRATEGIC)
        val daring = DigimonSocialProfile.forIndividual("same", DigimonPersonalityType.DARING)
        val devoted = DigimonSocialProfile.forIndividual("same", DigimonPersonalityType.DEVOTED)
        assertTrue(sociable.initiative > strategic.initiative)
        assertTrue(daring.curiosity > devoted.curiosity)
        assertTrue(devoted.loyalty > daring.loyalty)
        assertEquals(sociable, DigimonSocialProfile.forIndividual("same", DigimonPersonalityType.SOCIABLE))
        assertEquals(16, DigimonPersonalityType.entries.map { DigimonSocialProfile.forIndividual("same", it) }.toSet().size)
        assertNotEquals(sociable, DigimonSocialProfile.forIndividual("another", DigimonPersonalityType.SOCIABLE))
    }

    @Test fun evolutionDoesNotRerollTheIndividualsBaselineVoice() {
        val before = DigimonRoleplayVoice.create("individual", DigimonPersonalityType.STRATEGIC, 2, 0)
        val after = DigimonRoleplayVoice.create("individual", DigimonPersonalityType.STRATEGIC, 5, 3)
        assertEquals(before, after)
    }

    @Test fun neutralConversationDoesNotRandomlyChangeTrustAndChallengesHaveDifferentAppraisals() {
        val brave = DigimonSocialProfile.forIndividual("brave", DigimonPersonalityType.BRAVE)
        val cautious = DigimonSocialProfile.forIndividual("cautious", DigimonPersonalityType.OVERPROTECTIVE)
        assertEquals(0, SocialEventAppraisal.delta(brave, SocialStimulus.NEUTRAL))
        assertTrue(SocialEventAppraisal.delta(brave, SocialStimulus.CHALLENGE) >
            SocialEventAppraisal.delta(cautious, SocialStimulus.CHALLENGE))
        assertTrue(SocialEventAppraisal.delta(brave, SocialStimulus.INSULT) < 0)
        assertEquals(SocialStimulus.NEUTRAL, SocialEventAppraisal.classify("What time is it?"))
        assertNotEquals(SocialStimulus.INSULT, SocialEventAppraisal.classify("Would you like a friendly sparring match?"))
    }
}
