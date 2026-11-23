package com.tamerslab.gamesave

import java.util.Base64

/**
 * Byte-level Digimon World Dawn/Dusk edits over the verified 0x164-byte record layout.
 *
 * Every operation is revision-bound: it re-reads the exact source bytes, works on the copy the
 * game itself would load (the unique newest valid copy), and returns a candidate whose unrelated
 * bytes are verified unchanged. The writer layer still owns backups, journaling and file writes.
 *
 * Layout (from the shared ARM9 engine code, cross-checked on two retail saves):
 *  - two card copies of 0x7B2C bytes at 0x0 and 0x7B80; MD5 header over the copy with the first
 *    sixteen bytes zeroed; u32 write sequence at copy+0x18 (higher wins)
 *  - 40 individual records at 0x288 stride 0x164, empty when the id word is 0
 *  - the ten visible party references at 0x260 (the add path uses slots 0..7)
 *  - last id handed out at 0x25C
 */
object Ds2SaveEdits {
    private val PROFILE get() = NdsStoryProfiles.DAWN_DUSK
    private const val SAVE_BYTES = 0x7B2C
    private const val RECORDS = 0x288
    private const val STRIDE = 0x164
    private const val RECORD_COUNT = 40
    private const val PARTY = 0x260
    private const val PARTY_SLOTS = 10
    private const val LAST_ID = 0x25C
    private const val SEQUENCE = 0x18
    private const val HEADER = 16

    /** Mirrors the game's own free-party-slot rule (ARM9 FUN_02032f74). */
    private fun freePartySlot(bytes: ByteArray, copyBase: Int): Int? {
        fun party(slot: Int) = u32(bytes, copyBase + PARTY + slot * 4)
        var occupied = 0
        for (slot in 0 until 5) if (party(slot) != 0L) occupied++
        return if (occupied < 3) {
            (0 until 5).firstOrNull { party(it) == 0L }
        } else {
            (5 until 8).firstOrNull { party(it) == 0L }
        }
    }

    private fun freeRecordSlot(bytes: ByteArray, copyBase: Int): Int? =
        (0 until RECORD_COUNT).firstOrNull { u32(bytes, copyBase + RECORDS + it * STRIDE) == 0L }

    /** The copy the game would load: the unique newest copy whose integrity header matches. */
    private fun authority(observation: SaveObservation): Int {
        val copy = NdsStoryCopySelection.authoritativeCopy(observation)
            ?: NdsStoryCopySelection.latestCopy(observation)
            ?: observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().singleOrNull()
            ?: throw IllegalArgumentException("The save has no defensible authoritative copy")
        require(observation.copyIntegrity[copy] != false) {
            "The authoritative copy does not match its integrity header; repair it in-game first"
        }
        return copy
    }

    private fun copyBase(observation: SaveObservation, copy: Int) =
        observation.imageBase + (copy - 1) * PROFILE.copyStride

    private fun populatedCopies(observation: SaveObservation): List<Int> =
        observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().sorted()

    /**
     * The game alternates between two card copies but a save load can fall back to the copy that
     * still holds the previous state (observed: a move-out that only edited the newest copy was
     * ignored by the retail loader). Every logical edit is therefore applied to all populated copies
     * and each copy gets its own bumped sequence and MD5, exactly like the game's own slot writes.
     */
    private fun finalizeCopies(candidate: ByteArray, observation: SaveObservation, copies: List<Int>) {
        copies.forEach { finalize(candidate, observation, it) }
    }

    private fun finalize(candidate: ByteArray, observation: SaveObservation, copy: Int) {
        val base = copyBase(observation, copy)
        val sequence = u32(candidate, base + SEQUENCE) + 1
        put32(candidate, base + SEQUENCE, sequence)
        NdsStoryCopyIntegrity.seal(candidate, base, PROFILE)
    }

    private fun newCandidate(source: ByteArray): ByteArray = source.copyOf()

