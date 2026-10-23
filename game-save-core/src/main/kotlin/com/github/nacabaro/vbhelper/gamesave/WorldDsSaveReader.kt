package com.github.nacabaro.vbhelper.gamesave

/**
 * Read-only World DS profile. Any structurally recognizable save is reported: implausible values, missing
 * name terminators and dangling references become record review issues instead of whole-file rejection, so
 * every game-progress state stays browsable. Blocks are identified by the game's own invariants — the
 * written DSDIGI signature at +0x10, or an id allocator plus record table and bookkeeping references that
 * match the game's code — never by header byte guesses. Bare 0x7DC0 save regions, raw 64 KiB images and the same images behind small
 * export headers or emulator trailers are unwrapped before reading. See game-save-core/SAVE_READING_STANDARD.md
 * and WORLD_DS_RESEARCH.md for the decompilation evidence.
 */
class WorldDsSaveReader : GameSaveReader {
    override val gameId: String = GAME_ID
    override val maxInputBytes: Int = FILE_BYTES + WRAPPER_SLACK_BYTES + WorldDsResearchInspector.DESMUME_FOOTER_BYTES
    override fun acceptsLength(length: Int): Boolean = length in SAVE_BYTES..maxInputBytes

    override fun read(bytes: ByteArray): SaveObservation {
        val contentEnd = WorldDsResearchInspector.desmumePayloadStart(bytes) ?: bytes.size
        var imageBase = -1
        var resolved: List<DsResearchBlock>? = null
        var lastFailure: SaveReadException? = null
        for (base in candidateImageBases(contentEnd)) {
            val found = try { collectBlocks(bytes, base, contentEnd) }
            catch (error: SaveReadException) { lastFailure = error; emptyList() }
            if (found.isNotEmpty()) { imageBase = base; resolved = found; break }
        }
        val blocks = resolved ?: throw (lastFailure ?: SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT))
        val ambiguous = blocks.size > 1
        val copyIntegrity = blocks.associate { block ->
            (block.offset - imageBase) / COPY_STRIDE + 1 to copyHeaderValid(bytes, block.offset)
        }
        val records = blocks.flatMap { block ->
            block.records.map { native ->
            val decodedName = WorldDsText.decode(native.nameCodes)
            val lastId = u32(bytes, block.offset + 0x208)
            val saveSequence = u32(bytes, block.offset + 0x18)
            val slot = (block.offset - imageBase) / COPY_STRIDE + 1
            val issues = buildList {
                native.issues.forEach { add(when (it) {
                    DsResearchIssue.DUPLICATE_LOCAL_ID -> RecordIssue.DUPLICATE_IDENTITY
                    DsResearchIssue.CONFLICTING_LOCATION -> RecordIssue.CONFLICTING_LOCATION
                    DsResearchIssue.GUEST -> RecordIssue.GUEST_RECORD
                    DsResearchIssue.LENT -> RecordIssue.LENT_RECORD
                    DsResearchIssue.EGG -> RecordIssue.EGG_RECORD
                    DsResearchIssue.OTHER_DS_RECORD -> RecordIssue.FOREIGN_RECORD
                }) }
                if (native.location == DsResearchLocation.FARM_REFERENCE) add(RecordIssue.FARM_OWNERSHIP_UNVERIFIED)
                if (native.location == DsResearchLocation.UNASSIGNED) add(RecordIssue.UNASSIGNED_LOCATION)
                if (decodedName.contains('\uFFFD')) add(RecordIssue.UNKNOWN_NAME_CODES)
                if (!isPlausible(native, lastId)) add(RecordIssue.IMPLAUSIBLE_VALUES)
                if (ambiguous) add(RecordIssue.AMBIGUOUS_SAVE_BLOCK)
            }
            val stats = native.stats
            val container = when (native.location) {
                DsResearchLocation.PARTY_REFERENCE -> SaveContainer.PARTY
                DsResearchLocation.FARM_REFERENCE -> SaveContainer.FARM
                else -> SaveContainer.UNASSIGNED
            }
            val position = if (container == SaveContainer.PARTY) {
                (0 until 10).first { index -> u32(bytes, block.offset + 0x20C + index * 4) == native.localId } + 1
            } else native.index + 1
            val recordBase = block.offset + 0x234 + native.index * RECORD_BYTES
            val previousSpeciesIds = (0 until FORM_SLOTS).map { index ->
                u32(bytes, recordBase + 0xA4 + index * 4).toInt().toLong()
            }.filter { it in 0L until SPECIES_COUNT.toLong() && it != native.nativeSpeciesId }
            ObservedDigimon("copy:$slot:record:${native.index}", container,
                position, native.nativeSpeciesId, decodedName, native.level, personalityId = null,
                identityCandidate = 0, membership = if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
                issues = issues, totalExp = null, talentRaw = null, bondRaw = null,
                previousSpeciesIds = previousSpeciesIds,
                nativeRecordBase64 = native.nativeRecordBase64, nativeIdentity = "save-local:${native.localId}",
                nativeParameters = NativeStatParameters(stats.hp, stats.maxHp, stats.mp, stats.maxMp,
                    stats.attack, stats.defense, stats.spirit, stats.speed, native.aptitude, native.expCounters,
                    slot, saveSequence))
        } }
        return SaveObservation(GAME_ID, PROFILE, 1, revision(bytes), records,
            buildList {
                add(ObservationNotice.SAVE_INTEGRITY_UNVERIFIED)
                add(ObservationNotice.SPECIES_MAPPING_PENDING)
                add(ObservationNotice.IDENTITY_RESEARCH_PENDING)
                if (ambiguous) add(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED)
            }, SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true), copyIntegrity, imageBase)
    }

    private fun copyHeaderValid(bytes: ByteArray, offset: Int): Boolean {
        if (offset < 0 || offset + SAVE_BYTES > bytes.size) return false
        val copy = bytes.copyOfRange(offset, offset + SAVE_BYTES)
        val stored = copy.copyOfRange(0, 16)
        copy.fill(0, 0, 16)
        val digest = java.security.MessageDigest.getInstance("MD5").digest(copy)
        copy.fill(0)
        return digest.contentEquals(stored)
    }

    /**
     * Container families: the canonical 64 KiB image (the two card copies back-to-back at 0x0 and 0x7E00),
     * the bare 0x7DC0 save region the game writes per copy, and the same content behind a small wrapper
     * (export headers, emulator trailers already stripped). A wrapped image slides a bounded, aligned scan
     * so no particular header length is assumed; any other total size is a container failure, never a
     * silent scan of an unrelated file.
     */
    private fun candidateImageBases(content: Int): List<Int> {
        if (content == FILE_BYTES || content == SAVE_BYTES) return listOf(0)
        val slack = when {
            content > FILE_BYTES && content <= FILE_BYTES + WRAPPER_SLACK_BYTES -> content - FILE_BYTES
            content > SAVE_BYTES && content <= SAVE_BYTES + WRAPPER_SLACK_BYTES -> content - SAVE_BYTES
            else -> throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
        }
        val bases = (0..slack step IMAGE_STEP).toMutableList()
        if (bases.last() != slack) bases += slack
        return bases
    }

    private fun collectBlocks(bytes: ByteArray, base: Int, contentEnd: Int): List<DsResearchBlock> =
        listOf(0, COPY_STRIDE).mapNotNull { copyOffset ->
            val start = base + copyOffset
            if (start < 0 || start + SAVE_BYTES > contentEnd) return@mapNotNull null
            val copy = bytes.copyOfRange(start, start + SAVE_BYTES)
            try {
                if (copy.all { it == 0.toByte() } || copy.all { it == 0xFF.toByte() }) null
                else readIdentifiedBlock(bytes, start)
            } finally { copy.fill(0) }
        }

    /** A blank or shapeless copy is "not a save block"; only the block identification itself is strict. */
    private fun readIdentifiedBlock(bytes: ByteArray, base: Int): DsResearchBlock {
        if (!identifiesSaveBlock(bytes, base)) throw SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT)
        return WorldDsResearchInspector.readBlock(bytes, base)
    }

    /**
     * Game-grounded identification, never header byte guesses. The matching decompilation shows takeNextId
     * hands out one monotonic sequence of ids from a per-save random starting value: every observed save
     * (0xACA7BA00 and 0x37720000 starts, counts in the low bytes, crossing byte boundaries as they grow)
     * confirms it. Ids are never 0 (nor -1, an empty slot) and never reach the game's reserved bound, and
     * the game's own bookkeeping — party slots, farm residents, Digimon items, the unk443C reference —
     * names those ids. An unsigned block is recognized when the allocator is sane and at least one of
     * those references names a live record: unrelated bytes cannot reproduce the game's cross-references.
     * The game's written signature (DSDIGI at +0x10) confirms the block outright once the allocator
     * looks sane.
     */
    private fun identifiesSaveBlock(bytes: ByteArray, base: Int): Boolean {
        val lastId = u32(bytes, base + 0x208)
        if (WorldDsResearchInspector.hasSaveSignature(bytes, base + 0x10)) return lastId in 1 until ID_RESERVED
        if (lastId !in 1 until ID_RESERVED) return false
        val live = liveRecordIds(bytes, base)
        if (live.isEmpty()) return false
        return referencesRecordId(bytes, base, live)
    }

    /** Live Digimon records: the game's own counting treats 0 and -1 as empty slots (func_0209ff28). */
    private fun liveRecordIds(bytes: ByteArray, base: Int): Set<Long> {
        val live = mutableSetOf<Long>()
        for (slot in 0 until RECORD_COUNT) {
            val id = u32(bytes, base + RECORD_BASE + slot * RECORD_BYTES)
            if (id != 0L && id != WorldDsResearchInspector.EMPTY_RECORD_ID) live += id
        }
        return live
    }

    /**
     * The game's id-bearing bookkeeping that references Digimon records: the party array (data_021db804),
     * farm residents and each farm's last-visitor reference (FARMS), Digimon items (data_021df9d0) and
     * unk443C. Slots the game left empty carry 0 and are skipped.
     */
    private fun referencesRecordId(bytes: ByteArray, base: Int, live: Set<Long>): Boolean {
        fun namesRecord(at: Int): Boolean = u32(bytes, at).let { it != 0L && it in live }
        for (slot in 0 until PARTY_SLOTS) if (namesRecord(base + 0x20C + slot * 4)) return true
        for (farm in 0 until FARM_GROUPS) {
            val group = base + FARM_BASE + farm * FARM_BYTES
            for (slot in 0 until FARM_RESIDENTS) if (namesRecord(group + 0x44 + slot * 4)) return true
            if (namesRecord(group + 0x64)) return true
        }
        for (slot in 0 until DIGIMON_ITEM_SLOTS) if (namesRecord(base + ITEM_BASE + slot * ITEM_BYTES + 4)) return true
        return namesRecord(base + 0x443C)
    }

    /** Range facts observed in one save snapshot: deviations are reported, never grounds for rejection. */
    private fun isPlausible(record: DsResearchRecord, lastId: Long): Boolean {
        val egg = DsResearchIssue.EGG in record.issues
        return record.localId in 1..lastId && record.nativeSpeciesId in 0L until SPECIES_COUNT.toLong() &&
            record.level in (if (egg) 0..99 else 1..99) && record.aptitude in 0..999 &&
            record.stats.maxHp in (if (egg) 0..999999 else 1..999999) &&
            record.stats.hp in 0..record.stats.maxHp && record.stats.maxMp >= 0 &&
            record.stats.mp in 0..record.stats.maxMp &&
            listOf(record.stats.attack, record.stats.defense, record.stats.spirit, record.stats.speed).all { it >= 0 } &&
            record.expCounters.all { counter -> counter in 0L..99999L }
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, i -> value or ((bytes[at + i].toLong() and 255) shl (8 * i)) }

    companion object {
        const val GAME_ID = "digimon_world_ds_nds"
        const val PROFILE = "world-ds-usa-drastic-raw-v1"
        const val FILE_BYTES = 65536

        /** The game's species range: the USA ROM's name table holds exactly this many entries. */
        const val SPECIES_COUNT = 426

        /** The save region the game writes to the card per copy (SAVE_SIZE in the matching decompilation). */
        const val SAVE_BYTES = 0x7DC0

        /** Card stride between the two save copies: SAVE_SIZE rounded up to the card's 0x100 page
         *  (allocSaveBuffer in the matching decompilation); real saves read both copies at 0x0 and 0x7E00. */
        const val COPY_STRIDE = 0x7E00

        /** Observed header family: 64 KiB image behind a 0x7C-byte export header. */
        const val HEADER_BYTES = 0x7C

        /** Maximum recognized wrapper between a container edge and the image: bounded, content still decides. */
        const val WRAPPER_SLACK_BYTES = 0x400

        /** Wrapped images are probed on one 4-byte alignment step; header lengths are never assumed. */
        private const val IMAGE_STEP = 4

        /** From takeNextId in the matching decompilation: ids at or above this are never handed out. */
        private const val ID_RESERVED = 0xFFFFFF00L

        private const val RECORD_BASE = 0x234
        private const val RECORD_COUNT = 40
        private const val RECORD_BYTES = WorldDsResearchInspector.RECORD_BYTES
        private const val FORM_SLOTS = 5

        /** Reference arrays in the save struct (data.h): party slots, 4 farms of 0x6C and 8 Digimon items of 0x0C. */
        private const val PARTY_SLOTS = 10
        private const val FARM_BASE = 0x3E40
        private const val FARM_GROUPS = 4
        private const val FARM_BYTES = 0x6C
        private const val FARM_RESIDENTS = 8
        private const val ITEM_BASE = 0x43D8
        private const val DIGIMON_ITEM_SLOTS = 8
        private const val ITEM_BYTES = 0x0C
    }
}

/** Published USA text-code facts, not UTF-16. Unknown glyphs remain explicit and exact bytes are retained. */
internal object WorldDsText {
    fun decode(codes: List<Int>): String = buildString {
        codes.forEach { code -> append(when (code) {
            in 0x50..0x69 -> 'A' + (code - 0x50)
            in 0x6A..0x83 -> 'a' + (code - 0x6A)
            in 1..10 -> '0' + (code - 1)
            0 -> ' '; 0x0B -> '/'; 0x0C -> '%'; 0x0D -> ':'; 0x0E -> '\''; 0x0F -> '"'
            0x11 -> ','; 0x12 -> '.'; 0x13 -> '!'; 0x14 -> '?'; 0x15 -> '('; 0x16 -> ')'
            0x17 -> '+'; 0x18 -> '-'; 0x19 -> '='; 0x1B -> '~'; 0x23 -> '&'
            else -> '\uFFFD'
        }) }
    }
}
