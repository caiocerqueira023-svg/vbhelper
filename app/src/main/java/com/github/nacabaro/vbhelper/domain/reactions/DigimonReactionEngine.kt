package com.github.nacabaro.vbhelper.domain.reactions

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
import com.github.nacabaro.vbhelper.chat.PromptLocalization
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

enum class ReactionType {
    EVOLUTION,
    DEGENERATION,
    BATTLE_WIN,
    BATTLE_LOSS,
    INJURY_GAINED,
    INJURY_HEALED,
    MILESTONE_WINS,
    MILESTONE_TROPHIES,
    MISSION_PROGRESS
}

data class ReactionEvent(val type: ReactionType, val prompt: String)

data class ReactionResult(
    val events: List<ReactionEvent>,
    val newWinsMilestone: Int,
    val newTrophyMilestone: Int
)

object DigimonReactionEngine {
    private val gson = Gson()
    private val missionListType = object : TypeToken<List<SnapshotMission>>() {}.type
    private val winMilestones = listOf(10, 25, 50, 100, 250, 500, 1000)
    private val trophyMilestones = listOf(10, 50, 100, 250, 500, 1000, 2500, 5000)

    fun compareStates(
        previous: DigimonStateSnapshot,
        current: UserCharacter,
        currentStage: Int,
        currentSpecialMissionsJson: String,
        lastCelebratedWinsMilestone: Int,
        lastCelebratedTrophyMilestone: Int
    ): ReactionResult {
        val events = mutableListOf<ReactionEvent>()

        if (currentStage != previous.stage) {
            if (currentStage < previous.stage) {
                events += ReactionEvent(
                    ReactionType.DEGENERATION,
                    PromptLocalization.degenerationEvent(
                        PromptLocalization.currentLanguageTag(),
                        previous.stage,
                        currentStage
                    )
                )
            } else {
                events += ReactionEvent(
                    ReactionType.EVOLUTION,
                    PromptLocalization.evolutionEvent(PromptLocalization.currentLanguageTag(), previous.stage, currentStage)
                )
            }
        }

        val wins = current.totalBattlesWon - previous.totalBattlesWon
        if (wins > 0) {
            events += ReactionEvent(
                ReactionType.BATTLE_WIN,
                PromptLocalization.battleEvent(PromptLocalization.currentLanguageTag(), wins, true)
            )
        }

        val losses = current.totalBattlesLost - previous.totalBattlesLost
        if (losses > 0) {
            events += ReactionEvent(
                ReactionType.BATTLE_LOSS,
                PromptLocalization.battleEvent(PromptLocalization.currentLanguageTag(), losses, false)
            )
        }

        val wasInjured = previous.injuryStatus != NfcCharacter.InjuryStatus.None.name
        val isInjured = current.injuryStatus != NfcCharacter.InjuryStatus.None
        if (!wasInjured && isInjured) {
            events += ReactionEvent(
                ReactionType.INJURY_GAINED,
                PromptLocalization.injuryEvent(PromptLocalization.currentLanguageTag(), false)
            )
        } else if (wasInjured && !isInjured) {
            events += ReactionEvent(
                ReactionType.INJURY_HEALED,
                PromptLocalization.injuryEvent(PromptLocalization.currentLanguageTag(), true)
            )
        }

        var newWinsMilestone = lastCelebratedWinsMilestone
        winMilestones.lastOrNull { it <= current.totalBattlesWon && it > lastCelebratedWinsMilestone }?.let {
            events += ReactionEvent(
                ReactionType.MILESTONE_WINS,
                PromptLocalization.milestoneEvent(PromptLocalization.currentLanguageTag(), it, false)
            )
            newWinsMilestone = it
        }

        var newTrophyMilestone = lastCelebratedTrophyMilestone
        trophyMilestones.lastOrNull { it <= current.trophies && it > lastCelebratedTrophyMilestone }?.let {
            events += ReactionEvent(
                ReactionType.MILESTONE_TROPHIES,
                PromptLocalization.milestoneEvent(PromptLocalization.currentLanguageTag(), it, true)
            )
            newTrophyMilestone = it
        }

        val previousMissions = parseMissions(previous.specialMissionsJson)
        val currentMissions = parseMissions(currentSpecialMissionsJson)
        currentMissions.forEach { mission ->
            val previousProgress = previousMissions
                .firstOrNull { it.watchId == mission.watchId }
                ?.progress
                ?: 0
            if (mission.progress > previousProgress && mission.goal > 0) {
                val remaining = (mission.goal - mission.progress).coerceAtLeast(0)
                events += ReactionEvent(
                    ReactionType.MISSION_PROGRESS,
                    PromptLocalization.missionEvent(
                        PromptLocalization.currentLanguageTag(),
                        mission.missionType.toString(),
                        mission.progress,
                        mission.goal,
                        remaining
                    )
                )
            }
        }

        return ReactionResult(events, newWinsMilestone, newTrophyMilestone)
    }

    private fun parseMissions(json: String): List<SnapshotMission> =
        runCatching { gson.fromJson<List<SnapshotMission>>(json, missionListType) }.getOrNull().orEmpty()
}
