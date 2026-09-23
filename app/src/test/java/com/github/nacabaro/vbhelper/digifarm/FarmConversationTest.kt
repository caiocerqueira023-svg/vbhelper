package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.digifarm.map.DigifarmGround
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
import com.github.nacabaro.vbhelper.digifarm.social.ConversationSession
import com.github.nacabaro.vbhelper.digifarm.social.FarmUtteranceValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FarmConversationTest {
    private val residents = setOf("aaa", "bbb", "ccc")

    @Test
    fun audienceResolvesAllIndividualSubset() {
        assertEquals("ALL", FarmUtteranceValidator.validate("Hi!", emptyList(), residents)!!.audience)
        assertEquals("INDIVIDUAL", FarmUtteranceValidator.validate("Hi!", listOf("aaa"), residents)!!.audience)
        assertEquals("SUBSET", FarmUtteranceValidator.validate("Hi!", listOf("aaa", "bbb"), residents)!!.audience)
    }

    @Test
    fun rejectsUnknownRecipientsBadIntentAndBlankSpeech() {
        assertNull(FarmUtteranceValidator.validate("Hi!", listOf("zzz"), residents))
        assertNull(FarmUtteranceValidator.validate("   ", emptyList(), residents))
        assertNull(
            FarmUtteranceValidator.validate("Hi!", emptyList(), residents, intent = "drop_table")
        )
        assertNotNull(
            FarmUtteranceValidator.validate("Hi!", emptyList(), residents, intent = "invite_activity", targetId = "bbb")
        )
        assertNull(
            FarmUtteranceValidator.validate("Hi!", emptyList(), residents, targetId = "zzz")
        )
    }

    @Test
    fun sessionExpiresAfterSixTurnsOrTimeout() {
        val now = System.currentTimeMillis()
        val expired = ConversationSession(UUID.randomUUID().toString(), listOf("aaa", "bbb"), 6, now - 10_000L, now + 60_000L)
        assertTrue(expired.turnCount >= 6)
        val timedOut = ConversationSession(UUID.randomUUID().toString(), listOf("aaa", "bbb"), 0, now - 600_000L, now - 1L)
        assertTrue(now > timedOut.expiresAt)
    }

    @Test
    fun nearbyResidentsShareTheContinuousIsland() {
        val a = MapPoint(205f, 153f)
        val near = MapPoint(212f, 160f)
        assertTrue(DigifarmGround.isWalkable(a))
        assertTrue(DigifarmGround.isWalkable(near))
        assertTrue(DigifarmGround.worldDistance(a, near) < 0.05f)
    }
}
