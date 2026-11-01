package com.tamerslab.gamesave

import java.util.Base64

/**
 * Structural removal/return evidence, not a playable save or an acquired vault individual.
 * Every edited copy has its integrity header resealed, so the game's loader accepts the result
 * (boot-verified: a save whose header lagged its data booted to "there is no save data" and the
 * card was erased afterwards). Production write capabilities still stay off.
 * The newest valid copy (or an explicitly reviewed copy) is authoritative. Older copies may diverge;
 * removals clear the identity wherever it exists, while admissions update stale copies best-effort.
 */
class WorldDsResearchRemoval internal constructor(
    override val sourceRevision: String,
    override val candidateRevision: String,
    override val recordKey: String,
    val nativeRecordBase64: String,
    internal val bases: List<Int>,
    internal val slot: Int,
    /** Slot that held the removed identity in each copy, or null where that copy never had it. */
    internal val slots: List<Int?>,
    /** Original bytes that occupied each copy's removed slot (copies may differ), for byte-identical undo. */
    internal val slotBytesBase64: List<String?>,
    internal val partiesBase64: List<String>,
    /** Each populated copy's integrity header exactly as the source left it, for byte-identical undo. */
    internal val headersBase64: List<String>,
    candidate: ByteArray,
) : NdsStoryPendingEdit {
    private val bytes = candidate.copyOf()
    override val candidateBytes: ByteArray get() = bytes.copyOf()
}

/**
 * Structural insertion evidence for a retained exact native record. The 0x208 write is read as an
 * ID high-water mark, verified only by the structural reader. The destination party cap is the
 * game's eight visible slots; the last two table entries are reserved and never assigned here.
 * The record is inserted into every populated save copy; a copy is never left without it; every
 * edited copy's integrity header is resealed so the game's loader accepts the result.
 */
class WorldDsResearchInsertion internal constructor(
    override val sourceRevision: String,
    override val candidateRevision: String,
    override val recordKey: String,
    val nativeIdentity: String,
    val slot: Int,
    val position: Int,
    internal val bases: List<Int>,
    /** Slot the record was written to in each copy; independent because copies may diverge. */
    internal val slots: List<Int>,
    /** Each populated copy's original slot bytes, including erased (0xFF) tails, for byte-identical undo. */
    internal val slotBytesBase64: List<String>,
    internal val partiesBase64: List<String>,
    internal val previousLastIds: List<Long>,
    /** Each populated copy's integrity header exactly as the source left it, for byte-identical undo. */
    internal val headersBase64: List<String>,
    candidate: ByteArray,
) : NdsStoryPendingEdit {
    private val bytes = candidate.copyOf()
    override val candidateBytes: ByteArray get() = bytes.copyOf()
}

/**
 * Structural replacement evidence for repairing a record that already exists in the destination.
 * The local identity and container stay unchanged; only the record bytes in each populated copy
 * change, and every edited copy's integrity header is resealed.
 */
class WorldDsResearchReplacement internal constructor(
    override val sourceRevision: String,
    override val candidateRevision: String,
    override val recordKey: String,
    val nativeIdentity: String,
    internal val bases: List<Int>,
    internal val slots: List<Int>,
    /** Each populated copy's original slot bytes, for byte-identical undo. */
    internal val slotBytesBase64: List<String>,
    /** Each populated copy's integrity header exactly as the source left it, for byte-identical undo. */
    internal val headersBase64: List<String>,
    candidate: ByteArray,
) : NdsStoryPendingEdit {
    private val bytes = candidate.copyOf()
    override val candidateBytes: ByteArray get() = bytes.copyOf()
}

/**
 * Structural admission evidence: an unreferenced storage record joins the visible party. The record's
 * slot is untouched; the authoritative copy's contiguous party table changes, compatible older copies
 * are updated best-effort, and every edited copy's integrity header is resealed. Slots 8 and 9 stay
 * reserved.
 */
class WorldDsResearchAdmission internal constructor(
    override val sourceRevision: String,
    override val candidateRevision: String,
    override val recordKey: String,
    val nativeIdentity: String,
    internal val bases: List<Int>,
    internal val slot: Int,
    internal val partiesBase64: List<String>,
    /** Each populated copy's integrity header exactly as the source left it, for byte-identical undo. */
    internal val headersBase64: List<String>,
    candidate: ByteArray,
) : NdsStoryPendingEdit {
    private val bytes = candidate.copyOf()
    override val candidateBytes: ByteArray get() = bytes.copyOf()
}

