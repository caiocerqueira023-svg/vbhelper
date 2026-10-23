package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.domain.transfer.NativeAppProjection
import com.github.nacabaro.vbhelper.gamesave.NativeStatParameters
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import com.github.nacabaro.vbhelper.gamesave.WorldDsRecordCodec
import com.github.nacabaro.vbhelper.gamesave.WorldDsRecordDefaults
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion

/** Everything the send path needs to project one app partner into a World DS record. */
internal data class WorldDsOutboundState(
    val dsSpeciesId: Long,
    val canonicalSpeciesId: String,
    val displayName: String,
    val fallbackName: String,
    val stage: Int,
    val baseHp: Int,
    val baseBp: Int,
    val baseAp: Int,
    val appStats: WorldDsStatConversion.AppStats,
)

internal object WorldDsOutboundConversion {
    /** True when the app state still equals the checkpoint captured at the last native sync. */
    fun matchesBaseline(state: WorldDsOutboundState, projection: NativeAppProjection): Boolean =
        projection.stage == state.stage && projection.baseHp == state.baseHp && projection.baseBp == state.baseBp &&
            projection.baseAp == state.baseAp && projection.trainingHp == state.appStats.trainingHp &&
            projection.trainingAp == state.appStats.trainingAp && projection.trainingBp == state.appStats.trainingBp &&
            projection.vitalPoints == state.appStats.vitalPoints && projection.wins == state.appStats.wins &&
            projection.losses == state.appStats.losses

    fun generate(
        state: WorldDsOutboundState,
        seedBase64: String?,
        defaults: WorldDsRecordDefaults? = null,
        lineage: List<Long> = emptyList(),
    ): ObservedDigimon {
        val native = WorldDsStatConversion.toNative(state.appStats)
        val decoded = seedBase64?.let { runCatching { WorldDsRecordCodec.decodeBase64(it) }.getOrNull() }
        val seed = decoded?.takeIf { it.size == WorldDsRecordCodec.RECORD_BYTES }
        decoded?.takeIf { it.size != WorldDsRecordCodec.RECORD_BYTES }?.fill(0)
        val seededLocalId = seed?.let { WorldDsRecordCodec.localId(it) }?.takeIf { it in 1 until 0xFFFFFF00L }
        val seededSpeciesId = seed?.let { WorldDsRecordCodec.speciesId(it) }
            ?.takeIf { it in 0 until WorldDsSaveReader.SPECIES_COUNT.toLong() }
        val localId = seededLocalId ?: 1L
        val speciesId = if (seededLocalId == null) state.dsSpeciesId else seededSpeciesId ?: state.dsSpeciesId
        val generated = try {
            WorldDsRecordCodec.generate(localId, speciesId, state.displayName, state.fallbackName, native, seed, defaults, lineage)
        } finally {
            seed?.fill(0)
        }
        val identity = seededLocalId?.let { "save-local:$it" }
        return ObservedDigimon(
            recordKey = if (identity == null) "generated" else "retained",
            container = SaveContainer.PARTY,
            position = 0,
            nativeSpeciesId = speciesId,
            storedName = generated.name,
            level = native.level,
            personalityId = null,
            identityCandidate = 0,
            membership = RecordMembership.ACTIVE_RECORD,
            nativeRecordBase64 = WorldDsRecordCodec.base64(generated.bytes),
            nativeIdentity = identity,
            nativeParameters = NativeStatParameters(native.hp, native.maxHp, native.mp, native.maxMp,
                native.attack, native.defense, native.spirit, native.speed, generated.maxLevel, native.exp, 1),
        )
    }
}