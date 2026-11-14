package com.tamerslab.gamesave

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Section 11 rung-2 candidate construction for the Time Stranger adapter.
 *
 * The only validated edit is a stored-name change on one active party/bank record. The
 * transformation mirrors the game's own controlled rename evidence: overwrite from the name
 * offset with the new UTF-8 bytes plus one terminator and leave the rest of the window
 * untouched. The game itself keeps stale tail residue from previously longer names (the
 * Before/After sample renames "Gritaria" to "Kuzuhamon" but leaves the old "a" behind), so
 * zero-filling would not reproduce native output. The writer re-decodes its own candidate and
 * requires every byte outside the window to match the source and the re-read name to be the
 * new one, so a mistake cannot silently produce a corrupt document. It still writes no files
 * by itself; callers retain the section 11 journal, backup and readback duties.
 */
object TimeStrangerEdits {
    const val NAME_WINDOW_BYTES = 32

    /** Personality byte offset from the record name offset, read by TimeStrangerSaveReader. */
    const val PERSONALITY_OFFSET = 0xEE
    private const val MAX_NAME_BYTES = NAME_WINDOW_BYTES - 1

    fun renameIndividual(sourceBytes: ByteArray, recordKey: String, newNameBytes: ByteArray): ByteArray {
        require(sourceBytes.size == TimeStrangerSaveReader.FILE_BYTES) { "Not a Time Stranger document" }
    require(newNameBytes.isNotEmpty() && newNameBytes.size <= MAX_NAME_BYTES) {
        "Name must encode to 1..$MAX_NAME_BYTES bytes"
    }
    require(newNameBytes.none { it == 0.toByte() }) { "Name bytes must not contain terminators" }
        val name = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(newNameBytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) { null }
        requireNotNull(name) { "Name bytes are not valid UTF-8" }
        require(name.none { it.isISOControl() }) { "Name must not contain control characters" }

        val observation = TimeStrangerSaveReader().read(sourceBytes)
        val record = observation.records.singleOrNull { it.recordKey == recordKey }
            ?: throw IllegalArgumentException("No record $recordKey in this document")
        require(record.container != SaveContainer.FARM) { "Farm records are outside transfer scope" }
        require(record.membership == RecordMembership.ACTIVE_RECORD) { "Only active party/bank records are editable" }

        val decoded = TimeStrangerContainer.decode(sourceBytes)
        val plain = decoded.copyOf()
        try {
            val offset = recordKey.substringAfter(':').toInt()
            val nameOffset = offset + 4
            val windowEnd = nameOffset + NAME_WINDOW_BYTES
            require(nameOffset >= 0 && windowEnd <= plain.size) { "Record window is out of range" }
            newNameBytes.copyInto(plain, nameOffset)
            plain[nameOffset + newNameBytes.size] = 0

            val candidate = TimeStrangerContainer.encode(plain)
            verifyCandidate(sourceBytes, candidate, nameOffset, windowEnd, name, recordKey)
            plain.fill(0)
            return candidate
        } finally {
            decoded.fill(0)
            plain.fill(0)
        }
    }

