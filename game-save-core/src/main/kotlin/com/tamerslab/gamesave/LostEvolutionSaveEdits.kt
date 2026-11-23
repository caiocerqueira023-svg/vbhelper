package com.tamerslab.gamesave

import java.util.Base64

/**
 * Read-only Lost Evolution save decoder over the verified layout in [LostEvolution].
 *
 * A slot is accepted only when its 16-byte MD5 header matches the slot bytes (header zeroed) and the
 * game's `"DM3_000"` signature is present. Both card slots are decoded; records are reported per
 * copy with their write sequence so the generic copy selection can pick the game's newest slot.
 * Every record's 0x78 native bytes are retained, which the writer layer uses for exact moves.
 */
class LostEvolutionSaveReader : GameSaveReader {
    override val gameId: String = LostEvolution.GAME_ID
    override val maxInputBytes: Int = LostEvolution.FILE_BYTES

    override fun acceptsLength(length: Int): Boolean =
        length == LostEvolution.FILE_BYTES || length == LostEvolution.SAVE_BYTES

    override fun read(bytes: ByteArray): SaveObservation {
        require(bytes.size == LostEvolution.FILE_BYTES || bytes.size == LostEvolution.SAVE_BYTES) {
            "A Lost Evolution save is a 64 KiB image or one 0x8000-byte slot"
        }
        val profile = NdsStoryProfiles.LOST_EVOLUTION
        val copyBases = candidateCopies(bytes)
        if (copyBases.isEmpty()) throw SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT)
        val ambiguous = copyBases.size > 1
        val copyIntegrity = copyBases.associate { base -> copyNumber(base) to NdsStoryCopyIntegrity.isValid(bytes, base, profile) }
        val records = copyBases.flatMap { base -> readCopy(bytes, base, ambiguous) }
        return SaveObservation(
            gameId = LostEvolution.GAME_ID,
            formatProfile = LostEvolution.FORMAT_PROFILE,
            codecVersion = LostEvolution.CODEC_VERSION,
            contentRevision = revision(bytes),
            records = records,
            notices = buildList {
                add(ObservationNotice.SPECIES_MAPPING_PENDING)
                add(ObservationNotice.IDENTITY_RESEARCH_PENDING)
                if (ambiguous) add(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED)
            },
            capabilities = SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true),
            copyIntegrity = copyIntegrity,
            imageBase = 0,
        )
    }

    /** Copy bases that carry a valid signature; a 0x8000-byte bare slot counts as copy 1. */
    internal fun candidateCopies(bytes: ByteArray): List<Int> {
        val bases = if (bytes.size == LostEvolution.SAVE_BYTES) listOf(0)
        else (0 until 2).map { index -> index * LostEvolution.COPY_STRIDE }
        return bases.filter { base -> hasSignature(bytes, base) }
    }

    internal fun hasSignature(bytes: ByteArray, base: Int): Boolean {
        if (base < 0 || base + LostEvolution.SAVE_BYTES > bytes.size) return false
        val at = base + LostEvolution.TAG_OFFSET
        return bytes.copyOfRange(at, at + LostEvolution.TAG.size).contentEquals(LostEvolution.TAG)
    }

    private fun copyNumber(base: Int): Int = base / LostEvolution.COPY_STRIDE + 1

    internal fun sequence(bytes: ByteArray, base: Int): Long =
        LostEvolution.u32(bytes, base + LostEvolution.SEQUENCE_OFFSET)

    private fun readCopy(bytes: ByteArray, base: Int, ambiguous: Boolean): List<ObservedDigimon> {
        val copy = copyNumber(base)
        val sequence = sequence(bytes, base)
        val identities = (0 until LostEvolution.RECORD_COUNT).map { slot ->
            val recordBase = base + LostEvolution.ROSTER_SAVE_BASE + slot * LostEvolution.RECORD_BYTES
            LostEvolution.identity(bytes.copyOfRange(recordBase, recordBase + LostEvolution.RECORD_BYTES))
        }
        val party = (0 until LostEvolution.PARTY_SLOTS).map { slot ->
            LostEvolution.u64(bytes, base + LostEvolution.PARTY_SAVE_BASE + slot * 8)
        }
        val farm = farmIdentities(bytes, base)
        val team = (0 until LostEvolution.TEAM_SLOTS).map { slot ->
            LostEvolution.u64(bytes, base + LostEvolution.TEAM_SAVE_BASE + slot * 8)
        }
        val records = mutableListOf<ObservedDigimon>()
        for (slot in 0 until LostEvolution.RECORD_COUNT) {
            val identity = identities[slot]
            if (identity == 0L) continue
            val recordBase = base + LostEvolution.ROSTER_SAVE_BASE + slot * LostEvolution.RECORD_BYTES
            val native = bytes.copyOfRange(recordBase, recordBase + LostEvolution.RECORD_BYTES)
            val species = LostEvolution.species(native)
            val name = LostEvolutionTextCodec.decode(native.copyOfRange(LostEvolution.OFFSET_NAME,
                LostEvolution.OFFSET_NAME + LostEvolution.NAME_BYTES).map { it.toInt() and 0xFF })
            val container: SaveContainer
            val position: Int
            when {
                identity in party.take(LostEvolution.PARTY_SLOTS) -> {
                    container = SaveContainer.PARTY
                    position = party.indexOf(identity) + 1
                }
                identity in farm -> {
                    container = SaveContainer.FARM
                    position = farm.indexOf(identity) + 1
                }
                identity in team -> {
                    container = SaveContainer.BANK
                    position = team.indexOf(identity) + 1
                }
                else -> {
                    container = SaveContainer.UNASSIGNED
                    position = slot + 1
                }
            }
            val stats = LostEvolution.stats(native)
            val issues = buildList {
                if (identities.count { it == identity } > 1) add(RecordIssue.DUPLICATE_IDENTITY)
                if (species !in 0 until LostEvolution.SPECIES_COUNT) add(RecordIssue.IMPLAUSIBLE_VALUES)
                if (species in 0 until LostEvolution.FIRST_DIGIMON) add(RecordIssue.EGG_RECORD)
                if (name.isEmpty() || name.contains('\uFFFD')) add(RecordIssue.UNKNOWN_NAME_CODES)
                if (LostEvolution.level(native) !in 0..LostEvolution.MAX_LEVEL) add(RecordIssue.IMPLAUSIBLE_VALUES)
                if (container == SaveContainer.UNASSIGNED) add(RecordIssue.UNASSIGNED_LOCATION)
                if (ambiguous) add(RecordIssue.AMBIGUOUS_SAVE_BLOCK)
            }.distinct()
            records += ObservedDigimon(
                recordKey = "copy:$copy:record:$slot",
                container = container,
                position = position,
                nativeSpeciesId = species.toLong(),
                storedName = name,
                level = LostEvolution.level(native),
                personalityId = null,
                identityCandidate = 0,
                membership = if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
                issues = issues,
                totalExp = LostEvolution.exp(native),
                talentRaw = native[LostEvolution.OFFSET_VARIANT].toLong() and 0xFF,
                bondRaw = (LostEvolution.u16(native, LostEvolution.OFFSET_FRIENDSHIP)).toFloat(),
                previousSpeciesIds = emptyList(),
                nativeRecordBase64 = Base64.getEncoder().encodeToString(native),
                nativeIdentity = LostEvolution.identityText(identity),
                nativeParameters = NativeStatParameters(stats.hp, stats.maxHp, stats.mp, stats.maxMp,
                    stats.attack, stats.defense, stats.spirit, stats.speed, stats.aptitude,
                    listOf(LostEvolution.exp(native)), copy, sequence),
            )
        }
        return records
    }

    /** Resident identities of every farm group (group count lives at FARM_SAVE_BASE). */
    internal fun farmIdentities(bytes: ByteArray, base: Int): List<Long> {
        val count = LostEvolution.u32(bytes, base + LostEvolution.FARM_SAVE_BASE)
            .toInt().coerceIn(0, LostEvolution.FARM_GROUPS_MAX)
        val residents = mutableListOf<Long>()
        for (group in 0 until count) {
            val groupBase = base + LostEvolution.FARM_SAVE_BASE + 4 +
                group * LostEvolution.FARM_GROUP_STRIDE
            for (slot in 0 until LostEvolution.FARM_RESIDENTS) {
                residents += LostEvolution.u64(bytes, groupBase + LostEvolution.FARM_RESIDENT_OFFSET + slot * 8)
            }
        }
        return residents
    }
}

