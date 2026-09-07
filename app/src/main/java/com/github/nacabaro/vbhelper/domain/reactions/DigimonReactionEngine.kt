package com.github.nacabaro.vbhelper.domain.reactions

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.device_data.UserCharacter
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
                    "Seu estágio regrediu de ${previous.stage} para $currentStage porque seu Tamer escolheu fazer você degenerar. Reaja de forma realista à regressão, ao custo de 5.000 bits e ao fato de seus vitais terem sido zerados."
                )
            } else {
                events += ReactionEvent(
                    ReactionType.EVOLUTION,
                    "Seu estágio mudou de ${previous.stage} para $currentStage depois de um período real no relógio. Reflita sobre essa evolução em uma ou duas frases."
                )
            }
        }

        val wins = current.totalBattlesWon - previous.totalBattlesWon
        if (wins > 0) {
            events += ReactionEvent(
                ReactionType.BATTLE_WIN,
                "O relógio registrou $wins vitória(s) em batalha física desde o último scan. Comente essa conquista com seu tamer."
            )
        }

        val losses = current.totalBattlesLost - previous.totalBattlesLost
        if (losses > 0) {
            events += ReactionEvent(
                ReactionType.BATTLE_LOSS,
                "O relógio registrou $losses derrota(s) em batalha física desde o último scan. Comente como se sente e diga que vai continuar treinando."
            )
        }

        val wasInjured = previous.injuryStatus != NfcCharacter.InjuryStatus.None.name
        val isInjured = current.injuryStatus != NfcCharacter.InjuryStatus.None
        if (!wasInjured && isInjured) {
            events += ReactionEvent(
                ReactionType.INJURY_GAINED,
                "O scan real mostrou que você se machucou em uma batalha física. Reaja com preocupação e peça cuidado ao seu tamer."
            )
        } else if (wasInjured && !isInjured) {
            events += ReactionEvent(
                ReactionType.INJURY_HEALED,
                "O scan real mostrou que sua lesão foi curada. Agradeça ao seu tamer por cuidar de você."
            )
        }

        var newWinsMilestone = lastCelebratedWinsMilestone
        winMilestones.lastOrNull { it <= current.totalBattlesWon && it > lastCelebratedWinsMilestone }?.let {
            events += ReactionEvent(
                ReactionType.MILESTONE_WINS,
                "Você cruzou o marco de $it vitórias físicas totais no relógio. Celebre com seu tamer."
            )
            newWinsMilestone = it
        }

        var newTrophyMilestone = lastCelebratedTrophyMilestone
        trophyMilestones.lastOrNull { it <= current.trophies && it > lastCelebratedTrophyMilestone }?.let {
            events += ReactionEvent(
                ReactionType.MILESTONE_TROPHIES,
                "Você cruzou o marco de $it troféus no relógio. Comemore esse progresso."
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
                    "A missão ${mission.missionType} avançou no relógio para ${mission.progress} de ${mission.goal}. ${if (remaining > 0) "Faltam $remaining." else "Ela foi concluída!"}"
                )
            }
        }

        return ReactionResult(events, newWinsMilestone, newTrophyMilestone)
    }

    private fun parseMissions(json: String): List<SnapshotMission> =
        runCatching { gson.fromJson<List<SnapshotMission>>(json, missionListType) }.getOrNull().orEmpty()
}