/** Byte-level World DS party move groundwork. Never writes a file or modifies caller-owned bytes. */
object WorldDsPartyEdits {
    private val PROFILE get() = NdsStoryProfiles.WORLD_DS
    private val LAST_ID get() = PROFILE.lastIdOffset
    private val PARTY get() = PROFILE.partyReferenceBase
    /** Full table width: slots 8 and 9 are reserved and never assigned to ordinary party members. */
    private val PARTY_COUNT get() = PROFILE.partySlots
    /** The game's party screen and add path expose exactly these eight slots (`data_ov002_0225a710`). */
    const val PARTY_CAP = 8
    private val RECORDS get() = PROFILE.recordRegion.base
    private val RECORD_COUNT get() = PROFILE.recordRegion.count
    private val STRIDE get() = PROFILE.recordRegion.stride
    /** Copy start of the integrity guard: MD5 over the copy with these bytes zeroed (crash-field cracked). */
    private val HEADER_BYTES get() = PROFILE.copyHeader.headerBytes
    // The game reacts to these unk108 bits (guest, lent, locked, egg, cross-DS). Every other bit,
    // including the unmodeled 0x40 observed on ordinary stored Digimon, travels verbatim.
    private const val LIVE_OWNERSHIP_FLAGS = 0x2 or 0x4 or 0x8 or 0x10 or 0x20

