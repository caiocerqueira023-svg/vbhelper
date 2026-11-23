package com.tamerslab.gamesave

/**
 * Generic read-only NDS Story-family profile reader. Every offset, size and count comes from
 * [profile]; World DS is the first registered profile and [WorldDsSaveReader] is its facade.
 *
 * Any structurally recognizable save is reported: implausible values, missing name terminators
 * and dangling references become record review issues instead of whole-file rejection, so every
 * game-progress state stays browsable. Blocks are identified by the profile's own invariants —
 * the game's written signature when present, or an id allocator plus record table and
 * bookkeeping references — never by header byte guesses. Bare save regions, raw card images and
 * the same images behind small export headers or emulator trailers are unwrapped before reading.
 * See game-save-core/SAVE_READING_STANDARD.md and WORLD_DS_RESEARCH.md.
 */
class NdsStorySaveReader(private val profile: NdsStoryProfile) : GameSaveReader {
    override val gameId: String = profile.gameId
    override val maxInputBytes: Int =
        profile.fileBytes + profile.wrapperSlackBytes + WorldDsResearchInspector.DESMUME_FOOTER_BYTES
    override fun acceptsLength(length: Int): Boolean = length in profile.saveBytes..maxInputBytes

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
            (block.offset - imageBase) / profile.copyStride + 1 to
                NdsStoryCopyIntegrity.isValid(bytes, block.offset, profile)
        }
        val records = blocks.flatMap { block ->
            block.records.map { native ->
                val decodedName = profile.text.decode(native.nameCodes)
                val lastId = u32(bytes, block.offset + profile.lastIdOffset)
                val saveSequence = readSequence(bytes, block.offset)
                val slot = (block.offset - imageBase) / profile.copyStride + 1
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
                    (0 until profile.partySlots).first {
                        index -> u32(bytes, block.offset + profile.partyReferenceBase + index * 4) == native.localId
                    } + 1
                } else native.index + 1
                val recordBase = block.offset + profile.recordRegion.base + native.index * profile.recordRegion.stride
                ObservedDigimon("copy:$slot:record:${native.index}", container,
                    position, native.nativeSpeciesId, decodedName, native.level, personalityId = null,
                    identityCandidate = 0, membership = if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
                    issues = issues, totalExp = null, talentRaw = null, bondRaw = null,
                    previousSpeciesIds = previousSpeciesIds(bytes, recordBase, native.nativeSpeciesId),
                    nativeRecordBase64 = native.nativeRecordBase64, nativeIdentity = "save-local:${native.localId}",
                    nativeParameters = NativeStatParameters(stats.hp, stats.maxHp, stats.mp, stats.maxMp,
                        stats.attack, stats.defense, stats.spirit, stats.speed, native.aptitude, native.expCounters,
                        slot, saveSequence))
            } }
        return SaveObservation(profile.gameId, profile.formatProfile, profile.codecVersion, revision(bytes), records,
            buildList {
                add(ObservationNotice.SAVE_INTEGRITY_UNVERIFIED)
                add(ObservationNotice.SPECIES_MAPPING_PENDING)
                add(ObservationNotice.IDENTITY_RESEARCH_PENDING)
                if (ambiguous) add(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED)
            }, SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true), copyIntegrity, imageBase)
    }

    private fun readSequence(bytes: ByteArray, copyBase: Int): Long? {
        val rule = profile.copyHeader
        val at = copyBase + rule.sequenceOffset
        if (rule.sequenceBytes !in 1..4 || at < 0 || at + rule.sequenceBytes > bytes.size) return null
        return (0 until rule.sequenceBytes).fold(0L) { value, index ->
            value or ((bytes[at + index].toLong() and 255) shl (8 * index))
        }
    }

    /** Forms this record has worn before the current one, earliest first. */
    private fun previousSpeciesIds(bytes: ByteArray, recordBase: Int, currentSpecies: Long): List<Long> {
        val layout = profile.record
        return (0 until layout.formSlots).map { index ->
            u32(bytes, recordBase + layout.offsetHistory + layout.historyForms + index * 4).toInt().toLong()
        }.filter { it in 0L until profile.speciesCount.toLong() && it != currentSpecies }
    }

    /**
     * Container families: the canonical card image (the copies back-to-back at the profile's
     * stride), the bare save region the game writes per copy, and the same content behind a small
     * wrapper (export headers, emulator trailers already stripped). A wrapped image slides a
     * bounded, aligned scan so no particular header length is assumed; any other total size is a
     * container failure, never a silent scan of an unrelated file.
     */
    private fun candidateImageBases(content: Int): List<Int> {
        if (content == profile.fileBytes || content == profile.saveBytes) return listOf(0)
        val slack = when {
            content > profile.fileBytes && content <= profile.fileBytes + profile.wrapperSlackBytes ->
                content - profile.fileBytes
            content > profile.saveBytes && content <= profile.saveBytes + profile.wrapperSlackBytes ->
                content - profile.saveBytes
            else -> throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
        }
        val bases = (0..slack step profile.imageStep).toMutableList()
        if (bases.last() != slack) bases += slack
        return bases
    }

    private fun collectBlocks(bytes: ByteArray, base: Int, contentEnd: Int): List<DsResearchBlock> =
        (0 until profile.copyCount).mapNotNull { copyIndex ->
            val start = base + copyIndex * profile.copyStride
            if (start < 0 || start + profile.saveBytes > contentEnd) return@mapNotNull null
            val copy = bytes.copyOfRange(start, start + profile.saveBytes)
            try {
                if (copy.all { it == 0.toByte() } || copy.all { it == 0xFF.toByte() }) null
                else readIdentifiedBlock(bytes, start)
            } finally { copy.fill(0) }
        }

    /** A blank or shapeless copy is "not a save block"; only the block identification itself is strict. */
    private fun readIdentifiedBlock(bytes: ByteArray, base: Int): DsResearchBlock {
        if (!identifiesSaveBlock(bytes, base)) throw SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT)
        return WorldDsResearchInspector.readBlock(bytes, base, profile)
    }

    /**
     * Game-grounded identification, never header byte guesses: the profile's written signature
     * confirms a block once its id allocator looks sane, and without a signature a block is
     * recognized only when the allocator is sane, at least one record slot carries a live id and
     * at least one piece of the game's own bookkeeping names a live record. Unrelated bytes cannot
     * reproduce those cross-references.
     */
    private fun identifiesSaveBlock(bytes: ByteArray, base: Int): Boolean {
        val lastId = u32(bytes, base + profile.lastIdOffset)
        val signature = WorldDsResearchInspector.hasSaveSignature(bytes, base + 0x10, profile)
        if (profile.requireSignature && !signature) return false
        if (signature) return lastId in 1 until profile.idReserved
        if (lastId !in 1 until profile.idReserved) return false
        val live = liveRecordIds(bytes, base)
        if (live.isEmpty()) return false
        return referencesRecordId(bytes, base, live)
    }

    /** Live Digimon records: the game's own counting treats 0 and the profile's empty id as empty. */
    private fun liveRecordIds(bytes: ByteArray, base: Int): Set<Long> {
        val live = mutableSetOf<Long>()
        val region = profile.recordRegion
        for (slot in 0 until region.count) {
            val id = u32(bytes, base + region.base + slot * region.stride)
            if (id != 0L && id != profile.emptyRecordId) live += id
        }
        return live
    }

    /** The id-bearing bookkeeping that references Digimon records: party, farms, items and the misc reference. */
    private fun referencesRecordId(bytes: ByteArray, base: Int, live: Set<Long>): Boolean {
        fun namesRecord(at: Int): Boolean = u32(bytes, at).let { it != 0L && it in live }
        for (slot in 0 until profile.partySlots) if (namesRecord(base + profile.partyReferenceBase + slot * 4)) return true
        profile.farm?.let { farm ->
            for (group in 0 until farm.groups) {
                val groupBase = base + farm.base + group * farm.stride
                for (slot in 0 until farm.residents) if (namesRecord(groupBase + farm.residentOffset + slot * 4)) return true
                if (namesRecord(groupBase + farm.lastVisitorOffset)) return true
            }
        }
        profile.item?.let { item ->
            for (slot in 0 until item.slots) if (namesRecord(base + item.base + slot * item.stride + item.idOffset)) return true
        }
        val misc = profile.miscReferenceOffset ?: return false
        return namesRecord(base + misc)
    }

    /** Range facts observed in one save snapshot: deviations are reported, never grounds for rejection. */
    private fun isPlausible(record: DsResearchRecord, lastId: Long): Boolean {
        val layout = profile.record
        val egg = DsResearchIssue.EGG in record.issues
        val stats = record.stats
        return record.localId in 1..lastId && record.nativeSpeciesId in 0L until profile.speciesCount.toLong() &&
            record.level in (if (egg) 0..99 else 1..99) && record.aptitude in 0..999 &&
            stats.maxHp in (if (egg) 0..999999 else 1..999999) &&
            stats.hp in 0..stats.maxHp && stats.maxMp >= 0 &&
            stats.mp in 0..stats.maxMp &&
            listOf(stats.attack, stats.defense, stats.spirit, stats.speed).all { it >= 0 } &&
            record.expCounters.size == layout.expChannels && record.expCounters.all { counter -> counter in 0L..99999L }
    }

    private fun u32(bytes: ByteArray, at: Int): Long =
        (0 until 4).fold(0L) { value, i -> value or ((bytes[at + i].toLong() and 255) shl (8 * i)) }
}