    /**
     * Writes one stored personality id (1..16) on one active party/bank record.
     *
     * Mirrors the rename safety contract: only the personality byte at `nameOffset + 0xEE`
     * changes, the candidate is re-encoded with the game's own container codec and re-read,
     * and every other byte of the document must be identical to the source. Farm records and
     * inactive/needs-review records are refused because only active party/bank records were
     * validated for edits. The caller retains the journal, backup and readback duties; this
     * method writes no files.
     */
    fun setPersonality(sourceBytes: ByteArray, recordKey: String, nativePersonalityId: Int): ByteArray {
        require(sourceBytes.size == TimeStrangerSaveReader.FILE_BYTES) { "Not a Time Stranger document" }
        require(nativePersonalityId in 1..16) { "Personality id must be 1..16" }
        val observation = TimeStrangerSaveReader().read(sourceBytes)
        val record = observation.records.singleOrNull { it.recordKey == recordKey }
            ?: throw IllegalArgumentException("No record $recordKey in this document")
        require(record.container != SaveContainer.FARM) { "Farm records are outside transfer scope" }
        require(record.membership == RecordMembership.ACTIVE_RECORD) { "Only active party/bank records are editable" }
        require(record.personalityId != nativePersonalityId) {
            "Record $recordKey already stores personality $nativePersonalityId"
        }

        val decoded = TimeStrangerContainer.decode(sourceBytes)
        val plain = decoded.copyOf()
        try {
            val offset = recordKey.substringAfter(':').toInt()
            val personalityOffset = offset + 4 + PERSONALITY_OFFSET
            require(personalityOffset in plain.indices) { "Personality byte is out of range" }
            require((plain[personalityOffset].toInt() and 255) == record.personalityId) {
                "Personality byte changed since the record was read"
            }
            plain[personalityOffset] = nativePersonalityId.toByte()

            val candidate = TimeStrangerContainer.encode(plain)
            verifyPersonalityEdit(sourceBytes, candidate, personalityOffset, nativePersonalityId, record)
            plain.fill(0)
            return candidate
        } finally {
            decoded.fill(0)
            plain.fill(0)
        }
    }

    /** The candidate must differ only at the personality byte and re-read with the new value. */
    private fun verifyPersonalityEdit(
        sourceBytes: ByteArray,
        candidate: ByteArray,
        personalityOffset: Int,
        expectedPersonalityId: Int,
        sourceRecord: ObservedDigimon
    ) {
        val sourceDecoded = TimeStrangerContainer.decode(sourceBytes)
        val candidateDecoded = TimeStrangerContainer.decode(candidate)
        try {
            for (index in candidateDecoded.indices) {
                if (index == personalityOffset) continue
                check(candidateDecoded[index] == sourceDecoded[index]) {
                    "Candidate changed a byte outside the personality field at 0x${index.toString(16)}"
                }
            }
        } finally {
            sourceDecoded.fill(0)
            candidateDecoded.fill(0)
        }

        val reread = TimeStrangerSaveReader().read(candidate)
        val updated = reread.records.singleOrNull { it.recordKey == sourceRecord.recordKey }
            ?: throw IllegalStateException("Candidate lost record ${sourceRecord.recordKey}")
        check(updated.personalityId == expectedPersonalityId) {
            "Candidate re-read personality ${updated.personalityId}, expected $expectedPersonalityId"
        }
        check(updated.membership == RecordMembership.ACTIVE_RECORD) {
            "Candidate re-read is not an active record"
        }
        check(updated.container == sourceRecord.container) { "Candidate re-read changed container" }
        check(updated.storedName == sourceRecord.storedName &&
            updated.nativeSpeciesId == sourceRecord.nativeSpeciesId &&
            updated.level == sourceRecord.level &&
            updated.identityCandidate == sourceRecord.identityCandidate) {
            "Candidate re-read changed record fields"
        }
    }

