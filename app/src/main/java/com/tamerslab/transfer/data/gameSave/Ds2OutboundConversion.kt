package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.NativeAppProjection
import com.tamerslab.gamesave.Ds2RecordCodec
import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.WorldDsStatConversion

/** Everything the send path needs to project one app partner into a Dawn/Dusk record. */
internal data class Ds2OutboundState(
    val dsSpeciesId: Long,
    val canonicalSpeciesId: String,
    val displayName: String,
    val fallbackName: String,
    val appStats: WorldDsStatConversion.AppStats,
    val defaults: Ds2RecordCodec.Ds2RecordDefaults,
    val lineage: List<Long>,
)

/**
 * Dawn/Dusk record synthesis for partners that have no exact retained record (app-only partners,
 * or a stored partner whose app growth exceeds the archived record). The generated record mirrors
 * the engine's own creation path; a retained record is used as the seed so its unknown windows and
 * native identity survive.
 */
internal object Ds2OutboundConversion {
    fun recordName(nickname: String?, gameSpeciesName: String): String =
        nickname?.trim()?.takeIf { it.isNotEmpty() } ?: gameSpeciesName

    fun matchesBaseline(state: Ds2OutboundState, projection: NativeAppProjection): Boolean =
        projection.stage == state.appStats.stage && projection.baseHp == state.appStats.baseHp &&
            projection.baseBp == state.appStats.baseBp && projection.baseAp == state.appStats.baseAp &&
            projection.trainingHp == state.appStats.trainingHp && projection.trainingAp == state.appStats.trainingAp &&
            projection.trainingBp == state.appStats.trainingBp && projection.vitalPoints == state.appStats.vitalPoints &&
            projection.wins == state.appStats.wins && projection.losses == state.appStats.losses

    fun retainedCoversAppLevel(state: Ds2OutboundState, retained: ObservedDigimon): Boolean =
        Ds2StatConversion.levelForVitalPoints(state.appStats.vitalPoints) <= retained.level

    fun generate(state: Ds2OutboundState, seedBase64: String?,
        nativeStats: Ds2StatConversion.NativeProjection? = null,
        expCounters: List<Long> = emptyList()): ObservedDigimon {
        // A tracked story block is authoritative: no stat re-conversion when it exists.
        val native = nativeStats ?: Ds2StatConversion.toNative(state.appStats, state.defaults.maxLevel)
        val decoded = seedBase64?.let { runCatching { Ds2RecordCodec.decodeBase64(it) }.getOrNull() }
        val seed = decoded?.takeIf { it.size == Ds2RecordCodec.RECORD_BYTES }
        decoded?.takeIf { it.size != Ds2RecordCodec.RECORD_BYTES }?.fill(0)
        val seededLocalId = seed?.let { Ds2RecordCodec.localId(it) }?.takeIf { it in 1 until 0xFFFFFF00L }
        val generated = try {
            Ds2RecordCodec.generate(
                localId = seededLocalId ?: 0L,
                speciesId = state.dsSpeciesId,
                name = state.displayName,
                fallbackName = state.fallbackName,
                stats = Ds2RecordCodec.Ds2GeneratedStats(
                    level = native.level, hp = native.hp, maxHp = native.maxHp, mp = native.mp, maxMp = native.maxMp,
                    attack = native.attack, defense = native.defense, spirit = native.spirit, speed = native.speed,
                    maxLevel = native.maxLevel,
                ),
                seed = seed,
                defaults = state.defaults,
                lineage = state.lineage,
                exp = expCounters,
            )
        } finally {
            seed?.fill(0)
        }
        val storedName = decodedName(generated.bytes)
        val identity = seededLocalId?.let { "save-local:$it" }
        return ObservedDigimon(
            recordKey = if (identity == null) "generated" else "retained",
            container = SaveContainer.PARTY,
            position = 0,
            nativeSpeciesId = state.dsSpeciesId,
            storedName = storedName,
            level = native.level,
            personalityId = null,
            identityCandidate = 0,
            membership = RecordMembership.ACTIVE_RECORD,
            nativeRecordBase64 = Ds2RecordCodec.base64(generated.bytes),
            nativeIdentity = identity,
            nativeParameters = NativeStatParameters(native.hp, native.maxHp, native.mp, native.maxMp,
                native.attack, native.defense, native.spirit, native.speed, native.maxLevel,
                expCounters.ifEmpty { List(Ds2StatConversion.EXP_CHANNELS) { 0L } }, 1),
        )
    }

    /** The name actually stored in the fixed nine-glyph window (may be truncated by it). */
    private fun decodedName(bytes: ByteArray): String {
        val codes = (0 until Ds2RecordCodec.NAME_SLOTS).map { slot ->
            val at = Ds2RecordCodec.OFFSET_NAME + slot * 2
            (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)
        }.takeWhile { it != Ds2RecordCodec.NAME_TERMINATOR }
        return Ds2RecordCodec.decodeName(codes)
    }

    /** Species defaults in the shape the core codec consumes. */
    fun defaultsOf(table: Ds2SpeciesTable, speciesId: Long): Ds2RecordCodec.Ds2RecordDefaults? {
        val row = table.defaults(speciesId) ?: return null
        return Ds2RecordCodec.Ds2RecordDefaults(
            unk1A = row.unk1A,
            unk50 = row.unk50,
            abilities = row.abilities,
            growth = row.growth,
            skills = row.skills,
            resistances = row.resistances,
            expCurve = row.expCurve,
            baseLevel = row.baseLevel,
            maxLevel = row.maxLevel.coerceIn(1, 99),
            baseHp = row.baseHp,
            baseMp = row.baseMp,
            baseAttack = row.baseAttack,
            baseDefense = row.baseDefense,
            baseSpirit = row.baseSpirit,
            baseSpeed = row.baseSpeed,
            unk27 = row.unk27,
        )
    }
}