/**
 * Verifiable Lost Evolution edits. Every operation re-reads the exact source, edits only the copy
 * the game would load (the newest valid slot), bumps that slot's write sequence, reseals its MD5
 * header, and verifies the readback before returning a candidate. The writer layer still owns
 * backups, journaling and the actual file write.
 */
object LostEvolutionSaveEdits {
    private val PROFILE get() = NdsStoryProfiles.LOST_EVOLUTION

    private fun authority(observation: SaveObservation): Int {
        val copy = NdsStoryCopySelection.authoritativeCopy(observation)
            ?: NdsStoryCopySelection.latestCopy(observation)
            ?: observation.records.mapNotNull { it.nativeParameters?.saveBlock }.distinct().singleOrNull()
            ?: throw IllegalArgumentException("The save has no defensible authoritative copy")
        require(observation.copyIntegrity[copy] != false) {
            "The authoritative copy does not match its integrity header; save once in-game first"
        }
        return copy
    }

    private fun copyBase(observation: SaveObservation, copy: Int): Int =
        observation.imageBase + (copy - 1) * PROFILE.copyStride

    private fun recordBase(observation: SaveObservation, copy: Int, slot: Int): Int =
        copyBase(observation, copy) + LostEvolution.ROSTER_SAVE_BASE + slot * LostEvolution.RECORD_BYTES

