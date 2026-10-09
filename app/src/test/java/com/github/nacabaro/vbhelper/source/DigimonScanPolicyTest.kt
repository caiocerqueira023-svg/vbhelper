package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.world.ecosystem.*
import org.junit.Assert.*
import org.junit.Test

class DigimonScanPolicyTest {
    private val owned = participant("partner", InteractionRole.OWNED, InteractionSide.ALLIED, 100)
    private val enemy = participant("enemy", InteractionRole.WILD, InteractionSide.OPPOSING, 200)

    @Test fun directVictoryAwardsTwentyPercentagePoints() {
        assertEquals(mapOf(200L to 20), awards(listOf(owned, enemy)))
    }

    @Test fun joinedVictoryCountsEachEnemyOnceNotEachPartner() {
        val secondOwned = participant("partner-two", InteractionRole.OWNED, InteractionSide.ALLIED, 100)
        val secondEnemy = enemy.copy(individualId = "enemy-two")
        assertEquals(mapOf(200L to 40), awards(listOf(owned, secondOwned, enemy, secondEnemy), InteractionOrigin.JOINED_PLAYER))
    }

    @Test fun alliedWildsAndDuplicateParticipantsAreExcluded() {
        assertEquals(mapOf(200L to 20), awards(listOf(owned, enemy, enemy,
            participant("friend", InteractionRole.WILD, InteractionSide.ALLIED, 300))))
    }

    @Test fun importedVariantsHaveIndependentProgress() {
        assertEquals(mapOf(200L to 20, 201L to 20), awards(listOf(owned, enemy, enemy.copy(individualId = "variant", cardCharacterId = 201))))
    }

    @Test fun nonVictoriesNeverAwardScan() {
        BattleOutcome.entries.filter { it != BattleOutcome.ALLIED_VICTORY }.forEach { outcome ->
            assertTrue(DigimonScanPolicy.awards(InteractionOrigin.DIRECT_PLAYER, outcome, false, listOf(owned, enemy)).isEmpty())
        }
    }

    @Test fun autonomousAndFriendlyBattlesNeverAwardScan() {
        assertTrue(awards(listOf(owned, enemy), InteractionOrigin.AUTONOMOUS).isEmpty())
        assertTrue(DigimonScanPolicy.awards(InteractionOrigin.DIRECT_PLAYER, BattleOutcome.ALLIED_VICTORY, true, listOf(owned, enemy)).isEmpty())
    }

    @Test fun anAlliedOwnedPartnerAndAvailableSpeciesAreRequired() {
        assertTrue(awards(listOf(enemy)).isEmpty())
        assertTrue(awards(listOf(owned.copy(side = InteractionSide.OPPOSING), enemy)).isEmpty())
        assertTrue(awards(listOf(owned, enemy.copy(cardCharacterId = null))).isEmpty())
    }

    @Test fun scanCapsAtOneHundredIncludingOverflow() {
        assertEquals(20, DigimonScanPolicy.afterReward(0, 20))
        assertEquals(100, DigimonScanPolicy.afterReward(80, 40))
        assertEquals(100, DigimonScanPolicy.afterReward(100, 20))
        assertEquals(100, DigimonScanPolicy.afterReward(0, Int.MAX_VALUE))
    }

    @Test fun instantScanOnlyChangesTheRateInDebugBuilds() {
        assertEquals(20, DigimonScanPolicy.perDefeat(false, false))
        assertEquals(20, DigimonScanPolicy.perDefeat(false, true))
        assertEquals(20, DigimonScanPolicy.perDefeat(true, false))
        assertEquals(100, DigimonScanPolicy.perDefeat(true, true))
        assertEquals(mapOf(200L to 100), DigimonScanPolicy.awards(InteractionOrigin.DIRECT_PLAYER,
            BattleOutcome.ALLIED_VICTORY, false, listOf(owned, enemy), percentagePerDefeat = 100))
        assertTrue(DigimonScanPolicy.awards(InteractionOrigin.AUTONOMOUS,
            BattleOutcome.ALLIED_VICTORY, false, listOf(owned, enemy), percentagePerDefeat = 100).isEmpty())
    }

    private fun awards(participants: List<WorldInteractionParticipant>, origin: InteractionOrigin = InteractionOrigin.DIRECT_PLAYER) =
        DigimonScanPolicy.awards(origin, BattleOutcome.ALLIED_VICTORY, false, participants)

    private fun participant(id: String, role: InteractionRole, side: InteractionSide, card: Long) =
        WorldInteractionParticipant("battle", id, role, side, cardCharacterId = card)
}
