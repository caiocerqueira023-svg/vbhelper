package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.battle.offline.core.BattleOutcome
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.toRadarBattleEffect

enum class InteractionFailure { BUSY, UNAVAILABLE, STALE_LOCATION }
class WorldInteractionException(val reason: InteractionFailure) : IllegalStateException(reason.name)

data class WorldPlayerFix(val position: GeoPoint, val capturedAt: Long, val accuracyMeters: Float? = null) {
    fun isFresh(now: Long): Boolean = capturedAt >= 0 && now >= capturedAt && now - capturedAt <= 30_000L
}

data class InteractionBattleEffects(val ownedWon: Boolean?, val removeOpposingWilds: Boolean)

object WorldInteractionPolicy {
    const val RESERVATION_MILLIS = 30_000L
    const val MAX_BATTLE_MILLIS = 30 * 60 * 1_000L
    const val MAX_CHAT_MILLIS = 5 * 60 * 1_000L

    fun canTransition(from: InteractionState, to: InteractionState): Boolean = when (from) {
        InteractionState.PROPOSED -> to == InteractionState.ACTIVE || to == InteractionState.CANCELLED
        InteractionState.ACTIVE -> to == InteractionState.RESERVED || to == InteractionState.RESOLVING || to == InteractionState.CANCELLED
        InteractionState.RESERVED -> to == InteractionState.PLAYER_CONTROLLED || to == InteractionState.ACTIVE || to == InteractionState.CANCELLED
        InteractionState.PLAYER_CONTROLLED -> to == InteractionState.RESOLVING || to == InteractionState.INTERRUPTED || to == InteractionState.CANCELLED
        InteractionState.RESOLVING -> to == InteractionState.ENDED || to == InteractionState.INTERRUPTED
        InteractionState.ENDED, InteractionState.CANCELLED, InteractionState.INTERRUPTED -> false
    }

    fun battleEffects(origin: InteractionOrigin, outcome: BattleOutcome, friendly: Boolean = false): InteractionBattleEffects {
        if (origin == InteractionOrigin.AUTONOMOUS) return InteractionBattleEffects(null, false)
        val effect = outcome.toRadarBattleEffect()
        return InteractionBattleEffects(effect.won, effect.removesSpawn && !friendly)
    }
}