    /**
     * Commits one edited slot body the way the game's own save writer does: both card copies get the
     * same edited body with consecutive write sequences (slot 1 lower, slot 2 newer) and a fresh MD5
     * header. The genuine saves on disk always have identical bodies in both slots, so writing only
     * one copy left the other one divergent - which the game treats as an unusable save.
     */
    private fun finalize(candidate: ByteArray, observation: SaveObservation, copy: Int) {
        val editedBase = copyBase(observation, copy)
        val body = candidate.copyOfRange(editedBase, editedBase + LostEvolution.SAVE_BYTES)
        // The engine stores an XOR checksum of the serialized player state at +0x4078; leaving the
        // source value in place makes the modified slot fail the game's post-load validation.
        LostEvolution.recomputeStateChecksum(body)
        val previousSequence = (0 until PROFILE.copyCount).maxOf { index ->
            LostEvolution.u32(candidate, imageBase(observation) + index * PROFILE.copyStride +
                LostEvolution.SEQUENCE_OFFSET)
        }
        for (index in 0 until PROFILE.copyCount) {
            val base = imageBase(observation) + index * PROFILE.copyStride
            body.copyInto(candidate, base)
            LostEvolution.putU32(candidate, base + LostEvolution.SEQUENCE_OFFSET,
                previousSequence + 1 + index)
            NdsStoryCopyIntegrity.seal(candidate, base, PROFILE)
        }
        body.fill(0)
    }

    private fun imageBase(observation: SaveObservation): Int = observation.imageBase