    /** A planned candidate plus the exact source it was derived from, so undo can be byte-exact. */
    class Edit internal constructor(
        override val sourceRevision: String,
        override val recordKey: String,
        candidate: ByteArray,
        private val original: ByteArray,
        override val nativeIdentity: String? = null,
    ) : NdsStoryPendingEdit {
        private val planned = candidate.copyOf()
        override val candidateRevision: String get() = revision(planned)
        override val candidateBytes: ByteArray get() = planned.copyOf()

        fun restoreInto(candidate: ByteArray): ByteArray {
            require(candidate.contentEquals(planned)) { "Candidate no longer matches the planned revision" }
            return original.copyOf()
        }
    }

    /**
     * Removes one party member: clears its party reference and empties the record slot, exactly the
     * shape the game itself leaves behind (id 0, species -1). Refuses the last usable party member.
     */
    fun removeForResearch(source: ByteArray, observedRevision: String, recordKey: String): Edit {
        require(revision(source) == observedRevision) { "Source changed; read it again before planning a move" }
        val observation = WorldDs2SaveReader().read(source)
        val record = requireNotNull(observation.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        require(record.container == SaveContainer.PARTY) {
            "Only party members can move out; DigiFarm and other records stay inspection-only"
        }
        require(record.issues.none { it != RecordIssue.AMBIGUOUS_SAVE_BLOCK && it != RecordIssue.UNKNOWN_NAME_CODES }) {
            "Records that need review cannot move"
        }
        val id = requireNotNull(record.nativeIdentity).removePrefix("save-local:").toLongOrNull()
        requireNotNull(id) { "The record has no native identity" }
        val copies = populatedCopies(observation)
        val copy = authority(observation)
        val authorityBase = copyBase(observation, copy)
        val partyIds = (0 until PARTY_SLOTS).map { u32(source, authorityBase + PARTY + it * 4) }.filter { it != 0L }
        require(partyIds.count { it != id } >= 1) { "The last usable party member cannot move" }
        require((0 until RECORD_COUNT).any { u32(source, authorityBase + RECORDS + it * STRIDE) == id }) {
            "The record is not in the authoritative copy"
        }

        val candidate = newCandidate(source)
        copies.forEach { index ->
            val base = copyBase(observation, index)
            for (slot in 0 until PARTY_SLOTS) {
                if (u32(candidate, base + PARTY + slot * 4) == id) put32(candidate, base + PARTY + slot * 4, 0)
            }
            val recordSlot = (0 until RECORD_COUNT).firstOrNull {
                u32(candidate, base + RECORDS + it * STRIDE) == id
            }
            if (recordSlot != null) {
                candidate.fill(0, base + RECORDS + recordSlot * STRIDE,
                    base + RECORDS + (recordSlot + 1) * STRIDE)
                put32(candidate, base + RECORDS + recordSlot * STRIDE + 4, 0xFFFFFFFFL)
            }
        }
        finalizeCopies(candidate, observation, copies)

        val after = WorldDs2SaveReader().read(candidate)
        copies.forEach { index ->
            val remaining = observation.records.filter {
                it.nativeParameters?.saveBlock == index && it.nativeIdentity != record.nativeIdentity
            }
            val afterCopy = after.records.filter { it.nativeParameters?.saveBlock == index }
            check(afterCopy.none { it.nativeIdentity == record.nativeIdentity }) {
                "Removal left the individual in a save copy"
            }
            check(afterCopy.withoutCopySequence() == remaining.withoutCopySequence()) {
                "Removal changed an unrelated native individual"
            }
        }
        val windows = copies.flatMap { index ->
            val base = copyBase(observation, index)
            listOf(
                base until base + HEADER,
                base + SEQUENCE until base + SEQUENCE + 4,
                base + LAST_ID until base + PARTY,
                base + PARTY until base + RECORDS,
                base + RECORDS until base + RECORDS + RECORD_COUNT * STRIDE,
            )
        }
        check(source.indices.all { offset -> windows.any { offset in it } || source[offset] == candidate[offset] }) {
            "Removal changed bytes outside its own windows"
        }
        return Edit(observedRevision, recordKey, candidate, source, record.nativeIdentity)
    }

    /**
     * Inserts an exact retained record into a free slot and gives it a party position, mirroring
     * the game's add path (id from the record, last-id high-water mark, first free party slot).
     */
    fun insertRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer? = null): Edit {
        val native = WorldDsRecordCodec.decodeBase64(recordBase64)
        require(native.size == STRIDE) { "A retained record is exactly 0x164 bytes" }
        validateRetained(native)
        return insertNative(destination, observedRevision, native, container)
    }

