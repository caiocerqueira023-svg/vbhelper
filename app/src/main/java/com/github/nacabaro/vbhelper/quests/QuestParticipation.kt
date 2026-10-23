package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.species.PartnerParticipationSnapshot

/** Shared by quest actions, evidence producers and the mode-change transaction. */
fun AppDatabase.questParticipationAllowed(quest: QuestInstance,
    eligibility: PartnerParticipationSnapshot? = participation?.snapshot()): Boolean {
    if (eligibility == null) return true
    if (!eligibility.card(quest.giverCardCharacterId).usable) return false
    if (quest.partnerId != null && !eligibility.individual(quest.partnerId).usable) return false
    return questDao().objectives(quest.id).filter { it.progress < it.required }.all { goal ->
        (goal.targetCardCharacterId == null || eligibility.card(goal.targetCardCharacterId).usable) &&
            (goal.targetIndividualId == null || eligibility.individual(goal.targetIndividualId).usable)
    }
}