    fun removeForResearch(source: ByteArray, observedRevision: String, recordKey: String): WorldDsResearchRemoval {
        require(revision(source) == observedRevision) { "Source changed; read it again before planning a move" }
        val before = WorldDsSaveReader().read(source)
        val record = requireNotNull(before.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        val storage = record.container == SaveContainer.UNASSIGNED
        require((record.container == SaveContainer.PARTY || storage) && record.issues.all { it in removableIssues(storage) }) {
            "Removal requires an ordinary party member or an unreferenced storage record"
        }
        val identity = requireNotNull(record.nativeIdentity)
        val id = requireNotNull(identity.removePrefix("save-local:").toLongOrNull()) { "The record has no native identity" }
        val populated = before.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().sorted()
        require(populated.isNotEmpty()) { "The source has no populated save copy" }
        val bases = populated.map { base(before, it) }
        // Copies may be at different revisions: locate this individual's own slot in each copy by local id.
        val slots = bases.map { base -> (0 until RECORD_COUNT).firstOrNull { u32(source, base + RECORDS + it * STRIDE) == id } }
        require(slots.any { it != null }) { "The individual is not present in any populated save copy" }
        val slotBytes = bases.mapIndexed { index, base -> slots[index]?.let { slot ->
            source.copyOfRange(base + RECORDS + slot * STRIDE, base + RECORDS + slot * STRIDE + STRIDE)
        } }
        val authorityBlock = requireNotNull(record.nativeParameters?.saveBlock) { "The record has no save-copy authority" }
        val authorityIndex = populated.indexOf(authorityBlock)
        require(authorityIndex >= 0 && slots[authorityIndex] != null) { "The authoritative copy does not contain this individual" }
        val authorityBase = bases[authorityIndex]
        fun usable(base: Int, slot: Int): Boolean {
            val slotStart = base + RECORDS + slot * STRIDE
            return u32(source, slotStart) != 0L && (u16(source, slotStart + 0x108) and LIVE_OWNERSHIP_FLAGS) == 0
        }
        fun slotOf(base: Int, localId: Long): Int? = (0 until RECORD_COUNT).firstOrNull {
            u32(source, base + RECORDS + it * STRIDE) == localId
        }
        fun partyAt(base: Int): List<Long> = List(PARTY_COUNT) { u32(source, base + PARTY + it * 4) }
        fun remainingParty(base: Int, party: List<Long>): Int = party.count { member ->
            member != 0L && member != id && slotOf(base, member)?.let { usable(base, it) } == true
        }
        fun remainingStorage(base: Int, party: List<Long>): Int = (0 until RECORD_COUNT).count { slot ->
            val other = u32(source, base + RECORDS + slot * STRIDE)
            other != 0L && other != id && usable(base, slot) && party.none { it == other }
        }
        val authorityParty = partyAt(authorityBase)
        val authorityIsParty = authorityParty.any { it == id }
        require(authorityIsParty == (record.container == SaveContainer.PARTY)) {
            "The authoritative copy's container does not match the selected record"
        }
        // Last-member safety belongs to the authoritative copy only. Older copies may be stale and
        // must never turn a valid move into a copy-divergence block.
        val remainingParty = remainingParty(authorityBase, authorityParty)
        val remainingStorage = remainingStorage(authorityBase, authorityParty)
        require(if (authorityIsParty) remainingParty > 0 else remainingParty + remainingStorage > 0) {
            if (authorityIsParty) "The last usable party member must stay in the game"
            else "The last usable Digimon must stay in the game"
        }
        val originalParties = bases.map { source.copyOfRange(it + PARTY, it + RECORDS) }
        val originalHeaders = bases.map { source.copyOfRange(it, it + HEADER_BYTES) }
        val changedParties = mutableListOf<IntRange>()
        val candidate = source.copyOf()
        try {
            bases.forEachIndexed { index, base ->
                val at = slots[index]?.let { base + RECORDS + it * STRIDE }
                if (at != null) {
                    // Live ownership flags (guest, lent, locked, egg, cross-DS) and external
                    // references need their own removal semantics.
                    require((u16(source, at + 0x108) and LIVE_OWNERSHIP_FLAGS) == 0) { "Native ownership flags are not supported for removal" }
                    require((0 until 8).none { u32(source, base + 0x43D8 + it * 12 + 4) == id } &&
                        u32(source, base + 0x443C) != id) { "Native item or special-selection references require review" }
                }
                val party = partyAt(base)
                require(party.count { it == id } <= 1) { "Party references must be unique" }
                require((0 until 4).none { farm ->
                    (0 until 8).any { u32(source, base + 0x3E40 + farm * 0x6C + 0x44 + it * 4) == id } }) {
                    "Farm residents require their own removal semantics"
                }
                if (at == null) {
                    // A stale older copy may still reference an identity it no longer carries.
                    // Clear that dangling reference instead of letting it veto the move.
                    if (party.any { it == id }) {
                        repeat(PARTY_COUNT) { put32(candidate, base + PARTY + it * 4, if (party[it] == id) 0L else party[it]) }
                        changedParties += base + PARTY until base + RECORDS
                    }
                    return@forEachIndexed
                }
                candidate.fill(0, at, at + STRIDE)
                if (party.any { it == id }) {
                    repeat(PARTY_COUNT) { put32(candidate, base + PARTY + it * 4, if (party[it] == id) 0L else party[it]) }
                    changedParties += base + PARTY until base + RECORDS
                }
            }
            resealHeaders(candidate, bases)
            val after = WorldDsSaveReader().read(candidate)
            val removed = before.records.filter { it.nativeIdentity == identity }
            check(after.records.size == before.records.size - removed.size) { "Removal did not clear one copy of the native individual" }
            // Removing a party reference leaves a hole, exactly as the game does; other positions stay unchanged.
            val removedKeys = removed.map { it.recordKey }.toSet()
            val expected = before.records.filter { it.recordKey !in removedKeys }
            check(after.records == expected) { "Removal changed an unrelated native individual" }
            val windows = bases.flatMapIndexed { index, base ->
                buildList {
                    slots[index]?.let { add(base + RECORDS + it * STRIDE until base + RECORDS + it * STRIDE + STRIDE) }
                    add(base until base + HEADER_BYTES)
                }
            } + changedParties
            check(source.indices.all { index -> windows.any { index in it } || source[index] == candidate[index] }) {
                "Removal changed unrelated save bytes"
            }
            return WorldDsResearchRemoval(observedRevision, revision(candidate), recordKey,
                requireNotNull(record.nativeRecordBase64), bases, requireNotNull(slots.firstOrNull { it != null }), slots,
                slotBytes.map { bytes -> bytes?.let { Base64.getEncoder().encodeToString(it) } },
                originalParties.map { Base64.getEncoder().encodeToString(it) },
                originalHeaders.map { Base64.getEncoder().encodeToString(it) }, candidate)
        } finally {
            slotBytes.forEach { it?.fill(0) }
            originalParties.forEach { it.fill(0) }
            candidate.fill(0)
        }
    }

    /**
     * Insert one exact retained native record into the destination's first unused slot of every populated
     * copy and append its identity to each copy's contiguous party table. Nothing is guessed: retained
     * bytes are copied verbatim.
     */
    fun insertRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String): WorldDsResearchInsertion {
        require(revision(destination) == observedRevision) { "Destination changed; read it again before planning a return" }
        val before = WorldDsSaveReader().read(destination)
        val native = Base64.getDecoder().decode(recordBase64)
        try {
            require(native.size == STRIDE) { "A retained native record is exactly 384 bytes" }
            validateRetainedRecord(native)
            val id = u32(native, 0)
            require(before.records.none { it.nativeIdentity == "save-local:$id" }) {
                "The destination already owns the retained local identity"
            }
            return insertNativeRecord(destination, observedRevision, before, native, id)
        } finally {
            native.fill(0)
        }
    }

