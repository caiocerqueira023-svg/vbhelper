package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.NativeAppProjection
import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.WorldDsRecordCodec
import com.tamerslab.gamesave.WorldDsRecordDefaults
import com.tamerslab.gamesave.WorldDsSaveReader
import com.tamerslab.gamesave.WorldDsStatConversion

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
    /**
     * The name stored on the outgoing record: the user's nickname when they set one, otherwise the
     * game's own species name. A canonical id (for example "digimon.garurumon") must never reach the
     * record, because the fixed 8-glyph name field would truncate it to "digimon.".
     */
    fun recordName(nickname: String?, gameSpeciesName: String): String =
        nickname?.trim()?.takeIf { it.isNotEmpty() } ?: gameSpeciesName

    /** True when the app state still equals the checkpoint captured at the last native sync. */
    fun matchesBaseline(state: WorldDsOutboundState, projection: NativeAppProjection): Boolean =
        projection.stage == state.stage && projection.baseHp == state.baseHp && projection.baseBp == state.baseBp &&
            projection.baseAp == state.baseAp && projection.trainingHp == state.appStats.trainingHp &&
            projection.trainingAp == state.appStats.trainingAp && projection.trainingBp == state.appStats.trainingBp &&
            projection.vitalPoints == state.appStats.vitalPoints && projection.wins == state.appStats.wins &&
            projection.losses == state.appStats.losses

    /**
     * Retained bytes are preferred only while they cannot take app progress away. A return may
     * deliberately keep the app's pre-evolution level when the native record changed form; the
     * retained level must then be raised on send instead of silently restoring the native level.
     */
    fun retainedCoversAppLevel(state: WorldDsOutboundState, retained: ObservedDigimon): Boolean =
        WorldDsStatConversion.levelForVitalPoints(state.appStats.vitalPoints) <= retained.level

    fun generate(
        state: WorldDsOutboundState,
        seedBase64: String?,
        defaults: WorldDsRecordDefaults? = null,
        lineage: List<Long> = emptyList(),
        nativeStats: WorldDsStatConversion.NativeStats? = null,
    ): ObservedDigimon {
        // A tracked story block is authoritative: it is written exactly, not re-derived from the app.
        val native = nativeStats ?: WorldDsStatConversion.toNative(state.appStats)
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