    /**
     * Inserts a synthesized record. A record with no identity yet (the engine's own creation shape)
     * receives the next id from the destination's `lastId` high-water mark, exactly as the game's
     * add path does; a seeded record keeps its identity and goes through the same insertion.
     */
    fun insertGeneratedRecord(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer? = null): Edit {
        require(revision(destination) == observedRevision) { "Destination changed; read it again" }
        val native = Ds2RecordCodec.decodeBase64(recordBase64)
        require(native.size == STRIDE) { "A generated record is exactly 0x164 bytes" }
        if (u32(native, 0) == 0L) {
            val observation = WorldDs2SaveReader().read(destination)
            val base = copyBase(observation, authority(observation))
            val live = (0 until RECORD_COUNT).map { u32(destination, base + RECORDS + it * STRIDE) }
                .filter { it != 0L }
            val next = maxOf(u32(destination, base + LAST_ID), live.maxOrNull() ?: 0L) + 1
            put32(native, 0, next)
        }
        validateRetained(native)
        return insertNative(destination, observedRevision, native, container)
    }

    private fun insertNative(destination: ByteArray, observedRevision: String, native: ByteArray,
        container: SaveContainer?): Edit {
        require(revision(destination) == observedRevision) { "Destination changed; read it again" }
        val id = u32(native, 0)
        val observation = WorldDs2SaveReader().read(destination)
        val copies = populatedCopies(observation)
        require(copies.isNotEmpty()) { "The destination has no populated save copy" }
        require(container != SaveContainer.FARM) {
            "The Dawn/Dusk save layout has no farm table; choose party or bank"
        }
        val wantsParty = container == null || container == SaveContainer.PARTY
        val candidate = newCandidate(destination)
        val insertedKeys = copies.map { index ->
            val base = copyBase(observation, index)
            val present = (0 until RECORD_COUNT).any { u32(destination, base + RECORDS + it * STRIDE) == id }
            val key = if (present) {
                // The record exists in this copy (older slot): keep its bytes and only place it.
                "copy:$index:record:${(0 until RECORD_COUNT).first { u32(destination, base + RECORDS + it * STRIDE) == id }}"
            } else {
                val slot = requireNotNull(freeRecordSlot(destination, base)) {
                    "The record table is full in a destination copy"
                }
                native.copyInto(candidate, base + RECORDS + slot * STRIDE)
                "copy:$index:record:$slot"
            }
            val alreadyInParty = (0 until PARTY_SLOTS).any { u32(destination, base + PARTY + it * 4) == id }
            if (wantsParty && !alreadyInParty) {
                val partySlot = requireNotNull(freePartySlot(destination, base)) { "The party has no free slot" }
                put32(candidate, base + PARTY + partySlot * 4, id)
            }
            if (u32(candidate, base + LAST_ID) < id) put32(candidate, base + LAST_ID, id)
            key
        }
        finalizeCopies(candidate, observation, copies)

        val after = WorldDs2SaveReader().read(candidate)
        copies.forEach { index ->
            val inserted = requireNotNull(after.records.singleOrNull {
                it.nativeIdentity == "save-local:$id" && it.nativeParameters?.saveBlock == index
            }) { "The inserted record did not read back in every save copy" }
            check(inserted.container == if (wantsParty) SaveContainer.PARTY else SaveContainer.UNASSIGNED) {
                "The inserted record did not land in the requested destination"
            }
            check(inserted.nativeRecordBase64.let { bytes ->
                bytes != null && WorldDsRecordCodec.decodeBase64(bytes).contentEquals(native)
            }) { "The inserted record changed" }
        }
        return Edit(observedRevision, insertedKeys.first(), candidate, destination, "save-local:$id")
    }