    /** A planned candidate plus the exact source bytes, so undo is byte-exact. */
    class Edit internal constructor(
        override val sourceRevision: String,
        override val recordKey: String,
        candidate: ByteArray,
        private val original: ByteArray,
        override val nativeIdentity: String? = null,
    ) : NdsStoryPendingEdit {
        private val planned = candidate.copyOf()
        init { require(revision(original) == sourceRevision) { "An edit must not mutate its source bytes" } }
        override val candidateRevision: String get() = revision(planned)
        override val candidateBytes: ByteArray get() = planned.copyOf()

        internal fun restoreInto(candidate: ByteArray): ByteArray {
            require(candidate.contentEquals(planned)) { "Candidate no longer matches the planned revision" }
            return original.copyOf()
        }
    }

    /**
     * Clears every identity reference of one individual, mirroring the game's own delete routine
     * (`FUN_020ae0ac`): party slots are zeroed in place, farm residents and the second farm list are
     * compacted by shifting later entries down, and the team container is zeroed. The current
     * partner reference is never silently dropped - [removeRecord] refuses to move the partner.
     */
    private fun clearIdentity(bytes: ByteArray, base: Int, identity: Long) {
        for (slot in 0 until LostEvolution.PARTY_SLOTS) {
            val at = base + LostEvolution.PARTY_SAVE_BASE + slot * 8
            if (LostEvolution.u64(bytes, at) == identity) LostEvolution.putU64(bytes, at, 0)
        }
        val farmCount = LostEvolution.u32(bytes, base + LostEvolution.FARM_SAVE_BASE)
            .toInt().coerceIn(0, LostEvolution.FARM_GROUPS_MAX)
        for (group in 0 until farmCount) {
            val groupBase = base + LostEvolution.FARM_SAVE_BASE + 4 + group * LostEvolution.FARM_GROUP_STRIDE
            val residents = (0 until LostEvolution.FARM_RESIDENTS).map { slot ->
                LostEvolution.u64(bytes, groupBase + LostEvolution.FARM_RESIDENT_OFFSET + slot * 8)
            }.toMutableList()
            val index = residents.indexOf(identity)
            if (index >= 0) {
                for (slot in index until LostEvolution.FARM_RESIDENTS - 1) residents[slot] = residents[slot + 1]
                residents[LostEvolution.FARM_RESIDENTS - 1] = 0L
                for (slot in 0 until LostEvolution.FARM_RESIDENTS) {
                    LostEvolution.putU64(bytes,
                        groupBase + LostEvolution.FARM_RESIDENT_OFFSET + slot * 8, residents[slot])
                }
            }
        }
        val visitorsBase = base + LostEvolution.FARM_VISITORS_SAVE_BASE
        val entries = (0 until LostEvolution.FARM_VISITORS_SLOTS).map { slot ->
            LostEvolution.u64(bytes, visitorsBase + slot * LostEvolution.FARM_VISITORS_STRIDE)
        }.toMutableList()
        val visitor = entries.indexOf(identity)
        if (visitor >= 0) {
            for (slot in visitor until LostEvolution.FARM_VISITORS_SLOTS - 1) entries[slot] = entries[slot + 1]
            entries[LostEvolution.FARM_VISITORS_SLOTS - 1] = 0L
            for (slot in 0 until LostEvolution.FARM_VISITORS_SLOTS) {
                val at = visitorsBase + slot * LostEvolution.FARM_VISITORS_STRIDE
                LostEvolution.putU64(bytes, at, entries[slot])
                val marker = at + 0xA
                bytes[marker] = if (entries[slot] == 0L) LostEvolution.FARM_VISITORS_EMPTY.toByte() else 1
            }
        }
        for (slot in 0 until LostEvolution.TEAM_SLOTS) {
            val at = base + LostEvolution.TEAM_SAVE_BASE + slot * 8
            if (LostEvolution.u64(bytes, at) == identity) LostEvolution.putU64(bytes, at, 0)
        }
    }

