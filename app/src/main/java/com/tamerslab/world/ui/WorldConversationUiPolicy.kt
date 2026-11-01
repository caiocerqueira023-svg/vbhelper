package com.tamerslab.world.ui

import com.tamerslab.world.ecosystem.*

internal fun worldConversationCanParticipate(event: WorldInteraction?, snapshot: EcosystemSnapshot, allowAnyDistance: Boolean = false): Boolean {
    if(event==null || event.type!=InteractionType.CHAT || event.origin==InteractionOrigin.DIRECT_PLAYER ||
        event.state !in listOf(InteractionState.ACTIVE,InteractionState.PLAYER_CONTROLLED)) return false
    val stamp=snapshot.commandStamp ?: return false
    return snapshot.commandRejection(RadarCommand(stamp,RadarCommandKind.EVENT_PARTICIPATION,interactionId=event.id),snapshot.observedAt,allowAnyDistance)==null
}
