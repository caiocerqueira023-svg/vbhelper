package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.world.RadarWorldGeometry

enum class RadarCommandKind { ENCOUNTER, REGION, SETTINGS, BATTLE_RESERVATION, BATTLE_COMMIT, EVENT_PARTICIPATION, EVENT_RESERVATION }
enum class RadarRejection { INACTIVE, NOT_READY, STALE_SNAPSHOT, STALE_LOCATION, UNAVAILABLE, OUT_OF_RANGE, CLAIMED }

data class RadarCommandStamp(val leaseEpoch: Long, val revision: Long, val status: EcosystemStatus)
data class RadarCommand(val stamp: RadarCommandStamp, val kind: RadarCommandKind, val individualId: String? = null, val interactionId: String? = null)
data class RadarCommandContext(val snapshot: EcosystemSnapshot) {
    val session: WorldEcosystemSession get() = requireNotNull(snapshot.session)
    val playerFix: WorldPlayerFix get() = requireNotNull(snapshot.playerFix)
}

class RadarCommandException(val rejection: RadarRejection? = null, val issue: EcosystemIssue? = null) :
    IllegalStateException(rejection?.name ?: issue?.name)

sealed interface RadarCommandResult<out T> {
    data class Applied<T>(val value: T) : RadarCommandResult<T>
    data class Rejected(val reason: RadarRejection) : RadarCommandResult<Nothing>
    data class Failed(val issue: EcosystemIssue) : RadarCommandResult<Nothing>
}

fun <T> RadarCommandResult<T>.getOrThrow(): T = when (this) {
    is RadarCommandResult.Applied -> value
    is RadarCommandResult.Rejected -> throw RadarCommandException(rejection = reason)
    is RadarCommandResult.Failed -> throw RadarCommandException(issue = issue)
}

/** Shared UI/commit eligibility. The coordinator additionally verifies the submitting lease. */
fun EcosystemSnapshot.commandRejection(command: RadarCommand, now: Long): RadarRejection? {
    val expectedStatus = if (command.kind == RadarCommandKind.BATTLE_COMMIT) EcosystemStatus.FROZEN else EcosystemStatus.READY
    if (status != expectedStatus || issue != null || session == null) return RadarRejection.NOT_READY
    if (command.stamp != commandStamp) return RadarRejection.STALE_SNAPSHOT
    if (command.kind != RadarCommandKind.SETTINGS && playerFix?.isFresh(now) != true) return RadarRejection.STALE_LOCATION
    if (command.kind == RadarCommandKind.ENCOUNTER || command.kind == RadarCommandKind.BATTLE_RESERVATION) {
        val individual = individuals.firstOrNull { it.individualId == command.individualId } ?: return RadarRejection.UNAVAILABLE
        if (individual.individualId in claimedIndividuals) return RadarRejection.CLAIMED
        if (!RadarWorldGeometry.relative(playerFix!!.position, individual.position).withinInteractionRange) return RadarRejection.OUT_OF_RANGE
    }
    if(command.kind==RadarCommandKind.EVENT_PARTICIPATION || command.kind==RadarCommandKind.EVENT_RESERVATION) {
        val event=interactions.firstOrNull { it.id==command.interactionId } ?: return RadarRejection.UNAVAILABLE
        if(event.state.terminal || event.participantIds.isEmpty())return RadarRejection.UNAVAILABLE
        event.participantIds.forEach { id ->
            val actor=individuals.firstOrNull { it.individualId==id } ?: return RadarRejection.UNAVAILABLE
            if(!RadarWorldGeometry.relative(playerFix!!.position,actor.position).withinInteractionRange) return RadarRejection.OUT_OF_RANGE
        }
    }
    return null
}