    /**
     * Places a sent or admitted individual into the destination the player asked for, mirroring the
     * engine's own add rules: party `FUN_020afce8` (six members, active slots searched first, then
     * the reserve slots), farm `FUN_020ea928` (first empty resident of a group) and the third
     * assignment container at player +0x157C `FUN_020eb3e0` (first empty of eight). The default
     * (null) keeps the historical farm-first policy.
     */
    private fun placeIdentity(bytes: ByteArray, base: Int, identity: Long,
        requested: SaveContainer?): SaveContainer = when (requested) {
        null -> placeFarmFirst(bytes, base, identity)
        SaveContainer.FARM -> placeFarm(bytes, base, identity)
        SaveContainer.PARTY -> placeParty(bytes, base, identity)
        SaveContainer.BANK -> placeBank(bytes, base, identity)
        else -> throw IllegalArgumentException("Unsupported Lost Evolution destination: $requested")
    }

    private fun placeFarmFirst(bytes: ByteArray, base: Int, identity: Long): SaveContainer {
        try {
            return placeFarm(bytes, base, identity)
        } catch (_: IllegalStateException) {
            return placeParty(bytes, base, identity)
        }
    }

    private fun placeFarm(bytes: ByteArray, base: Int, identity: Long): SaveContainer {
        val farmCount = LostEvolution.u32(bytes, base + LostEvolution.FARM_SAVE_BASE)
            .toInt().coerceIn(0, LostEvolution.FARM_GROUPS_MAX)
        for (group in 0 until farmCount) {
            val groupBase = base + LostEvolution.FARM_SAVE_BASE + 4 + group * LostEvolution.FARM_GROUP_STRIDE
            for (slot in 0 until LostEvolution.FARM_RESIDENTS) {
                val at = groupBase + LostEvolution.FARM_RESIDENT_OFFSET + slot * 8
                if (LostEvolution.u64(bytes, at) == 0L) {
                    LostEvolution.putU64(bytes, at, identity)
                    return SaveContainer.FARM
                }
            }
        }
        throw IllegalStateException("Every farm slot is full")
    }

    private fun placeBank(bytes: ByteArray, base: Int, identity: Long): SaveContainer {
        for (slot in 0 until LostEvolution.TEAM_SLOTS) {
            val at = base + LostEvolution.TEAM_SAVE_BASE + slot * 8
            if (LostEvolution.u64(bytes, at) == 0L) {
                LostEvolution.putU64(bytes, at, identity)
                return SaveContainer.BANK
            }
        }
        throw IllegalStateException("The bank (eight slots) is full")
    }

    private fun placeParty(bytes: ByteArray, base: Int, identity: Long): SaveContainer {
        // Party placement follows the engine's own insert rule (FUN_020afce8): six members at most,
        // three active slots (0..4) and three reserve slots (5..7), with an active-first search.
        fun partySlot(slot: Int): Long =
            LostEvolution.u64(bytes, base + LostEvolution.PARTY_SAVE_BASE + slot * 8)
        var active = 0
        for (slot in 0 until 5) if (partySlot(slot) != 0L) active++
        var reserve = 0
        for (slot in 5 until LostEvolution.PARTY_SLOTS) if (partySlot(slot) != 0L) reserve++
        if (active + reserve < 6) {
            val range = if (active < 3) 0 until 5 else if (reserve < 3) 5 until LostEvolution.PARTY_SLOTS else null
            if (range != null) {
                for (slot in range) {
                    if (partySlot(slot) == 0L) {
                        LostEvolution.putU64(bytes,
                            base + LostEvolution.PARTY_SAVE_BASE + slot * 8, identity)
                        return SaveContainer.PARTY
                    }
                }
            }
        }
        throw IllegalStateException("The party is full")
    }

