package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.NativeAppProjection
import com.tamerslab.gamesave.LostEvolution
import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.WorldDsStatConversion

/** Everything the send path needs to project one app partner into a Lost Evolution record. */
internal data class LeOutboundState(
    val dsSpeciesId: Long,
    val canonicalSpeciesId: String,
    val displayName: String,
    val fallbackName: String,
    val appStats: WorldDsStatConversion.AppStats,
    val defaults: LeSpeciesDefaults?,
    /** Aptitude byte the record carries (100 in the game's own records). */
    val aptitude: Int = 100,
)

/**
 * Lost Evolution record synthesis for partners that have no exact retained record (app-only
 * partners, or a stored partner whose app growth exceeds the archived record). The generated record
 * mirrors the engine's creation path; a retained record is used as the seed so its identity and
 * unknown windows survive.
 */
internal object LeOutboundConversion {
    fun recordName(nickname: String?, gameSpeciesName: String): String =
        nickname?.trim()?.takeIf { it.isNotEmpty() } ?: gameSpeciesName

    fun matchesBaseline(state: LeOutboundState, projection: NativeAppProjection): Boolean =
        projection.stage == state.appStats.stage && projection.baseHp == state.appStats.baseHp &&
            projection.baseBp == state.appStats.baseBp && projection.baseAp == state.appStats.baseAp &&
            projection.trainingHp == state.appStats.trainingHp && projection.trainingAp == state.appStats.trainingAp &&
            projection.trainingBp == state.appStats.trainingBp && projection.vitalPoints == state.appStats.vitalPoints &&
            projection.wins == state.appStats.wins && projection.losses == state.appStats.losses

    fun retainedCoversAppLevel(state: LeOutboundState, retained: ObservedDigimon): Boolean =
        LeStatConversion.levelForVitalPoints(state.appStats.vitalPoints) <= retained.level

    fun generate(state: LeOutboundState, seedBase64: String?,
        nativeStats: LeStatConversion.NativeProjection? = null,
        expTotal: Long = 0L): ObservedDigimon {
        // A tracked story block is authoritative: no stat re-conversion when it exists.
        val native = nativeStats ?: LeStatConversion.toNative(state.appStats, state.aptitude)
        val decoded = seedBase64?.let { runCatching { LostEvolution.base64Decode(it) }.getOrNull() }
        val seed = decoded?.takeIf { it.size == LostEvolution.RECORD_BYTES }
        decoded?.takeIf { it.size != LostEvolution.RECORD_BYTES }?.fill(0)
        val seededIdentity = seed?.let(LostEvolution::identity)?.takeIf { it != 0L }
        val record = LostEvolution.buildRecord(
            speciesId = state.dsSpeciesId,
            displayName = state.displayName,
            fallbackName = state.fallbackName,
            level = native.level,
            hp = native.hp,
            maxHp = native.maxHp,
            mp = native.mp,
            maxMp = native.maxMp,
            attack = native.attack,
            defense = native.defense,
            spirit = native.spirit,
            speed = native.speed,
            aptitude = native.aptitude,
            skills = state.defaults?.skills.orEmpty(),
            exp = expTotal,
            seed = seed,
        )
        seed?.fill(0)
        val storedName = LostEvolution.decodeName(record)
        val identity = seededIdentity?.let(LostEvolution::identityText)
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
            nativeRecordBase64 = LostEvolution.base64Encode(record),
            nativeIdentity = identity,
            nativeParameters = NativeStatParameters(native.hp, native.maxHp, native.mp, native.maxMp,
                native.attack, native.defense, native.spirit, native.speed, native.aptitude,
                listOf(expTotal.coerceIn(0L, 0xFFFFFFFFL)), 1),
        )
    }
}
