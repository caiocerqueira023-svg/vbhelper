package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import org.junit.Assert.*
import org.junit.Test

class QuestRulesTest {
    private val quest = QuestInstance("q", "giver", 1, "Giver", QuestCategory.NORMAL, "courier", 4, 42, 2,
        state = QuestState.ACTIVE, acceptedAt = 100, phaseStartedAt = 200, currentPhase = 1, createdAt = 0)
    @Test fun `completed earlier steps do not unlock every future objective`() {
        val objectives = listOf(
            QuestObjective("old", "q", 0, QuestObjectiveType.COLLECT_TOKEN, 1, progress = 1, phase = 0),
            QuestObjective("now", "q", 1, QuestObjectiveType.DELIVER_TOKEN, 1, phase = 1),
            QuestObjective("future", "q", 2, QuestObjectiveType.DELIVER_TOKEN, 1, phase = 2))
        assertEquals(listOf("now"), QuestSteps.current(quest, objectives).map { it.id })
        assertEquals(1, QuestSteps.nextPhase(objectives))
    }
    @Test fun `all parallel requirements must finish before advancing`() {
        val goals = listOf(QuestObjective("a", "q", 0, QuestObjectiveType.WATCH_WINS, 3, progress = 3),
            QuestObjective("b", "q", 1, QuestObjectiveType.WATCH_BATTLES, 6, progress = 5),
            QuestObjective("c", "q", 2, QuestObjectiveType.DEFEAT_TARGET, 1, phase = 1))
        assertEquals(0, QuestSteps.nextPhase(goals))
        assertEquals(1, QuestSteps.nextPhase(goals.map { if (it.id == "b") it.copy(progress = 6) else it }))
        assertNull(QuestSteps.nextPhase(goals.map { it.copy(progress = it.required) }))
    }
    @Test fun `physical errands do not require a hidden partner but watch and combat do`() {
        assertFalse(QuestSteps.needsPartner(listOf(QuestObjective("a", "q", 0, QuestObjectiveType.MEET_TARGET, 1))))
        listOf(QuestObjectiveType.WIN_BATTLE_CONDITION, QuestObjectiveType.WATCH_PP_EARN, QuestObjectiveType.WATCH_ADVENTURE_ADVANCE).forEach {
            assertTrue(QuestSteps.needsPartner(listOf(QuestObjective("a", "q", 0, it, 1))))
        }
    }
    @Test fun `source eligibility begins at current step activation not quest acceptance`() {
        assertEquals(200, QuestSteps.startsAt(quest))
        assertEquals(100, QuestSteps.startsAt(quest.copy(phaseStartedAt = null)))
    }
    @Test fun `recruitment effort is monotonic and Champion plus always has watch work`() {
        val efforts = (0..6).map(QuestDifficulty::recruitment)
        efforts.zipWithNext().forEach { (a, b) ->
            assertTrue(b.watchBattles >= a.watchBattles && b.watchWins >= a.watchWins && b.trophies >= a.trophies && b.partnerStage >= a.partnerStage)
        }
        (3..6).forEach { assertTrue(QuestDifficulty.recruitment(it).watchBattles > 0) }
    }
    @Test fun `follow up branches use actual interests and remain bounded`() {
        val helpful = DigimonSocialProfile(DigimonPersonalityType.COMPASSIONATE, empathy = .9, training = .2)
        val trainer = helpful.copy(training = .95)
        assertEquals("supply_round", QuestChains.nextTemplate("courier", 0, helpful))
        assertEquals("technique_trial", QuestChains.nextTemplate("courier", 0, trainer))
        QuestTemplates.all.forEach { template ->
            assertNull(QuestChains.nextTemplate(template.id, QuestChains.MAX_DEPTH, helpful))
            QuestChains.nextTemplate(template.id, 0, helpful)?.let { assertNotNull(QuestTemplates.get(it)) }
        }
    }
}