    /**
     * Takes the next identity from the game's own counter at player-state +0x4070 and advances the
     * counter by one, exactly the allocator the create path calls (FUN_020ad35c).
     */
    private fun allocateIdentity(bytes: ByteArray, base: Int, used: Set<Long>): Long {
        val counter = LostEvolution.u64(bytes, base + LostEvolution.NEXT_ID_SAVE_OFFSET)
        var low = counter and 0xFFFFFFFFL
        val high = if ((counter ushr 32) != 0L) (counter ushr 32) else LostEvolution.IDENTITY_MARKER
        while (true) {
            val candidate = (high shl 32) or low
            if (candidate != 0L && candidate !in used) {
                val nextLow = (low + 1) and 0xFFFFFFFFL
                LostEvolution.putU64(bytes, base + LostEvolution.NEXT_ID_SAVE_OFFSET,
                    (high shl 32) or if (nextLow == 0L) 1 else nextLow)
                return candidate
            }
            low = (low + 1) and 0xFFFFFFFFL
            if (low == 0L) low = 1
        }
    }

    /**
     * Removes one individual from the authoritative copy: empties its 0x78 record slot and clears
     * every identity reference (party, farm groups, team, current partner). Refuses to remove the
     * last remaining individual.
     */
    fun removeRecord(source: ByteArray, observedRevision: String, recordKey: String): Edit {
        require(revision(source) == observedRevision) { "Source changed; read it again before planning a move" }
        val observation = LostEvolutionSaveReader().read(source)
        val record = requireNotNull(observation.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        require(record.container != SaveContainer.BANK) { "Bank records are inspection-only" }
        require(record.issues.none { it != RecordIssue.AMBIGUOUS_SAVE_BLOCK && it != RecordIssue.UNASSIGNED_LOCATION }) {
            "Records that need review cannot move"
        }
        val identity = requireNotNull(LostEvolution.parseIdentityText(record.nativeIdentity)) {
            "The record has no native identity"
        }
        val copy = authority(observation)
        val base = copyBase(observation, copy)
        require(LostEvolution.u64(source, base + LostEvolution.PARTNER_SAVE_OFFSET) != identity) {
            "The current partner cannot leave the game; change it in-game first"
        }
        val sameCopy = observation.records.filter { it.nativeParameters?.saveBlock == copy }
        require(sameCopy.count { LostEvolution.parseIdentityText(it.nativeIdentity) != identity } >= 1) {
            "The last individual in the save cannot move"
        }
        val slot = (0 until LostEvolution.RECORD_COUNT).firstOrNull { index ->
            LostEvolution.identity(source.copyOfRange(recordBase(observation, copy, index),
                recordBase(observation, copy, index) + LostEvolution.RECORD_BYTES)) == identity
        } ?: throw IllegalArgumentException("The individual is not in the authoritative copy")
        val candidate = source.copyOf()
        candidate.fill(0, recordBase(observation, copy, slot),
            recordBase(observation, copy, slot) + LostEvolution.RECORD_BYTES)
        clearIdentity(candidate, base, identity)
        finalize(candidate, observation, copy)
        val after = LostEvolutionSaveReader().read(candidate)
        val afterCopy = after.records.filter { it.nativeParameters?.saveBlock == copy }
        check(afterCopy.none { it.nativeIdentity == record.nativeIdentity }) { "Removal left the individual in place" }
        check(afterCopy.size == sameCopy.size - 1) { "Removal changed an unrelated individual" }
        return Edit(observedRevision, recordKey, candidate, source, record.nativeIdentity)
    }

    /** Inserts exact retained record bytes (identity kept) or a generated record (identity assigned). */
    fun insertRecord(destination: ByteArray, observedRevision: String, recordBase64: String,
        assignIdentity: Boolean, container: SaveContainer?): Edit {
        require(revision(destination) == observedRevision) { "Destination changed; read it again" }
        val native = Base64.getDecoder().decode(recordBase64)
        require(native.size == LostEvolution.RECORD_BYTES) { "A record is exactly 0x78 bytes" }
        val species = LostEvolution.species(native)
        require(species in 0 until LostEvolution.SPECIES_COUNT) { "Species outside the Lost Evolution range" }
        require(LostEvolution.level(native) in 1..LostEvolution.MAX_LEVEL) { "Level outside 1..99" }
        val observation = LostEvolutionSaveReader().read(destination)
        val copy = authority(observation)
        val base = copyBase(observation, copy)
        val used = (0 until LostEvolution.RECORD_COUNT).mapNotNull { index ->
            val value = LostEvolution.identity(destination.copyOfRange(recordBase(observation, copy, index),
                recordBase(observation, copy, index) + LostEvolution.RECORD_BYTES))
            value.takeIf { it != 0L }
        }.toSet()
        var identity = LostEvolution.identity(native)
        if (!assignIdentity && identity != 0L) {
            require(identity !in used) { "That identity already exists in the authoritative copy" }
        }
        val slot = (0 until LostEvolution.RECORD_COUNT).firstOrNull { index ->
            LostEvolution.identity(destination.copyOfRange(recordBase(observation, copy, index),
                recordBase(observation, copy, index) + LostEvolution.RECORD_BYTES)) == 0L
        } ?: throw IllegalStateException("The record table is full")
        val candidate = destination.copyOf()
        if (assignIdentity || identity == 0L) {
            // Advance the game's own counter on the candidate; the caller's bytes stay untouched.
            identity = allocateIdentity(candidate, base, used)
        } else {
            // A retained identity may be at or past the counter (for example after the game saved
            // over an earlier write); keep the game's allocator ahead of every identity in use.
            val counter = LostEvolution.u64(candidate, base + LostEvolution.NEXT_ID_SAVE_OFFSET)
            val counterLow = counter and 0xFFFFFFFFL
            val identityLow = identity and 0xFFFFFFFFL
            if (identityLow >= counterLow && identityLow < 0xFFFFFFFFL) {
                val high = if ((counter ushr 32) != 0L) (counter ushr 32) else LostEvolution.IDENTITY_MARKER
                LostEvolution.putU64(candidate, base + LostEvolution.NEXT_ID_SAVE_OFFSET,
                    (high shl 32) or (identityLow + 1))
            }
        }
        // Stamp every identity window (and repair legacy records whose marker/tail windows were
        // never initialized) exactly like a game-created record.
        LostEvolution.stampIdentity(native, identity)
        native.copyInto(candidate, recordBase(observation, copy, slot))
        // The engine's create path registers the species in the encyclopedia book; a promoted record
        // that skipped it never shows in the game's species list.
        LostEvolution.registerSpecies(candidate, base, species.toInt())
        val placed = placeIdentity(candidate, base, identity, container)
        finalize(candidate, observation, copy)
        val after = LostEvolutionSaveReader().read(candidate)
        val inserted = requireNotNull(after.records.singleOrNull {
            it.nativeParameters?.saveBlock == copy && it.nativeIdentity == LostEvolution.identityText(identity)
        }) { "The inserted record did not read back" }
        check(inserted.container == placed) { "The inserted record is not where it was placed" }
        return Edit(observedRevision, inserted.recordKey, candidate, destination, inserted.nativeIdentity)
    }

    fun insertRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer? = null): Edit =
        insertRecord(destination, observedRevision, recordBase64, assignIdentity = false, container = container)

