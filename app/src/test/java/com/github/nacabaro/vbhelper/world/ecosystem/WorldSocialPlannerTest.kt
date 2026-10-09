package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.*
import org.junit.Assert.*
import org.junit.Test

class WorldSocialPlannerTest {
    private fun actor(id: String, type: DigimonPersonalityType, trust: Int = 50) = SocialCandidate(
        id, DigimonSocialProfile.forIndividual(id, type), trust = trust, territoryPressure = 0.8)

    @Test fun neutralEmotionCanProduceOccasionalRealAttacksAndOtherEncounterMotives() {
        val reckless = actor("rival", DigimonPersonalityType.RECKLESS)
        val choices = (1L..2_000L).mapNotNull { WorldSocialPlanner.choosePlayer(it, 1000, listOf(reckless), true) }
        val attacks = choices.count { it.motive == SocialMotive.HOSTILE_ATTACK }
        assertTrue("Hostile encounters are still unreachable", attacks > 0)
        assertTrue("Balanced encounters became only attacks", attacks < choices.size / 3)
        assertTrue(choices.map { it.motive }.toSet().size >= 5)
        assertTrue((1L..500L).mapNotNull { WorldSocialPlanner.choosePlayer(it, 1000, listOf(reckless), false) }
            .none { it.motive == SocialMotive.HOSTILE_ATTACK })
    }

    @Test fun encountersAreOrderIndependentAndDoNotAlwaysPickTheFirstIdentity() {
        val candidates = listOf(actor("a", DigimonPersonalityType.FRIENDLY), actor("z", DigimonPersonalityType.SOCIABLE))
        val selections = (1L..200L).mapNotNull { seed ->
            val normal = WorldSocialPlanner.choosePlayer(seed, 1000, candidates, true)
            assertEquals(normal, WorldSocialPlanner.choosePlayer(seed, 1000, candidates.reversed(), true))
            normal?.initiatorId
        }.toSet()
        assertEquals(setOf("a", "z"), selections)
    }

    @Test fun coolingDownIndividualsAndNeutralNeighborsAreHandledExplicitly() {
        val a = actor("a", DigimonPersonalityType.SOCIABLE)
        val b = actor("b", DigimonPersonalityType.DARING)
        assertNull(WorldSocialPlanner.choosePlayer(1, 1000, listOf(a.copy(lastInitiationTick = 995)), true))
        val pair = SocialPairCandidate(a, b, affinity = 10)
        val choices = (1L..200L).mapNotNull { WorldSocialPlanner.choosePair(it, 1000, listOf(pair)) }
        assertTrue("Neutral neighboring Digimon cannot get acquainted", choices.isNotEmpty())
        assertTrue(choices.none { it.motive == SocialMotive.HOSTILE_ATTACK })
    }

    @Test fun familiarRelationshipsProduceRecognitionAndReduceHostility() {
        val newcomer = actor("rival", DigimonPersonalityType.RECKLESS)
        val friend = newcomer.copy(trust = 95, familiarity = 8)
        val strangerChoices = (1L..1_000L).mapNotNull { WorldSocialPlanner.choosePlayer(it, 1000, listOf(newcomer), true) }
        val friendChoices = (1L..1_000L).mapNotNull { WorldSocialPlanner.choosePlayer(it, 1000, listOf(friend), true) }
        assertTrue(friendChoices.any { it.motive == SocialMotive.RECOGNITION })
        assertTrue(friendChoices.count { it.motive == SocialMotive.HOSTILE_ATTACK } <
            strangerChoices.count { it.motive == SocialMotive.HOSTILE_ATTACK })
    }

    @Test fun fallbackOpeningsVaryRememberRecentKeysAndAreLocalized() {
        val profile = DigimonSocialProfile.forIndividual("a", DigimonPersonalityType.ASTUTE)
        val keys = mutableSetOf<String>()
        val texts = mutableSetOf<String>()
        repeat(8) { index ->
            val choice = SocialEncounterDecision("a", "trainer", SocialMotive.CURIOUS_APPROACH, index)
            val opening = SocialOpenings.create(choice, profile, "en", keys)
            assertTrue(keys.add(opening.key))
            texts += opening.text
        }
        assertTrue("Different keys still say the same greeting", texts.size >= 6)
        val choice = SocialEncounterDecision("a", "trainer", SocialMotive.TERRITORIAL_WARNING, 0)
        assertNotEquals(SocialOpenings.create(choice, profile, "en").text, SocialOpenings.create(choice, profile, "pt-BR").text)
        assertTrue(SocialOpenings.create(choice, profile, "ja").text.any { it.code > 0x3000 })
    }

    @Test fun semanticPersonalitiesHaveDifferentInitiationAndChallengeDistributions() {
        fun choices(type: DigimonPersonalityType) = (1L..1_000L).mapNotNull {
            WorldSocialPlanner.choosePlayer(it,1000,listOf(actor("same",type)),true)
        }
        val sociable = choices(DigimonPersonalityType.SOCIABLE)
        val reserved = choices(DigimonPersonalityType.ENLIGHTENED)
        val brave = choices(DigimonPersonalityType.BRAVE)
        val compassionate = choices(DigimonPersonalityType.COMPASSIONATE)
        assertTrue("Initiative is still cosmetic", sociable.size > reserved.size + 200)
        assertTrue("Challenge willingness is still cosmetic",
            brave.count { it.motive == SocialMotive.CHALLENGE_SPARRING } >
                compassionate.count { it.motive == SocialMotive.CHALLENGE_SPARRING } * 2)
    }

    @Test fun peerPlanningIsIndependentOfPairOrientationAndInputOrder() {
        val a = actor("a",DigimonPersonalityType.SOCIABLE)
        val b = actor("b",DigimonPersonalityType.DARING)
        val c = actor("c",DigimonPersonalityType.ASTUTE)
        val pairs = listOf(SocialPairCandidate(a,b,affinity=10),SocialPairCandidate(b,c,affinity=40,meetings=3))
        for (seed in 1L..100L) assertEquals(WorldSocialPlanner.choosePair(seed,1000,pairs),
            WorldSocialPlanner.choosePair(seed,1000,pairs.reversed().map { it.copy(first=it.second,second=it.first) }))
    }
}
