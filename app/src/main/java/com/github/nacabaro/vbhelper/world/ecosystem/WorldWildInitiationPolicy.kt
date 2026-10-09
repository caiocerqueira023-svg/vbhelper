package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.personality.DigimonPersonalityType
import com.github.nacabaro.vbhelper.domain.personality.DigimonSocialProfile
import com.github.nacabaro.vbhelper.domain.personality.SocialRandom
import com.github.nacabaro.vbhelper.domain.world.RecruitmentState
import com.github.nacabaro.vbhelper.domain.world.WorldSpawn
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry

/** Wild encounters are initiated only with actual, resumed player presence. */
internal object WorldWildInitiationPolicy {
    const val GLOBAL_COOLDOWN_TICKS = WorldSocialPlanner.PLAYER_GLOBAL_COOLDOWN_TICKS
    const val INDIVIDUAL_COOLDOWN_TICKS = 160L

    fun kind(personality: DigimonPersonalityType, emotion: Int, hasOwnedPartner: Boolean, roll: Double): InteractionType {
        val p = DigimonSocialProfile.forIndividual("policy", personality)
        val attackChance = (.03 + p.challenge * .12 + p.territoriality * .08 +
            (-emotion).coerceIn(0, 50) / 50.0 * .25).coerceAtMost(.4)
        val attacking = roll < attackChance
        return if(hasOwnedPartner && attacking) InteractionType.BATTLE else InteractionType.CHAT
    }

    fun eligible(actor: WorldSpawn, fix: WorldPlayerFix?, now: Long, visible: Boolean, replay: Boolean, claimed: Boolean,
        allowApproach: Boolean = false): Boolean {
        if(!visible || replay || claimed || fix?.isFresh(now)!=true || actor.expiresAt<=now || actor.recruitmentState!=RecruitmentState.WILD) return false
        val position=GeoPoint.fromOrNull(actor.latitude,actor.longitude) ?: return false
        if (RadarWorldGeometry.relative(fix.position,position).withinInteractionRange) return true
        val home = GeoPoint.fromOrNull(actor.homeLatitude, actor.homeLongitude) ?: return false
        return allowApproach && RadarWorldGeometry.relative(fix.position, home)
            .withinRadius(actor.anchorRadiusMeters.coerceIn(1.0, 40.0) + RadarWorldGeometry.INTERACTION_RANGE_METERS - 1.0)
    }
}

internal val WorldInteraction.isWildAttack: Boolean get() = publicReason?.startsWith("WILD_ATTACK:")==true
internal val WorldInteraction.isPlayerBattle: Boolean get() = isWildAttack || publicReason?.startsWith("WILD_SPARRING:")==true
internal val EcosystemInteractionSummary.isPlayerBattle: Boolean get() = publicReason?.startsWith("WILD_ATTACK:")==true || publicReason?.startsWith("WILD_SPARRING:")==true
internal val WorldInteraction.isPlayerDirected: Boolean get() = isPlayerBattle || publicReason?.startsWith("WILD_CHAT:")==true