    /**
     * Experimental single-flag reactivation of one inactive bank-tail record.
     *
     * Corpus evidence shows released records stay fully intact in a contiguous stale tail behind
     * the active prefix (species, level, name, identity and chain fields all preserved; the
     * roster index simply continues the bank numbering) and the stored flag is the only known
     * ownership bit. This writer flips exactly that flag for the first inactive bank record and
     * nothing else — reviving it makes the active prefix contiguous again, exactly the shape the
     * game itself writes, while a mid-tail flip would leave an inactive hole inside the prefix
     * and is therefore refused. Unlike the rename path this semantics is a hypothesis, not yet
     * observed game output, so the caller must treat the candidate as an experiment: the record
     * flags, chain and index fields all line up as if the record had never been released, but
     * only in-game acceptance can confirm the game reads it back as owned. Capability flags
     * remain false; the writer itself still writes no files.
     */
    fun activateInactiveIndividual(sourceBytes: ByteArray, recordKey: String): ByteArray {
        require(sourceBytes.size == TimeStrangerSaveReader.FILE_BYTES) { "Not a Time Stranger document" }
        val observation = TimeStrangerSaveReader().read(sourceBytes)
        val record = observation.records.singleOrNull { it.recordKey == recordKey }
            ?: throw IllegalArgumentException("No record $recordKey in this document")
        require(record.container == SaveContainer.BANK && record.issues == listOf(RecordIssue.INACTIVE_BOUNDARY)) {
            "Only the first inactive bank record with an intact identity can be reactivated"
        }

        val decoded = TimeStrangerContainer.decode(sourceBytes)
        val plain = decoded.copyOf()
        try {
            val offset = recordKey.substringAfter(':').toInt()
            val flagOffset = offset + 4 + 0x140
            require(flagOffset + 4 <= plain.size) { "Record flag is out of range" }
            require(u32(plain, flagOffset) == 0L) { "Record is not stored as inactive" }
            plain[flagOffset] = 1

            val candidate = TimeStrangerContainer.encode(plain)
            verifyActivation(sourceBytes, candidate, record, flagOffset)
            return candidate
        } finally {
            decoded.fill(0)
            plain.fill(0)
        }
    }

    /** The candidate must differ only in the flag word, and must re-read as the same active record. */
    private fun verifyActivation(sourceBytes: ByteArray, candidate: ByteArray, sourceRecord: ObservedDigimon, flagOffset: Int) {
        val sourceDecoded = TimeStrangerContainer.decode(sourceBytes)
        val candidateDecoded = TimeStrangerContainer.decode(candidate)
        try {
            for (index in candidateDecoded.indices) {
                if (index in flagOffset until flagOffset + 4) continue
                check(candidateDecoded[index] == sourceDecoded[index]) {
                    "Candidate changed a byte outside the flag word at 0x${index.toString(16)}"
                }
            }
        } finally {
            sourceDecoded.fill(0)
            candidateDecoded.fill(0)
        }
        val reread = TimeStrangerSaveReader().read(candidate)
        val updated = reread.records.singleOrNull { it.recordKey == sourceRecord.recordKey }
            ?: throw IllegalStateException("Candidate lost record ${sourceRecord.recordKey}")
        check(updated.membership == RecordMembership.ACTIVE_RECORD && updated.issues.isEmpty()) {
            "Candidate re-read is not an active bank record"
        }
        check(updated.storedName == sourceRecord.storedName && updated.nativeSpeciesId == sourceRecord.nativeSpeciesId &&
            updated.identityCandidate == sourceRecord.identityCandidate && updated.level == sourceRecord.level) {
            "Candidate re-read changed stored fields"
        }
    }

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[offset + index].toLong() and 255) shl (index * 8)) }

    /** The candidate must re-read cleanly, show the new name and differ only inside the window. */
    private fun verifyCandidate(sourceBytes: ByteArray, candidate: ByteArray, nameOffset: Int, windowEnd: Int,
        expectedName: String, recordKey: String) {
        val sourceDecoded = TimeStrangerContainer.decode(sourceBytes)
        val candidateDecoded = TimeStrangerContainer.decode(candidate)
        try {
            for (index in candidateDecoded.indices) {
                if (index in nameOffset until windowEnd) continue
                check(candidateDecoded[index] == sourceDecoded[index]) {
                    "Candidate changed a byte outside the name window at 0x${index.toString(16)}"
                }
            }
        } finally {
            sourceDecoded.fill(0)
            candidateDecoded.fill(0)
        }
        val reread = TimeStrangerSaveReader().read(candidate)
        val updated = reread.records.singleOrNull { it.recordKey == recordKey }
            ?: throw IllegalStateException("Candidate lost record $recordKey")
        check(updated.storedName == expectedName) { "Candidate re-read does not show the new name" }
    }
}
