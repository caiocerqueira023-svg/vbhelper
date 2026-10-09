package com.github.nacabaro.vbhelper.world.ecosystem

import com.github.nacabaro.vbhelper.domain.world.WorldMovementState
import com.github.nacabaro.vbhelper.world.GeoPoint

enum class EcosystemStatus { LOADING, UPDATING, READY, SUSPENDED, FROZEN, FAILED }
enum class EcosystemIssue { UNSUPPORTED_RULES, STORAGE }

data class EcosystemInteractionSummary(
    val id: String,
    val type: InteractionType,
    val origin: InteractionOrigin,
    val state: InteractionState,
    val revision: Long,
    val participantIds: List<String>,
    val expiresAt: Long,
    val publicReason: String? = null,
    val utterances: List<EcosystemUtterance> = emptyList(),
    val nextActionTick: Long = 0,
    val combatants: Map<String,NpcCombatantSummary> = emptyMap()
)

data class EcosystemUtterance(val speakerId: String, val body: String, val tick: Long)

fun EcosystemSnapshot.publicInteractionFor(individualId: String): EcosystemInteractionSummary? = interactions.firstOrNull {
    !it.state.terminal && individualId in it.participantIds && !(it.type == InteractionType.CHAT && it.origin == InteractionOrigin.DIRECT_PLAYER)
}

fun EcosystemSnapshot.speechFor(individualId: String): String? =
    (publicInteractionFor(individualId)?.utterances.orEmpty() + trailingUtterances)
        .filter { it.speakerId == individualId && (session?.tickIndex ?: 0) - it.tick in 0..8 }
        .maxByOrNull { it.tick }?.body?.take(100)

data class EcosystemIndividual(
    val individualId: String,
    val spawnId: Long,
    val position: GeoPoint,
    val home: GeoPoint,
    val wanderTarget: GeoPoint?,
    val movementState: WorldMovementState,
    val anchorRadiusMeters: Double,
    val denId: String?,
    val emotion: Int,
    val previousPosition: GeoPoint? = null,
    val motionFrameNanos: Long = 0
)

/** Geographic state only. Renderers may interpolate display positions, never eligibility. */
data class EcosystemSnapshot(
    val status: EcosystemStatus = EcosystemStatus.LOADING,
    val session: WorldEcosystemSession? = null,
    val individuals: List<EcosystemIndividual> = emptyList(),
    val issue: EcosystemIssue? = null,
    val interactions: List<EcosystemInteractionSummary> = emptyList(),
    val claimedIndividuals: Set<String> = emptySet(),
    val playerFix: WorldPlayerFix? = null,
    val leaseEpoch: Long = 0,
    val observedAt: Long = 0,
    val trailingUtterances: List<EcosystemUtterance> = emptyList()
) {
    val commandStamp: RadarCommandStamp? get() = session?.let { RadarCommandStamp(leaseEpoch, it.revision, status) }
}
