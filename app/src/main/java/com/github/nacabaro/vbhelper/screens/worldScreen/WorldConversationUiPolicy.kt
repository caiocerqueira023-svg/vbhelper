package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.world.ecosystem.*

internal fun worldConversationCanParticipate(event: WorldInteraction?, snapshot: EcosystemSnapshot): Boolean {
    if(event==null || event.type!=InteractionType.CHAT || event.origin==InteractionOrigin.DIRECT_PLAYER ||
        event.state !in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) return false
    val stamp=snapshot.commandStamp ?: return false
    return snapshot.commandRejection(RadarCommand(stamp,RadarCommandKind.EVENT_PARTICIPATION,interactionId=event.id),snapshot.observedAt)==null
}