    /**
     * Insert a generated record with a fresh local identity. The seed bytes are copied only for
     * native fields the caller did not regenerate; identity, species, name and stat windows are
     * always overwritten before insertion.
     */
    fun insertGeneratedRecord(destination: ByteArray, observedRevision: String, recordBase64: String): WorldDsResearchInsertion {
        require(revision(destination) == observedRevision) { "Destination changed; read it again before planning a return" }
        val before = WorldDsSaveReader().read(destination)
        val seed = Base64.getDecoder().decode(recordBase64)
        val native = seed.copyOf()
        try {
            require(native.size == STRIDE) { "A generated native record is exactly 384 bytes" }
            val previousLastIds = before.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct()
                .map { u32(destination, base(before, it) + LAST_ID) }
            val liveIds = before.records.mapNotNull {
                it.nativeIdentity?.removePrefix("save-local:")?.toLongOrNull()
            }
            val id = ((liveIds + previousLastIds).maxOrNull() ?: 0L) + 1
            require(id in 1 until 0xFFFFFF00L) { "The destination has no safe native identity space" }
            put32(native, 0, id)
            validateRetainedRecord(native)
            return insertNativeRecord(destination, observedRevision, before, native, id)
        } finally {
            seed.fill(0)
            native.fill(0)
        }
    }

