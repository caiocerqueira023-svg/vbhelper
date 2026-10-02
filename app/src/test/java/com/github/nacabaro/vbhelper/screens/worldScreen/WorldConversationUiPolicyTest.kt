package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.world.GeoPoint
import com.github.nacabaro.vbhelper.world.RadarWorldGeometry
import com.github.nacabaro.vbhelper.world.ecosystem.*
import org.junit.Assert.*
import org.junit.Test

class WorldConversationUiPolicyTest {
    private val event=WorldInteraction("chat",InteractionType.CHAT,InteractionOrigin.AUTONOMOUS,InteractionState.ACTIVE,
        42,startTick=0,nextActionTick=0,createdAt=0,expiresAt=100_000)
    private val actor=EcosystemIndividual("wild",1,GeoPoint(0.0,0.0),GeoPoint(0.0,0.0),null,WorldMovementState.HOME,25.0,null,0)
    private val snapshot=EcosystemSnapshot(status=EcosystemStatus.READY,session=WorldEcosystemSession(seed=42,lastCheckpointAt=0),
        individuals=listOf(actor),playerFix=WorldPlayerFix(actor.position,10_000),observedAt=10_000,
        interactions=listOf(EcosystemInteractionSummary(event.id,event.type,event.origin,event.state,event.revision,listOf("wild"),event.expiresAt)))

    @Test fun aLocalPublicConversationAllowsTheRegularComposerBeforeItsFirstPlayerMessage() {
        assertTrue(worldConversationCanParticipate(event,snapshot))
        assertTrue(worldConversationCanParticipate(event.copy(state=InteractionState.PLAYER_CONTROLLED,origin=InteractionOrigin.JOINED_PLAYER),snapshot))
    }
    @Test fun remoteAndStaleConversationViewsRemainReadOnly() {
        val far=RadarWorldGeometry.offset(actor.position,41.0,0.0)
        assertFalse(worldConversationCanParticipate(event,snapshot.copy(individuals=listOf(actor.copy(position=far)))))
        assertFalse(worldConversationCanParticipate(event,snapshot.copy(observedAt=40_001)))
        assertFalse(worldConversationCanParticipate(event,snapshot.copy(individuals=emptyList())))
    }
    @Test fun privateBattleEndedOrUnreadyEventsCannotBeJoinedThroughThePublicChatRoute() {
        assertFalse(worldConversationCanParticipate(event.copy(origin=InteractionOrigin.DIRECT_PLAYER),snapshot))
        assertFalse(worldConversationCanParticipate(event.copy(type=InteractionType.BATTLE),snapshot))
        assertFalse(worldConversationCanParticipate(event.copy(state=InteractionState.ENDED),snapshot))
        assertFalse(worldConversationCanParticipate(event,snapshot.copy(status=EcosystemStatus.LOADING)))
    }
    @Test fun acceptedSingleWildSparringUsesPlayerHandoffWithoutBecomingAHostileAttack() {
        val sparring=event.copy(type=InteractionType.BATTLE,publicReason="WILD_SPARRING:Friendly practice")
        assertTrue(sparring.isPlayerBattle)
        assertTrue(sparring.isPlayerDirected)
        assertFalse(sparring.isWildAttack)
    }
}