    fun insertGeneratedRecord(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer? = null): Edit =
        insertRecord(destination, observedRevision, recordBase64, assignIdentity = true, container = container)

    /** Replaces one existing record with exact bytes that keep the same identity. */
    fun replaceRetainedRecord(destination: ByteArray, observedRevision: String, recordBase64: String): Edit {
        require(revision(destination) == observedRevision) { "Destination changed; read it again" }
        val native = Base64.getDecoder().decode(recordBase64)
        require(native.size == LostEvolution.RECORD_BYTES) { "A record is exactly 0x78 bytes" }
        val identity = LostEvolution.identity(native)
        require(identity != 0L) { "A replacement record carries its native identity" }
        val observation = LostEvolutionSaveReader().read(destination)
        val copy = authority(observation)
        val slot = (0 until LostEvolution.RECORD_COUNT).firstOrNull { index ->
            LostEvolution.identity(destination.copyOfRange(recordBase(observation, copy, index),
                recordBase(observation, copy, index) + LostEvolution.RECORD_BYTES)) == identity
        } ?: throw IllegalArgumentException("The record is not in the authoritative copy")
        val candidate = destination.copyOf()
        native.copyInto(candidate, recordBase(observation, copy, slot))
        finalize(candidate, observation, copy)
        val after = LostEvolutionSaveReader().read(candidate)
        check(after.records.any {
            it.nativeParameters?.saveBlock == copy && it.nativeIdentity == LostEvolution.identityText(identity)
        }) { "The replacement did not read back" }
        return Edit(observedRevision, "copy:$copy:record:$slot", candidate, destination,
            LostEvolution.identityText(identity))
    }