    /** Replaces one existing record with exact retained bytes (used to repair a stored individual). */
    fun replaceRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String): Edit {
        require(revision(destination) == observedRevision) { "Destination changed; read it again" }
        val native = WorldDsRecordCodec.decodeBase64(recordBase64)
        require(native.size == STRIDE) { "A retained record is exactly 0x164 bytes" }
        validateRetained(native)
        val id = u32(native, 0)
        val observation = WorldDs2SaveReader().read(destination)
        val copies = populatedCopies(observation)
        val slots = copies.map { index ->
            val base = copyBase(observation, index)
            (0 until RECORD_COUNT).firstOrNull { u32(destination, base + RECORDS + it * STRIDE) == id }
        }
        require(slots.any { it != null }) { "The record is not present in any populated copy" }
        val candidate = newCandidate(destination)
        copies.forEachIndexed { position, index ->
            val slot = slots[position] ?: return@forEachIndexed
            native.copyInto(candidate, copyBase(observation, index) + RECORDS + slot * STRIDE)
        }
        val touched = copies.filterIndexed { position, _ -> slots[position] != null }
        finalizeCopies(candidate, observation, touched)
        val after = WorldDs2SaveReader().read(candidate)
        touched.forEach { index ->
            check(after.records.any { it.nativeIdentity == "save-local:$id" && it.nativeParameters?.saveBlock == index }) {
                "The replacement did not read back in every touched copy"
            }
        }
        val firstKey = "copy:${copies.first { slots[copies.indexOf(it)] != null }}" +
            ":record:${requireNotNull(slots.first { it != null })}"
        return Edit(observedRevision, firstKey, candidate, destination, "save-local:$id")
    }

    /** Gives an existing record a party position without touching its bytes. */
    fun admitStorageRecordToParty(source: ByteArray, observedRevision: String, recordKey: String): Edit {
        require(revision(source) == observedRevision) { "Source changed; read it again" }
        val observation = WorldDs2SaveReader().read(source)
        val record = requireNotNull(observation.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        val identity = requireNotNull(record.nativeIdentity)
        val id = identity.removePrefix("save-local:").toLongOrNull() ?: throw IllegalArgumentException("No native identity")
        require(record.container == SaveContainer.UNASSIGNED) { "Only stored records need admitting" }
        val candidate = newCandidate(source)
        val admitted = populatedCopies(observation).filter { index ->
            val base = copyBase(observation, index)
            val present = (0 until RECORD_COUNT).any { u32(source, base + RECORDS + it * STRIDE) == id }
            if (!present) return@filter false
            val partySlot = requireNotNull(freePartySlot(source, base)) { "The party has no free slot" }
            put32(candidate, base + PARTY + partySlot * 4, id)
            true
        }
        require(admitted.isNotEmpty()) { "The record is not present in any populated copy" }
        finalizeCopies(candidate, observation, admitted)
        val after = WorldDs2SaveReader().read(candidate)
        admitted.forEach { index ->
            check(after.records.single {
                it.nativeIdentity == identity && it.nativeParameters?.saveBlock == index
            }.container == SaveContainer.PARTY) { "The admission did not take effect in every copy" }
        }
        return Edit(observedRevision, recordKey, candidate, source, record.nativeIdentity)
    }

    fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray = when (edit) {
        is Edit -> edit.restoreInto(candidate)
        else -> throw IllegalArgumentException("Edit does not belong to the Dawn/Dusk layout")
    }

    /** Mirrors the reader's validated ranges so a return can never store an out-of-range record. */
    private fun validateRetained(native: ByteArray) {
        val layout = PROFILE.record
        require(u32(native, 0) != 0L) { "A retained record must carry a nonzero local identity" }
        require(u32(native, 4) in 0x41L..0x1F4L) { "The retained native species is outside the validated range" }
        require((u16(native, layout.offsetUnk108) and PROFILE.liveFlagBits) == 0) {
            "Only records without live ownership flags can be returned"
        }
        val nameCodes = List(layout.nameSlots) { u16(native, layout.offsetName + it * 2) }
        require(nameCodes.any { it == layout.nameTerminator }) { "The retained name codes have no terminator" }
        require(u16(native, layout.offsetLevel) in 1..99) { "The retained level is outside the validated range" }
        val hp = u32(native, layout.offsetHp).toInt()
        val maxHp = u32(native, layout.offsetMaxHp).toInt()
        require(maxHp in 1..999999 && hp in 0..maxHp) { "The retained HP is outside the validated range" }
        require(layout.expChannels.let { channels ->
            (0 until channels).all { u32(native, layout.offsetExp + it * 4) in 0L..99999L }
        }) { "The retained experience counters are outside the validated range" }
    }

    private fun ByteArray.contentEqualsIgnoringWindows(source: ByteArray, base: Int, slot: Int,
        identity: String?): Boolean {
        if (size != source.size) return false
        val recordStart = base + RECORDS + slot * STRIDE
        for (index in indices) {
            if (this[index] == source[index]) continue
            val inRecord = index in recordStart until recordStart + STRIDE
            val inParty = index in base + PARTY until base + PARTY + PARTY_SLOTS * 4
            val inHeader = index in base until base + HEADER
            val inSequence = index in base + SEQUENCE until base + SEQUENCE + 4
            if (!inRecord && !inParty && !inHeader && !inSequence) return false
        }
        return true
    }

    /** Records with the copy sequence removed: every re-sealed copy bumps it for every record. */
    private fun List<ObservedDigimon>.withoutCopySequence(): List<ObservedDigimon> = map { record ->
        record.nativeParameters?.let { record.copy(nativeParameters = it.copy(saveSequence = null)) } ?: record
    }

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, index -> value or ((bytes[at + index].toLong() and 255) shl (8 * index)) }

    private fun put32(bytes: ByteArray, at: Int, value: Long) {
        repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    /** Exposed for diagnostics and tests: the save region size this editor assumes. */
    internal const val REGION_BYTES = SAVE_BYTES
}

