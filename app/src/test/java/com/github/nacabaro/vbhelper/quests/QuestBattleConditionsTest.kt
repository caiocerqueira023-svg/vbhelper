package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import org.junit.Assert.*
import org.junit.Test

class QuestBattleConditionsTest {
    private val goal = QuestObjective("o", "q", 0, QuestObjectiveType.WIN_BATTLE_CONDITION, 1,
        techniqueId = "skill", minTechniqueHits = 2, maxBattleMillis = 60000, maxBattleItems = 0,
        minHealthPercent = 40, alliedTeamSize = 1, opposingTeamSize = 1)
    private val report = QuestBattleReport("battle", BattleOutcome.ALLIED_VICTORY, 60000, 0, 1, 1, 3, 100)
    private val member = QuestBattleMember("battle", "partner", true, 400, 1000)
    private fun failure(g: QuestObjective = goal, r: QuestBattleReport? = report, m: QuestBattleMember? = member,
                        hits: Int = 2, outcome: BattleOutcome = BattleOutcome.ALLIED_VICTORY) =
        QuestBattleConditions.evaluate(g, "partner", "battle", outcome, r, m, hits)

    @Test fun `inclusive boundaries qualify only when every condition is satisfied`() { assertNull(failure()) }
    @Test fun `missing facts never masquerade as zero item use or high HP`() {
        assertEquals(QuestBattleFailure.REPORT_MISSING, failure(r = null))
        assertEquals(QuestBattleFailure.REPORT_MISSING, failure(m = null))
    }
    @Test fun `different battle or partner facts cannot qualify`() {
        assertEquals(QuestBattleFailure.REPORT_MISSING, failure(r = report.copy(interactionId = "other")))
        assertEquals(QuestBattleFailure.REPORT_MISSING, failure(m = member.copy(individualId = "teammate")))
        assertEquals(QuestBattleFailure.REPORT_MISSING, failure(m = member.copy(allied = false)))
    }
    @Test fun `loss draw and abandonment never qualify`() {
        listOf(BattleOutcome.OPPOSING_VICTORY, BattleOutcome.DRAW, BattleOutcome.ABANDONED).forEach {
            assertEquals(QuestBattleFailure.NO_VICTORY, failure(outcome = it))
        }
    }
    @Test fun `extra teammate or opponent fails a solo trial`() {
        assertEquals(QuestBattleFailure.TEAM_SIZE, failure(r = report.copy(alliedTeamSize = 2)))
        assertEquals(QuestBattleFailure.TEAM_SIZE, failure(r = report.copy(opposingTeamSize = 2)))
    }
    @Test fun `a single extra millisecond fails the time requirement`() { assertEquals(QuestBattleFailure.TIME_LIMIT, failure(r = report.copy(elapsedMillis = 60001))) }
    @Test fun `any consumed item fails an item free trial`() { assertEquals(QuestBattleFailure.ITEM_LIMIT, failure(r = report.copy(itemsUsed = 1))) }
    @Test fun `fractional health below the threshold is not rounded up`() { assertEquals(QuestBattleFailure.HEALTH_LIMIT, failure(m = member.copy(health = 399))) }
    @Test fun `equipped unused or insufficient hits fails the demonstration`() {
        assertEquals(QuestBattleFailure.TECHNIQUE_HITS, failure(hits = 0))
        assertEquals(QuestBattleFailure.TECHNIQUE_HITS, failure(hits = 1))
    }
    @Test fun `large health values do not overflow the percentage comparison`() {
        assertNull(failure(m = member.copy(health = 1000000000, maxHealth = 2000000000)))
    }
    @Test fun `unspecified constraints do not create hidden requirements`() {
        assertNull(failure(g = QuestObjective("o", "q", 0, QuestObjectiveType.WIN_BATTLE_CONDITION, 1),
            r = report.copy(itemsUsed = 9, elapsedMillis = 900000), m = member.copy(health = 1), hits = 0))
    }
}