    /** Gives an existing individual a party slot (moving it out of a farm group when needed). */
    fun admitToParty(source: ByteArray, observedRevision: String, recordKey: String): Edit {
        require(revision(source) == observedRevision) { "Source changed; read it again" }
        val observation = LostEvolutionSaveReader().read(source)
        val record = requireNotNull(observation.records.singleOrNull { it.recordKey == recordKey }) { "No such native record" }
        val identity = requireNotNull(LostEvolution.parseIdentityText(record.nativeIdentity)) {
            "The record has no native identity"
        }
        val copy = authority(observation)
        val base = copyBase(observation, copy)
        require(record.container != SaveContainer.PARTY) { "The individual is already in the party" }
        val free = (0 until LostEvolution.PARTY_SLOTS).firstOrNull { slot ->
            LostEvolution.u64(source, base + LostEvolution.PARTY_SAVE_BASE + slot * 8) == 0L
        } ?: throw IllegalStateException("The party is full")
        val candidate = source.copyOf()
        LostEvolution.putU64(candidate, base + LostEvolution.PARTY_SAVE_BASE + free * 8, identity)
        finalize(candidate, observation, copy)
        val after = LostEvolutionSaveReader().read(candidate)
        check(after.records.any {
            it.nativeParameters?.saveBlock == copy && it.nativeIdentity == record.nativeIdentity &&
                it.container == SaveContainer.PARTY
        }) { "The admission did not take effect" }
        return Edit(observedRevision, recordKey, candidate, source, record.nativeIdentity)
    }

    fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray = when (edit) {
        is Edit -> edit.restoreInto(candidate)
        else -> throw IllegalArgumentException("Edit does not belong to the Lost Evolution layout")
    }
}

/** Registered native editor seam for Lost Evolution. */
object LostEvolutionSaveEditor : NdsStorySaveEditor {
    override val profile: NdsStoryProfile get() = NdsStoryProfiles.LOST_EVOLUTION

    override fun read(bytes: ByteArray): SaveObservation = LostEvolutionSaveReader().read(bytes)

    override fun removeRecord(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit =
        LostEvolutionSaveEdits.removeRecord(source, observedRevision, recordKey)

    override fun insertRetained(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer?): NdsStoryPendingEdit =
        LostEvolutionSaveEdits.insertRetainedRecord(destination, observedRevision, recordBase64, container)

    override fun insertGenerated(destination: ByteArray, observedRevision: String, recordBase64: String,
        container: SaveContainer?): NdsStoryPendingEdit =
        LostEvolutionSaveEdits.insertGeneratedRecord(destination, observedRevision, recordBase64, container)

    override fun replaceRetained(destination: ByteArray, observedRevision: String, recordBase64: String): NdsStoryPendingEdit =
        LostEvolutionSaveEdits.replaceRetainedRecord(destination, observedRevision, recordBase64)

    override fun admitToParty(source: ByteArray, observedRevision: String, recordKey: String): NdsStoryPendingEdit =
        LostEvolutionSaveEdits.admitToParty(source, observedRevision, recordKey)

    override fun restore(candidate: ByteArray, edit: NdsStoryPendingEdit): ByteArray =
        LostEvolutionSaveEdits.restore(candidate, edit)
}
