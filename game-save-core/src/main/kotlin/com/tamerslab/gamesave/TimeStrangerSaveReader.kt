package com.tamerslab.gamesave

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Deterministic container codec for the Time Stranger document. AES-ECB with no padding is
 * symmetric, so the encode direction is the section 11 rung-1 rehearsal path: a correct
 * decode must re-encode byte-identically. It writes no files and edits no fields.
 */
internal object TimeStrangerContainer {
    // Public file-format constant, not an account credential or application secret.
    private val key = SecretKeySpec("3962776754555883".toByteArray(Charsets.US_ASCII), "AES")
    private fun cipher(mode: Int) = Cipher.getInstance("AES/ECB/NoPadding").apply { init(mode, key) }
    fun decode(bytes: ByteArray): ByteArray = cipher(Cipher.DECRYPT_MODE).doFinal(bytes)
    fun encode(plain: ByteArray): ByteArray = cipher(Cipher.ENCRYPT_MODE).doFinal(plain)
}

/** Independently implemented, read-only layout reader. See FORMAT_RESEARCH.md for evidence and limits. */
class TimeStrangerSaveReader : GameSaveReader {
    override val gameId = GAME_ID
    override val maxInputBytes = FILE_BYTES

    override fun read(bytes: ByteArray): SaveObservation {
        if (bytes.size != FILE_BYTES) throw SaveReadException(SaveReadFailure.UNSUPPORTED_FORMAT)
        val plain = TimeStrangerContainer.decode(bytes)
        try {
            val header = plain.copyOfRange(0, 128).toString(Charsets.UTF_8)
            if (!Regex("^[0-9]{1,3},\\s*3097152,").containsMatchIn(header)) {
                throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
            }
            val mainBase = alignment(plain, 0x1000, 0x53000, 0x150, requireActive = true)
                ?: throw SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT)
            val result = mutableListOf<ObservedDigimon>()
            var boundary = false
            var index = 0
            for (offset in mainBase..(0x53000 - 0x150) step 0x150) {
                val currentIndex = index++
                if (currentIndex < 2) continue // Known sentinel/header positions, not the six party positions.
                val flag = u32(plain, offset + 4 + 0x140)
                val pastBoundary = boundary
                if (currentIndex >= 8 && flag != 1L) boundary = true
                val entry = record(plain, offset, farm = false) ?: continue
                val container = if (currentIndex < 8) SaveContainer.PARTY else SaveContainer.BANK
                val issues = mutableListOf<RecordIssue>()
                if (currentIndex >= 8) {
                    if (flag != 1L) {
                        issues += if (pastBoundary) RecordIssue.INACTIVE_TAIL else RecordIssue.INACTIVE_BOUNDARY
                    } else if (pastBoundary) issues += RecordIssue.INACTIVE_TAIL
                } else if (flag != 1L) issues += RecordIssue.INACTIVE_BOUNDARY
                if (entry.identityCandidate == 0L) issues += RecordIssue.MISSING_IDENTITY
                result += entry.copy(container = container, position = if (currentIndex < 8) currentIndex - 1 else currentIndex - 7,
                    membership = if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
                    issues = issues)
            }
            val farmBase = alignment(plain, 0x53000, 0x5C000, 0x158, requireActive = false)
            if (farmBase != null) {
                for ((position, offset) in (farmBase..(0x5C000 - 0x158) step 0x158).withIndex()) {
                    val entry = record(plain, offset, farm = true) ?: continue
                    result += entry.copy(container = SaveContainer.FARM, position = position + 1,
                        membership = RecordMembership.NEEDS_REVIEW,
                        issues = listOf(RecordIssue.FARM_OWNERSHIP_UNVERIFIED) +
                            if (entry.identityCandidate == 0L) listOf(RecordIssue.MISSING_IDENTITY) else emptyList())
                }
            }
            // A hash is only evidence, not a binding. Retain duplicates for review instead of merging them.
            val duplicates = result.filter { it.membership == RecordMembership.ACTIVE_RECORD && it.identityCandidate != 0L }
                .groupingBy { it.identityCandidate }.eachCount().filterValues { it > 1 }.keys
            val records = result.map { if (it.identityCandidate in duplicates && it.membership == RecordMembership.ACTIVE_RECORD)
                it.copy(membership = RecordMembership.NEEDS_REVIEW, issues = it.issues + RecordIssue.DUPLICATE_IDENTITY) else it }
            return SaveObservation(GAME_ID, PROFILE, 1, revision(bytes), records,
                listOf(ObservationNotice.SPECIES_MAPPING_PENDING, ObservationNotice.FARM_RESEARCH_PENDING,
                    ObservationNotice.IDENTITY_RESEARCH_PENDING),
                SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))
        } finally { plain.fill(0) }
    }

    /** Score the strided layout rather than accepting the first name-like bytes in a save. */
    private fun alignment(data: ByteArray, start: Int, end: Int, stride: Int, requireActive: Boolean): Int? {
        val scored = (0 until stride step 4).map { phase ->
            var score = 0
            for (offset in start + phase until minOf(end - stride, start + stride * 64) step stride) {
                if (record(data, offset, farm = stride == 0x158) != null &&
                    (!requireActive || u32(data, offset + 4 + 0x140) == 1L)) score++
            }
            start + phase to score
        }
        val best = scored.maxOf { it.second }
        return scored.singleOrNull { it.second == best && best > 0 }?.first
    }

    private fun record(data: ByteArray, offset: Int, farm: Boolean): ObservedDigimon? {
        val nameOffset = offset + 4
        val end = nameOffset + if (farm) 0x154 else 0x14C
        if (offset < 0 || end > data.size) return null
        val species = u32(data, offset)
        if (species !in 1L..10000L || u32(data, nameOffset + 0x104) != species) return null
        val level = u32(data, nameOffset + 0x60)
        val personality = data[nameOffset + 0xEE].toInt() and 255
        if (level !in 1L..99L || personality !in 1..16) return null
        val nameEnd = (nameOffset until nameOffset + 32).firstOrNull { data[it] == 0.toByte() } ?: return null
        val name = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(data, nameOffset, nameEnd - nameOffset)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { return null }
        if (name.any { it.isISOControl() }) return null
        val bond = Float.fromBits(u32(data, nameOffset + 0x13C).toInt())
        if (!bond.isFinite()) return null
        return ObservedDigimon(recordKey = "${if (farm) "farm" else "main"}:$offset", container = SaveContainer.BANK,
            position = 0, nativeSpeciesId = species, storedName = name, level = level.toInt(), personalityId = personality,
            identityCandidate = u32(data, nameOffset + if (farm) 0x150 else 0x148),
            membership = RecordMembership.NEEDS_REVIEW, totalExp = u32(data, nameOffset + 0x64),
            talentRaw = u32(data, nameOffset + 0x100), bondRaw = bond,
            previousSpeciesIds = (0 until 5).map { u32(data, nameOffset + 0x108 + it * 4) }.filter { it in 1L..10000L },
            nativeRecordBase64 = data.copyOfRange(offset, end).let { native ->
                try { Base64.getEncoder().encodeToString(native) } finally { native.fill(0) }
            })
    }

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 255) shl (index * 8)) }

    companion object {
        const val GAME_ID = "digimon_story_time_stranger_pc"
        const val FILE_BYTES = 3_098_176
        const val PROFILE = "time-stranger-pc-strided-v1"
    }
}