/** Dawn/Dusk reader facade over the profile-driven generic reader. */
class WorldDs2SaveReader : GameSaveReader {
    private val delegate = NdsStorySaveReader(NdsStoryProfiles.DAWN_DUSK)

    override val gameId: String = NdsStoryProfiles.DAWN_DUSK.gameId
    override val maxInputBytes: Int = delegate.maxInputBytes
    override fun acceptsLength(length: Int): Boolean = delegate.acceptsLength(length)
    override fun read(bytes: ByteArray): SaveObservation = delegate.read(bytes)
}

/** Dawn/Dusk editor facade, resolved by [NdsStorySaveEditors]. */
object Ds2SaveEditor : NdsStorySaveEditor {
    override val profile: NdsStoryProfile get() = NdsStoryProfiles.DAWN_DUSK

    override fun read(bytes: ByteArray): SaveObservation = WorldDs2SaveReader().read(bytes)
    override fun removeRecord(source: ByteArray, observedRevision: String, recordKey: String) =
        Ds2SaveEdits.removeForResearch(source, observedRevision, recordKey)
    override fun insertRetained(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer?) =
        Ds2SaveEdits.insertRetainedRecord(destination, observedRevision, recordBase64, container)
    override fun insertGenerated(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer?) =
        Ds2SaveEdits.insertGeneratedRecord(destination, observedRevision, recordBase64, container)
    override fun replaceRetained(destination: ByteArray, observedRevision: String, recordBase64: String) =
        Ds2SaveEdits.replaceRetainedRecord(destination, observedRevision, recordBase64)
    override fun admitToParty(source: ByteArray, observedRevision: String, recordKey: String) =
        Ds2SaveEdits.admitStorageRecordToParty(source, observedRevision, recordKey)
    override fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray =
        Ds2SaveEdits.restore(candidate, edit)
}
