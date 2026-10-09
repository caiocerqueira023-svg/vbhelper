package com.github.nacabaro.vbhelper.domain.personality

import org.junit.Assert.*
import org.junit.Test

class SocialEventAppraisalResolveTest {
    private val friendly = DigimonSocialProfile.forIndividual("friendly", DigimonPersonalityType.FRIENDLY)

    @Test fun agreementReadsAsForwardMotion() {
        assertEquals(SocialStimulus.AGREEMENT, SocialEventAppraisal.classify("Lets do it!"))
        assertEquals(SocialStimulus.AGREEMENT, SocialEventAppraisal.classify("Fechado, vamos nessa!"))
        assertEquals(SocialStimulus.AGREEMENT, SocialEventAppraisal.classify("やろう！"))
        assertTrue(SocialEventAppraisal.resolve(friendly, "Lets do it!", null) > 0)
    }

    @Test fun everyTurnMovesTheBarCoherently() {
        assertTrue(SocialEventAppraisal.resolve(friendly, "What time is it?", null) > 0)
        assertTrue(SocialEventAppraisal.resolve(friendly, "No thanks, another time", null) < 0)
        assertTrue(SocialEventAppraisal.resolve(friendly, "You idiot!", null) < 0)
        assertTrue(SocialEventAppraisal.resolve(friendly, "Thank you, well done!", null) > 0)
    }

    @Test fun modelMarkersNeedASameSignLocalSignal() {
        assertEquals(3, SocialEventAppraisal.resolve(friendly, "Thank you!", 3))
        // A cheerful marker cannot manufacture trust from plain chit-chat,
        // and it cannot zero out a real signal either.
        assertEquals(2, SocialEventAppraisal.resolve(friendly, "What time is it?", 4))
        assertTrue(SocialEventAppraisal.resolve(friendly, "You idiot!", 4) < 0)
    }
}