    /**
     * Repair one record that already exists in the destination, preserving its local identity,
     * party/storage placement, high-water mark and every unrelated record. The replacement bytes
     * are written to the same slot in every populated copy; a copy missing the identity blocks the
     * repair instead of silently creating a second copy.
     */
    fun replaceRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String): WorldDsResearchReplacement {
        require(revision(destination) == observedRevision) { "Destination changed; read it again before repair" }
        val before = WorldDsSaveReader().read(destination)
        val native = Base64.getDecoder().decode(recordBase64)
        try {
            require(native.size == STRIDE) { "A retained native record is exactly 384 bytes" }
            validateRetainedRecord(native)
            val id = u32(native, 0)
            val identity = "save-local:$id"
            val existing = before.records.filter { it.nativeIdentity == identity }
            require(existing.isNotEmpty()) { "The destination does not own the retained local identity" }
            val populated = before.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().sorted()
            require(populated.isNotEmpty()) { "The destination has no populated save copy" }
            val bases = populated.map { base(before, it) }
            val slots = bases.map { base ->
                (0 until RECORD_COUNT).firstOrNull { u32(destination, base + RECORDS + it * STRIDE) == id }
            }
            require(slots.all { it != null }) { "Every populated save copy must hold this individual before repair" }
            val slotIndexes = slots.map { requireNotNull(it) }
            val originalSlots = bases.mapIndexed { index, base ->
                destination.copyOfRange(base + RECORDS + slotIndexes[index] * STRIDE,
                    base + RECORDS + slotIndexes[index] * STRIDE + STRIDE)
            }
            val originalHeaders = bases.map { destination.copyOfRange(it, it + HEADER_BYTES) }
            val candidate = destination.copyOf()
            try {
                bases.forEachIndexed { index, base ->
                    val at = base + RECORDS + slotIndexes[index] * STRIDE
                    require((u16(destination, at + 0x108) and LIVE_OWNERSHIP_FLAGS) == 0) {
                        "Native ownership flags are not supported for repair"
                    }
                    native.copyInto(candidate, at)
                }
                resealHeaders(candidate, bases)
                val after = WorldDsSaveReader().read(candidate)
                check(after.records.size == before.records.size) { "Repair changed the native record count" }
                val repaired = after.records.filter { it.nativeIdentity == identity }
                check(repaired.size == existing.size) { "Repair did not replace one record per populated copy" }
                val expected = Base64.getEncoder().encodeToString(native)
                check(repaired.all { it.nativeRecordBase64 == expected }) { "Repair did not write the exact replacement" }
                val repairedPlacement = repaired.map { Triple(it.recordKey, it.container, it.position) }.sortedBy { it.first }
                val existingPlacement = existing.map { Triple(it.recordKey, it.container, it.position) }.sortedBy { it.first }
                check(repairedPlacement == existingPlacement) { "Repair changed the record's container or position" }
                check(after.records.filter { it.nativeIdentity != identity } ==
                    before.records.filter { it.nativeIdentity != identity }) {
                    "Repair changed an unrelated native individual"
                }
                val windows = bases.flatMapIndexed { index, base ->
                    listOf(base + RECORDS + slotIndexes[index] * STRIDE until
                        base + RECORDS + slotIndexes[index] * STRIDE + STRIDE, base until base + HEADER_BYTES)
                }
                check(destination.indices.all { index -> windows.any { index in it } || destination[index] == candidate[index] }) {
                    "Repair changed unrelated save bytes"
                }
                return WorldDsResearchReplacement(observedRevision, revision(candidate),
                    existing.first().recordKey, identity, bases, slotIndexes,
                    originalSlots.map { Base64.getEncoder().encodeToString(it) },
                    originalHeaders.map { Base64.getEncoder().encodeToString(it) }, candidate)
            } finally {
                originalSlots.forEach { it.fill(0) }
                candidate.fill(0)
            }
        } finally {
            native.fill(0)
        }
    }

    private fun insertNativeRecord(
        destination: ByteArray,
        observedRevision: String,
        before: SaveObservation,
        native: ByteArray,
        id: Long,
    ): WorldDsResearchInsertion {
        val expectedBase64 = Base64.getEncoder().encodeToString(native)
        val populated = before.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().sorted()
        require(populated.isNotEmpty()) { "The destination has no populated save copy" }
        val bases = populated.map { base(before, it) }
        val previousLastIds = bases.map { u32(destination, it + LAST_ID) }
        require(previousLastIds.all { it != 0L }) { "The destination has no allocated native identity space" }
        val partyByBlock = bases.map { base ->
            val party = MutableList(PARTY_COUNT) { u32(destination, base + PARTY + it * 4) }
            require(party.none { it == id }) { "The destination party already references this individual" }
            val free = (0 until PARTY_CAP).firstOrNull { party[it] == 0L }
                ?: throw IllegalArgumentException("The destination has no free visible party slot (eight slots)")
            party[free] = id
            party to (free + 1)
        }
        val slots = bases.map { base ->
            (0 until RECORD_COUNT).firstOrNull { index ->
                u32(destination, base + RECORDS + index * STRIDE) == 0L
            } ?: throw IllegalArgumentException("No unused record slot is available in a populated copy of the destination")
        }
        val insertedKeys = bases.mapIndexed { index, base -> "copy:${populated[index]}:record:${slots[index]}" }
        val originalSlots = bases.mapIndexed { index, base ->
            destination.copyOfRange(base + RECORDS + slots[index] * STRIDE, base + RECORDS + slots[index] * STRIDE + STRIDE)
        }
        val originalParties = bases.map { destination.copyOfRange(it + PARTY, it + RECORDS) }
        val originalHeaders = bases.map { destination.copyOfRange(it, it + HEADER_BYTES) }
        val candidate = destination.copyOf()
        try {
            bases.forEachIndexed { index, base ->
                native.copyInto(candidate, base + RECORDS + slots[index] * STRIDE)
                repeat(PARTY_COUNT) { put32(candidate, base + PARTY + it * 4, partyByBlock[index].first[it]) }
                if (id > previousLastIds[index]) put32(candidate, base + LAST_ID, id)
            }
            resealHeaders(candidate, bases)
            val after = WorldDsSaveReader().read(candidate)
            check(after.records.size == before.records.size + populated.size) {
                "Insertion did not add exactly one native individual per populated copy"
            }
            populated.forEachIndexed { index, block ->
                val inserted = after.records.singleOrNull {
                    it.nativeIdentity == "save-local:$id" && it.nativeParameters?.saveBlock == block
                }
                check(inserted != null && inserted.nativeRecordBase64 == expectedBase64 &&
                    inserted.container == SaveContainer.PARTY && inserted.position == partyByBlock[index].second &&
                    inserted.issues.all { it in removableIssues(false) }) {
                    "Insertion did not produce the exact expected record in every populated copy"
                }
            }
            check(after.records.filter { it.nativeIdentity != "save-local:$id" } == before.records) {
                "Insertion changed an unrelated native individual"
            }
            val windows = bases.flatMapIndexed { index, base ->
                listOf(base + LAST_ID until base + RECORDS,
                    base + RECORDS + slots[index] * STRIDE until base + RECORDS + slots[index] * STRIDE + STRIDE) +
                    listOf(base until base + HEADER_BYTES)
            }
            check(destination.indices.all { index -> windows.any { index in it } || destination[index] == candidate[index] }) {
                "Insertion changed unrelated save bytes"
            }
            return WorldDsResearchInsertion(observedRevision, revision(candidate), insertedKeys.first(), "save-local:$id",
                slots.first(), partyByBlock.first().second, bases, slots,
                originalSlots.map { Base64.getEncoder().encodeToString(it) },
                originalParties.map { Base64.getEncoder().encodeToString(it) }, previousLastIds,
                originalHeaders.map { Base64.getEncoder().encodeToString(it) }, candidate)
        } finally {
            originalSlots.forEach { it.fill(0) }
            originalParties.forEach { it.fill(0) }
            candidate.fill(0)
        }
    }

    /**
     * Adds one unreferenced storage record's identity to the authoritative copy's first free visible
     * party slot. Other populated copies are updated when they still contain the identity and have a
     * safe free slot; stale copies missing it never block and only lose dangling party references.
     * The record's own slot stays verbatim, slots 8 and 9 stay reserved, and farm/ownership guards
     * mirror the removal semantics.
     */
    fun admitStorageRecordToParty(source: ByteArray, observedRevision: String, recordKey: String): WorldDsResearchAdmission {
        require(revision(source) == observedRevision) { "Source changed; read it again before planning a move" }
        val before = WorldDsSaveReader().read(source)
        val record = requireNotNull(before.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        require(record.container == SaveContainer.UNASSIGNED &&
            record.issues.all { it == RecordIssue.AMBIGUOUS_SAVE_BLOCK || it == RecordIssue.UNASSIGNED_LOCATION }) {
            "Admission requires an unreferenced storage record"
        }
        val identity = requireNotNull(record.nativeIdentity)
        val id = requireNotNull(identity.removePrefix("save-local:").toLongOrNull()) { "The record has no native identity" }
        val populated = before.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().sorted()
        require(populated.isNotEmpty()) { "The source has no populated save copy" }
        val bases = populated.map { base(before, it) }
        // Locate the identity's own slot per copy; copies may hold it at different indices.
        val slots = bases.map { base -> (0 until RECORD_COUNT).firstOrNull { u32(source, base + RECORDS + it * STRIDE) == id } }
        val authorityBlock = requireNotNull(record.nativeParameters?.saveBlock) { "The record has no save-copy authority" }
        val authorityIndex = populated.indexOf(authorityBlock)
        require(authorityIndex >= 0 && slots[authorityIndex] != null) { "The authoritative copy does not contain this individual" }
        val parties = bases.map { base -> MutableList(PARTY_COUNT) { u32(source, base + PARTY + it * 4) } }
        val changed = MutableList(bases.size) { false }
        val verified = MutableList(bases.size) { false }
        bases.forEachIndexed { index, base ->
            if (index == authorityIndex) verified[index] = true
            val slot = slots[index]
            if (slot == null) {
                // Stale copies that still list the identity without carrying its record are
                // repaired by dropping the dangling reference; they never block the admission.
                if (index != authorityIndex && parties[index].any { it == id }) {
                    parties[index].replaceAll { if (it == id) 0L else it }
                    changed[index] = true
                }
                return@forEachIndexed
            }
            if ((u16(source, base + RECORDS + slot * STRIDE + 0x108) and LIVE_OWNERSHIP_FLAGS) != 0) {
                require(index != authorityIndex) { "Native ownership flags are not supported for admission" }
                return@forEachIndexed
            }
            val party = parties[index]
            if (party.any { it == id }) {
                verified[index] = true
                return@forEachIndexed
            }
            if ((0 until 4).any { farm ->
                    (0 until 8).any { u32(source, base + 0x3E40 + farm * 0x6C + 0x44 + it * 4) == id } }) {
                require(index != authorityIndex) { "Farm residents require their own admission semantics" }
                return@forEachIndexed
            }
            val free = (0 until PARTY_CAP).firstOrNull { party[it] == 0L }
            if (free == null) {
                require(index != authorityIndex) { "The visible party is full (eight slots)" }
                return@forEachIndexed
            }
            party[free] = id
            changed[index] = true
            verified[index] = true
        }
        val originalParties = bases.map { source.copyOfRange(it + PARTY, it + RECORDS) }
        val originalHeaders = bases.map { source.copyOfRange(it, it + HEADER_BYTES) }
        val candidate = source.copyOf()
        try {
            bases.forEachIndexed { index, base ->
                if (changed[index]) repeat(PARTY_COUNT) { put32(candidate, base + PARTY + it * 4, parties[index][it]) }
            }
            resealHeaders(candidate, bases)
            val after = WorldDsSaveReader().read(candidate)
            check(after.records.size == before.records.size) { "Admission must not change the record count" }
            populated.forEachIndexed { index, block ->
                if (!verified[index]) return@forEachIndexed
                val admitted = after.records.singleOrNull { it.nativeIdentity == identity && it.nativeParameters?.saveBlock == block }
                check(admitted != null && admitted.container == SaveContainer.PARTY &&
                    (index != authorityIndex || admitted.nativeRecordBase64 == record.nativeRecordBase64)) {
                    "Admission did not produce a party member in the authoritative copy"
                }
            }
            check(after.records.filter { it.nativeIdentity != identity } == before.records.filter { it.nativeIdentity != identity }) {
                "Admission changed an unrelated native individual"
            }
            val windows = bases.mapIndexedNotNull { index, base ->
                if (changed[index]) base + PARTY until base + RECORDS else null
            } + bases.map { it until it + HEADER_BYTES }
            check(source.indices.all { index -> windows.any { index in it } || source[index] == candidate[index] }) {
                "Admission changed unrelated save bytes"
            }
            return WorldDsResearchAdmission(observedRevision, revision(candidate), recordKey,
                identity, bases, requireNotNull(slots[authorityIndex]),
                originalParties.map { Base64.getEncoder().encodeToString(it) },
                originalHeaders.map { Base64.getEncoder().encodeToString(it) }, candidate)
        } finally {
            originalParties.forEach { it.fill(0) }
            candidate.fill(0)
        }
    }

    /** Only the exact unmodified research candidate can be undone; the original slot bytes return byte-identically. */
    fun restoreResearchInsertion(candidate: ByteArray, insertion: WorldDsResearchInsertion): ByteArray {
        require(revision(candidate) == insertion.candidateRevision) { "Candidate changed; an old undo cannot be applied" }
        val slotBytes = insertion.slotBytesBase64.map { Base64.getDecoder().decode(it) }
        val parties = insertion.partiesBase64.map { Base64.getDecoder().decode(it) }
        val headers = insertion.headersBase64.map { Base64.getDecoder().decode(it) }
        val restored = candidate.copyOf()
        try {
            require(parties.size == insertion.bases.size && parties.all { it.size == PARTY_COUNT * 4 } &&
                insertion.previousLastIds.size == insertion.bases.size && insertion.slots.size == insertion.bases.size &&
                slotBytes.size == insertion.bases.size && slotBytes.all { it.size == STRIDE } &&
                headers.size == insertion.bases.size && headers.all { it.size == HEADER_BYTES })
            insertion.bases.forEachIndexed { index, base ->
                slotBytes[index].copyInto(restored, base + RECORDS + insertion.slots[index] * STRIDE)
                parties[index].copyInto(restored, base + PARTY)
                put32(restored, base + LAST_ID, insertion.previousLastIds[index])
                headers[index].copyInto(restored, base)
            }
            check(revision(restored) == insertion.sourceRevision) { "Native insertion/undo was not byte-identical" }
            WorldDsSaveReader().read(restored)
            return restored
        } catch (failure: Throwable) {
            restored.fill(0)
            throw failure
        } finally {
            slotBytes.forEach { it.fill(0) }
            parties.forEach { it.fill(0) }
            headers.forEach { it.fill(0) }
        }
    }

    /** Only the exact unmodified research candidate can be undone; original record bytes return byte-identically. */
    fun restoreResearchReplacement(candidate: ByteArray, replacement: WorldDsResearchReplacement): ByteArray {
        require(revision(candidate) == replacement.candidateRevision) { "Candidate changed; an old repair undo cannot be applied" }
        val slotBytes = replacement.slotBytesBase64.map { Base64.getDecoder().decode(it) }
        val headers = replacement.headersBase64.map { Base64.getDecoder().decode(it) }
        val restored = candidate.copyOf()
        try {
            require(slotBytes.size == replacement.bases.size && slotBytes.all { it.size == STRIDE } &&
                replacement.slots.size == replacement.bases.size && headers.size == replacement.bases.size &&
                headers.all { it.size == HEADER_BYTES })
            replacement.bases.forEachIndexed { index, base ->
                slotBytes[index].copyInto(restored, base + RECORDS + replacement.slots[index] * STRIDE)
                headers[index].copyInto(restored, base)
            }
            check(revision(restored) == replacement.sourceRevision) { "Native repair/undo was not byte-identical" }
            WorldDsSaveReader().read(restored)
            return restored
        } catch (failure: Throwable) {
            restored.fill(0)
            throw failure
        } finally {
            slotBytes.forEach { it.fill(0) }
            headers.forEach { it.fill(0) }
        }
    }

    /** Only the exact unmodified research candidate can be returned; never overwrite later game progress. */
    fun restoreResearchRemoval(candidate: ByteArray, removal: WorldDsResearchRemoval): ByteArray {
        require(revision(candidate) == removal.candidateRevision) { "Candidate changed; an old return cannot be applied" }
        val slotBytes = removal.slotBytesBase64.map { it?.let { bytes -> Base64.getDecoder().decode(bytes) } }
        val parties = removal.partiesBase64.map { Base64.getDecoder().decode(it) }
        val headers = removal.headersBase64.map { Base64.getDecoder().decode(it) }
        val restored = candidate.copyOf()
        try {
            require(slotBytes.size == removal.bases.size && removal.slots.size == removal.bases.size &&
                parties.size == removal.bases.size && parties.all { it.size == PARTY_COUNT * 4 } &&
                headers.size == removal.bases.size && headers.all { it.size == HEADER_BYTES })
            removal.bases.forEachIndexed { index, base ->
                val slot = removal.slots[index]
                if (slot != null) requireNotNull(slotBytes[index]).copyInto(restored, base + RECORDS + slot * STRIDE)
                parties[index].copyInto(restored, base + PARTY)
                headers[index].copyInto(restored, base)
            }
            check(revision(restored) == removal.sourceRevision) { "Native removal/return was not byte-identical" }
            WorldDsSaveReader().read(restored)
            return restored
        } catch (failure: Throwable) {
            restored.fill(0)
            throw failure
        } finally {
            slotBytes.forEach { it?.fill(0) }
            parties.forEach { it.fill(0) }
            headers.forEach { it.fill(0) }
        }
    }

    /** Only the exact unmodified research candidate can be undone; the party table returns byte-identically. */
    fun restoreResearchAdmission(candidate: ByteArray, admission: WorldDsResearchAdmission): ByteArray {
        require(revision(candidate) == admission.candidateRevision) { "Candidate changed; an old undo cannot be applied" }
        val parties = admission.partiesBase64.map { Base64.getDecoder().decode(it) }
        val headers = admission.headersBase64.map { Base64.getDecoder().decode(it) }
        val restored = candidate.copyOf()
        try {
            require(parties.size == admission.bases.size && parties.all { it.size == PARTY_COUNT * 4 } &&
                headers.size == admission.bases.size && headers.all { it.size == HEADER_BYTES })
            admission.bases.forEachIndexed { index, base ->
                parties[index].copyInto(restored, base + PARTY)
                headers[index].copyInto(restored, base)
            }
            check(revision(restored) == admission.sourceRevision) { "Native admission/undo was not byte-identical" }
            WorldDsSaveReader().read(restored)
            return restored
        } catch (failure: Throwable) {
            restored.fill(0)
            throw failure
        } finally {
            parties.forEach { it.fill(0) }
            headers.forEach { it.fill(0) }
        }
    }

    /** Removal/admission only need the record bytes; these flags describe the source image, not ownership. */
    private fun removableIssues(storage: Boolean): Set<RecordIssue> = buildSet {
        add(RecordIssue.AMBIGUOUS_SAVE_BLOCK)
        add(RecordIssue.IMPLAUSIBLE_VALUES)
        add(RecordIssue.UNKNOWN_NAME_CODES)
        if (storage) add(RecordIssue.UNASSIGNED_LOCATION)
    }

    /** Mirrors the reader's validated ranges so a return can never store an out-of-range record. */
    private fun validateRetainedRecord(native: ByteArray) {
        require(u32(native, 0) != 0L) { "A retained record must carry a nonzero local identity" }
        require(u32(native, 4) in 0L..425L) { "The retained native species is outside the validated range" }
        require((u16(native, 0x108) and LIVE_OWNERSHIP_FLAGS) == 0) {
            "Only native records without live ownership flags can be returned"
        }
        val nameCodes = List(9) { u16(native, 8 + it * 2) }
        require(nameCodes.any { it == 0xFFFF }) { "The retained name codes have no terminator" }
        require(u16(native, 0x74).toShort().toInt() in 1..99 && u16(native, 0x52).toShort().toInt() in 0..999) {
            "The retained level or aptitude is outside the validated range"
        }
        val hp = u32(native, 0x3C).toInt()
        val maxHp = u32(native, 0x40).toInt()
        val mp = u16(native, 0x44).toShort().toInt()
        val maxMp = u16(native, 0x46).toShort().toInt()
        require(maxHp in 1..999999 && hp in 0..maxHp) { "The retained HP is outside the validated range" }
        require(maxMp >= 0 && mp in 0..maxMp) { "The retained MP is outside the validated range" }
        require(listOf(0x48, 0x4A, 0x4C, 0x4E).all { u16(native, it).toShort().toInt() >= 0 }) {
            "The retained combat stats are outside the validated range"
        }
        require((0 until 8).all { u32(native, 0x78 + it * 4).toInt().toLong() in 0L..99999L }) {
            "The retained experience counters are outside the validated range"
        }
    }

    /** One populated copy's block base inside the container, wrappers included. */
    private fun base(observation: SaveObservation, saveBlock: Int): Int =
        observation.imageBase + (saveBlock - 1) * PROFILE.copyStride

    private fun u16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, i -> value or ((bytes[at + i].toLong() and 255) shl (8 * i)) }

    private fun put32(bytes: ByteArray, at: Int, value: Long) {
        repeat(4) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    /**
     * The loader's per-copy guard: MD5 over the copy's SAVE_BYTES with the header range zeroed,
     * stored at the copy's start. Verified byte-exactly against two real saves; a stale header is
     * rejected at boot ("there is no save data") and the card is erased afterwards. Resealing an
     * unedited copy reproduces the game's own header byte-identically, so undo paths can rely on it.
     */
    private fun resealHeaders(bytes: ByteArray, bases: List<Int>) {
        bases.forEach { NdsStoryCopyIntegrity.seal(bytes, it, PROFILE) }
    }
}
