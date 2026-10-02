package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry

/** Wild encounters are initiated only with actual, resumed player presence. */
internal object WorldWildInitiationPolicy {
    const val GLOBAL_COOLDOWN_TICKS = 80L
    const val INDIVIDUAL_COOLDOWN_TICKS = 160L

    fun kind(personality: DigimonPersonalityType, emotion: Int, hasOwnedPartner: Boolean, roll: Double): InteractionType {
        val attacking=emotion <= -20 || personality == DigimonPersonalityType.RECKLESS && roll < 0.35
        return if(hasOwnedPartner && attacking) InteractionType.BATTLE else InteractionType.CHAT
    }

    fun eligible(actor: WorldSpawn, fix: WorldPlayerFix?, now: Long, visible: Boolean, replay: Boolean, claimed: Boolean): Boolean {
        if(!visible || replay || claimed || fix?.isFresh(now)!=true || actor.expiresAt<=now || actor.recruitmentState!=RecruitmentState.WILD) return false
        val position=GeoPoint.fromOrNull(actor.latitude,actor.longitude) ?: return false
        return RadarWorldGeometry.relative(fix.position,position).withinInteractionRange
    }
}

internal val WorldInteraction.isWildAttack: Boolean get() = publicReason?.startsWith("WILD_ATTACK:")==true
internal val WorldInteraction.isPlayerBattle: Boolean get() = isWildAttack || publicReason?.startsWith("WILD_SPARRING:")==true
internal val EcosystemInteractionSummary.isPlayerBattle: Boolean get() = publicReason?.startsWith("WILD_ATTACK:")==true || publicReason?.startsWith("WILD_SPARRING:")==true
internal val WorldInteraction.isPlayerDirected: Boolean get() = isPlayerBattle || publicReason?.startsWith("WILD_CHAT:")==true
