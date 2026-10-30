package com.github.nacabaro.vbhelper.gamesave

import java.util.Base64

enum class NativeMeasure { LEVEL, TOTAL_EXP, TALENT, BOND, CURRENT_HP, MAX_HP, CURRENT_MP, MAX_MP,
    ATTACK, DEFENSE, SPIRIT, SPEED, APTITUDE, EXP_CHANNEL, FORM_HISTORY }
enum class NativeChangeKind { NO_CHANGE, PERSONAL_EDIT, LOCATION, FORM, IDENTITY, PERMANENT_PARAMETERS,
    TEMPORARY_STATE, UNKNOWN_NATIVE_DATA, NATIVE_DATA_UNAVAILABLE, UNSUPPORTED_PROFILE }
data class NativeMeasureChange(val measure: NativeMeasure, val before: String?, val after: String?,
    val delta: Long? = null, val channel: Int? = null)
data class NativeProgressReport(val version: Int, val kinds: Set<NativeChangeKind>, val changes: List<NativeMeasureChange>,
    val unknownNativeBytes: Boolean) {
    /** Native differences are not a validated cross-game conversion or an authorization to award app growth. */
    val canNormalize: Boolean get() = false
}

/** Read-only, versioned native difference review. Pending changes and semantic ledgers are never modified. */
object NativeProgressReview {
    const val VERSION = 1
    fun review(game: String, profile: String, codec: Int, before: ObservedDigimon, after: ObservedDigimon): NativeProgressReport {
        val dsProfile = NdsStoryProfiles.forTriple(game, profile, codec)
        val ds = dsProfile != null
        val ts = game == TimeStrangerSaveReader.GAME_ID && profile == TimeStrangerSaveReader.PROFILE
        if (codec != 1 || (!ds && !ts)) return NativeProgressReport(VERSION, setOf(NativeChangeKind.UNSUPPORTED_PROFILE), emptyList(), true)
        val changes = mutableListOf<NativeMeasureChange>()
        fun number(measure: NativeMeasure, a: Number?, b: Number?, channel: Int? = null) {
            if (a != b) changes += NativeMeasureChange(measure, a?.toString(), b?.toString(),
                if (a != null && b != null && a !is Float && b !is Float) b.toLong() - a.toLong() else null, channel)
        }
        number(NativeMeasure.LEVEL, before.level, after.level)
        number(NativeMeasure.TOTAL_EXP, before.totalExp, after.totalExp)
        number(NativeMeasure.TALENT, before.talentRaw, after.talentRaw)
        number(NativeMeasure.BOND, before.bondRaw, after.bondRaw)
        val a = before.nativeParameters
        val b = after.nativeParameters
        if (a != null || b != null) {
            number(NativeMeasure.CURRENT_HP, a?.hp, b?.hp); number(NativeMeasure.MAX_HP, a?.maxHp, b?.maxHp)
            number(NativeMeasure.CURRENT_MP, a?.mp, b?.mp); number(NativeMeasure.MAX_MP, a?.maxMp, b?.maxMp)
            number(NativeMeasure.ATTACK, a?.attack, b?.attack); number(NativeMeasure.DEFENSE, a?.defense, b?.defense)
            number(NativeMeasure.SPIRIT, a?.spirit, b?.spirit); number(NativeMeasure.SPEED, a?.speed, b?.speed)
            number(NativeMeasure.APTITUDE, a?.aptitude, b?.aptitude)
            for (index in 0 until maxOf(a?.expCounters?.size ?: 0, b?.expCounters?.size ?: 0))
                number(NativeMeasure.EXP_CHANNEL, a?.expCounters?.getOrNull(index), b?.expCounters?.getOrNull(index), index + 1)
        }
        if (before.previousSpeciesIds != after.previousSpeciesIds) changes += NativeMeasureChange(NativeMeasure.FORM_HISTORY,
            before.previousSpeciesIds.joinToString(" / "), after.previousSpeciesIds.joinToString(" / "))
        val layout = dsProfile?.record
        val rawBefore = decode(before.nativeRecordBase64, layout?.bytes ?: 336)
        val rawAfter = decode(after.nativeRecordBase64, layout?.bytes ?: 336)
        try {
            val nameRange = if (layout != null) layout.offsetName until (layout.offsetName + layout.nameSlots * 2) else 4..0x23
            val known = if (layout != null) listOf(
                0..7,
                nameRange,
                layout.offsetHp..(layout.offsetSpeed + 1),
                layout.offsetMaxLevel..(layout.offsetMaxLevel + 1),
                layout.offsetLevel..(layout.offsetLevel + 1),
                layout.offsetExp until (layout.offsetExp + layout.expChannels * 4),
            ) else listOf(0..3, nameRange, 0x64..0x6B, 0xF2..0xF2, 0x104..0x11F, 0x140..0x143, 0x14C..0x14F)
            val unavailable = rawBefore == null || rawAfter == null
            val rawNameChanged = !unavailable && nameRange.any { rawBefore!![it] != rawAfter!![it] }
            val outsideKnown = !unavailable && rawBefore!!.indices.any { rawBefore[it] != rawAfter!![it] && known.none { range -> it in range } }
            val unexplainedKnown = !unavailable && !rawBefore!!.contentEquals(rawAfter!!) && changes.isEmpty() && !rawNameChanged &&
                before.nativeSpeciesId == after.nativeSpeciesId && before.personalityId == after.personalityId &&
                before.identityEvidenceKey() == after.identityEvidenceKey()
            val unknown = outsideKnown || unexplainedKnown
            val kinds = buildSet {
                if (before.storedName != after.storedName || before.personalityId != after.personalityId || rawNameChanged) add(NativeChangeKind.PERSONAL_EDIT)
                if (before.container != after.container || before.position != after.position || before.recordKey != after.recordKey ||
                    a?.saveBlock != b?.saveBlock) add(NativeChangeKind.LOCATION)
                if (before.nativeSpeciesId != after.nativeSpeciesId) add(NativeChangeKind.FORM)
                if (before.identityEvidenceKey() != after.identityEvidenceKey()) add(NativeChangeKind.IDENTITY)
                if (changes.any { it.measure !in setOf(NativeMeasure.CURRENT_HP, NativeMeasure.CURRENT_MP, NativeMeasure.FORM_HISTORY) }) add(NativeChangeKind.PERMANENT_PARAMETERS)
                if (changes.any { it.measure in setOf(NativeMeasure.CURRENT_HP, NativeMeasure.CURRENT_MP) }) add(NativeChangeKind.TEMPORARY_STATE)
                if (changes.any { it.measure == NativeMeasure.FORM_HISTORY }) add(NativeChangeKind.FORM)
                if (unavailable) add(NativeChangeKind.NATIVE_DATA_UNAVAILABLE)
                if (unknown) add(NativeChangeKind.UNKNOWN_NATIVE_DATA)
                if (isEmpty()) add(NativeChangeKind.NO_CHANGE)
            }
            return NativeProgressReport(VERSION, kinds, changes.toList(), unknown)
        } finally { rawBefore?.fill(0); rawAfter?.fill(0) }
    }
    private fun decode(encoded: String?, expected: Int): ByteArray? {
        if (encoded == null || encoded.length > 1024) return null
        val bytes = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: return null
        if (bytes.size == expected) return bytes
        bytes.fill(0)
        return null
    }
}
