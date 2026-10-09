package com.github.nacabaro.vbhelper.quests

import com.github.nacabaro.vbhelper.utils.DeviceType

enum class QuestWatchNotice { DEVICE_MISMATCH, BATTLE_DISCONTINUITY, TROPHY_DISCONTINUITY, PP_DISCONTINUITY, ADVENTURE_DISCONTINUITY }

data class QuestWatchObservation(val family: DeviceType, val cardId: Long?, val charIndex: Int, val history: String,
    val won: Int, val lost: Int, val trophies: Int, val lifetimeTrophies: Int?, val adventureNext: Int)

data class QuestWatchDelta(val battles: Int?, val wins: Int?, val trophiesEarned: Int?, val ppEarned: Int?,
    val ppCurrent: Int?, val adventureAdvance: Int?, val notices: Set<QuestWatchNotice>)

/** Evidence policy consumes serialized watch observations, never app-wide progress or narration. */
object QuestWatchEvidence {
    fun evaluate(sent: QuestWatchBaseline, received: QuestWatchObservation): QuestWatchDelta {
        if (sent.family != null && sent.family != received.family) return QuestWatchDelta(null, null, null, null, null, null,
            setOf(QuestWatchNotice.DEVICE_MISMATCH))
        val notices = mutableSetOf<QuestWatchNotice>()
        val won = received.won - sent.won
        val lost = received.lost - sent.lost
        val monotonicBattles = won >= 0 && lost >= 0
        if (!monotonicBattles) notices += QuestWatchNotice.BATTLE_DISCONTINUITY
        val sameForm = sent.charIndex == received.charIndex && sent.history == received.history
        val trophies = if (received.family == DeviceType.VBDevice && sent.lifetimeTrophies != null && received.lifetimeTrophies != null) {
            (received.lifetimeTrophies - sent.lifetimeTrophies).takeIf { it >= 0 }
        } else null
        if (received.family == DeviceType.VBDevice && trophies == null) notices += QuestWatchNotice.TROPHY_DISCONTINUITY
        val pp = if (received.family == DeviceType.BEDevice && sent.family == DeviceType.BEDevice && sameForm) {
            (received.trophies - sent.trophies).takeIf { it >= 0 }
        } else null
        if (received.family == DeviceType.BEDevice && pp == null) notices += QuestWatchNotice.PP_DISCONTINUITY
        val limit = sent.adventureLimit
        val previous = sent.adventureNext
        val advance = if (sent.cardId != null && sent.cardId == received.cardId && limit != null && previous != null &&
            previous in 1..limit && received.adventureNext in previous..limit) received.adventureNext - previous else null
        if (advance == null) notices += QuestWatchNotice.ADVENTURE_DISCONTINUITY
        return QuestWatchDelta(if (monotonicBattles) won + lost else null, if (monotonicBattles) won else null,
            trophies, pp, if (received.family == DeviceType.BEDevice && sent.family == DeviceType.BEDevice) received.trophies else null,
            advance, notices)
    }
}
