package com.tamerslab.gamesave

import java.io.InputStream
import java.util.Base64

enum class DsResearchContainer { RAW_CANDIDATE, DESMUME_DSV_V0 }
enum class DsResearchLocation { PARTY_REFERENCE, FARM_REFERENCE, UNASSIGNED, CONFLICT }
enum class DsResearchIssue { DUPLICATE_LOCAL_ID, CONFLICTING_LOCATION, GUEST, LENT, EGG, OTHER_DS_RECORD }

data class DsNativeStats(val hp: Int, val maxHp: Int, val mp: Int, val maxMp: Int,
    val attack: Int, val defense: Int, val spirit: Int, val speed: Int)

/** Local record IDs and 16-bit name codes are not universal identity or decoded Unicode. */
data class DsResearchRecord(
    val index: Int,
    val localId: Long,
    val nativeSpeciesId: Long,
    val nameCodes: List<Int>,
    val level: Int,
    val aptitude: Int,
    val stats: DsNativeStats,
    val expCounters: List<Long>,
    val location: DsResearchLocation,
    val issues: Set<DsResearchIssue>,
    val nativeRecordBase64: String,
)

/** Each signature candidate is separate: checksum/sequence/active-copy selection is not validated. */
data class DsResearchBlock(val offset: Int, val records: List<DsResearchRecord>, val danglingReferences: Int)

data class DsResearchInspection(
    val container: DsResearchContainer,
    val contentRevision: String,
    val payloadRevision: String,
    val payloadBytes: Int,
    val blocks: List<DsResearchBlock>,
    /** Title/layout evidence label printed instead of claiming a region. */
    val layoutEvidence: String = "ADNE",
) {
    fun summary(): List<String> = buildList {
        add("research_only=true layout_evidence=$layoutEvidence container=${container.name.lowercase()} payload_bytes=$payloadBytes blocks=${blocks.size}")
        blocks.forEachIndexed { index, block ->
            add("block=$index records=${block.records.size} party_references=${block.records.count { it.location == DsResearchLocation.PARTY_REFERENCE }} farm_references=${block.records.count { it.location == DsResearchLocation.FARM_REFERENCE }} unassigned=${block.records.count { it.location == DsResearchLocation.UNASSIGNED }} conflicts=${block.records.count { it.location == DsResearchLocation.CONFLICT }} dangling_references=${block.danglingReferences}")
            add("block=$index flagged_records=${block.records.count { it.issues.isNotEmpty() }}")
        }
        add("identity=unverified region=unverified writes=disabled; no active block selected; no import or progress receipts")
    }
}

/**
 * Independent, opt-in structural research reader driven by a registered [NdsStoryProfile].
 * Not a GameSaveReader: no title/region recognition, ownership policy, checksum or import is claimed.
 * Sources and exact evidence boundaries: WORLD_DS_RESEARCH.md / DAWN_DUSK_TRANSFER_PLAN.md.
 * The default profile keeps the World DS entry point that existing callers and evidence use.
 */
object WorldDsResearchInspector {
    const val SAVE_BLOCK_BYTES = 0x7DC0
    const val RECORD_BYTES = 0x180
    private const val MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
    private const val MAX_BLOCKS = 32

    /** The trailer both DeSmuME and DraStic append: 24 metadata bytes and a 16-byte cookie. */
    internal const val BINARY_FOOTER_BYTES = 24 + 16

    /** From the game's own counting code (func_0209ff28): an id of 0 or -1 is not a live record. */
    internal const val EMPTY_RECORD_ID = 0xFFFFFFFFL
    private val footerNotice = "|<--Snip above here to create a raw sav by excluding this DeSmuME savedata footer:".toByteArray(Charsets.US_ASCII)
    private val footerCookie = "|-DESMUME SAVE-|".toByteArray(Charsets.US_ASCII)
    internal val DESMUME_FOOTER_BYTES = footerNotice.size + 24 + footerCookie.size
    val MAX_INPUT_BYTES: Int = NdsStoryProfiles.all.maxOf {
        it.fileBytes + it.wrapperSlackBytes + DESMUME_FOOTER_BYTES
    }.coerceAtLeast(MAX_PAYLOAD_BYTES + DESMUME_FOOTER_BYTES)

    fun inspect(input: InputStream, profile: NdsStoryProfile = NdsStoryProfiles.WORLD_DS): DsResearchInspection {
        val bytes = readBoundedSaveBytes(input, MAX_INPUT_BYTES)
        return try { inspect(bytes, profile) } finally { bytes.fill(0) }
    }

    fun inspect(bytes: ByteArray, profile: NdsStoryProfile = NdsStoryProfiles.WORLD_DS): DsResearchInspection {
        if (bytes.size > MAX_INPUT_BYTES) throw SaveReadException(SaveReadFailure.INPUT_TOO_LARGE)
        if (matches(bytes, 0, "NocashGbaBackupMediaSavDataFile".toByteArray(Charsets.US_ASCII)))
            throw SaveReadException(SaveReadFailure.UNSUPPORTED_FORMAT)
        val payloadStart = desmumePayloadStart(bytes)
        val wrapped = payloadStart != null
        val payloadSize = payloadStart ?: bytes.size
        if (payloadSize > MAX_PAYLOAD_BYTES) throw SaveReadException(SaveReadFailure.INPUT_TOO_LARGE)
        val offsets = mutableListOf<Int>()
        // Alignment is deliberately only structural. Several matching blocks never imply a newest copy.
        var offset = 0
        while (offset <= payloadSize - profile.saveBytes) {
            if (hasSaveSignature(bytes, offset + 0x10, profile)) {
                if (offsets.size == MAX_BLOCKS) throw SaveReadException(SaveReadFailure.AMBIGUOUS_FORMAT)
                offsets += offset
            }
            offset += profile.imageStep
        }
        if (offsets.isEmpty()) throw SaveReadException(SaveReadFailure.UNRECOGNIZED_LAYOUT)
        val payload = bytes.copyOfRange(0, payloadSize)
        return try {
            DsResearchInspection(if (wrapped) DsResearchContainer.DESMUME_DSV_V0 else DsResearchContainer.RAW_CANDIDATE,
                revision(bytes), revision(payload), payloadSize, offsets.map { readBlock(payload, it, profile) },
                profile.layoutEvidence)
        } finally { payload.fill(0) }
    }

    /**
     * Raw-payload start for a DeSmuME/DraStic trailer, or null when the file carries no footer.
     * The footer is the last 40 bytes (24 metadata + cookie) for both emulators; DeSmuME additionally
     * writes its snip notice text between the payload and the footer, and the metadata's padded size
     * (at footer+4) is where the payload ends either way. A recognized but malformed footer is a
     * container failure, never a silent raw fallback.
     */
    internal fun desmumePayloadStart(bytes: ByteArray): Int? {
        val hasCookie = matches(bytes, bytes.size - footerCookie.size, footerCookie)
        if (!hasCookie) {
            if (matches(bytes, bytes.size - BINARY_FOOTER_BYTES - footerNotice.size, footerNotice))
                throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
            return null
        }
        val meta = bytes.size - BINARY_FOOTER_BYTES
        val padded = if (meta >= 0) u32(bytes, meta + 4) else -1L
        if (meta < 0 || u32(bytes, meta + 20) != 0L || padded < 1 || padded > meta.toLong() || u32(bytes, meta) > padded)
            throw SaveReadException(SaveReadFailure.INVALID_CONTAINER)
        return padded.toInt()
    }

    /** The profile's game-written save signature, when it has one; corroboration, never the rule. */
    internal fun hasSaveSignature(bytes: ByteArray, offset: Int, profile: NdsStoryProfile = NdsStoryProfiles.WORLD_DS): Boolean {
        val expected = profile.signature?.toByteArray() ?: return false
        return matches(bytes, offset, expected)
    }

    internal fun readBlock(bytes: ByteArray, base: Int, profile: NdsStoryProfile = NdsStoryProfiles.WORLD_DS): DsResearchBlock {
        val layout = profile.record
        val region = profile.recordRegion
        fun id(offset: Int) = u32(bytes, base + offset)
        val party = List(profile.partySlots) { id(profile.partyReferenceBase + it * 4) }.filter { it != 0L }
        val farm = profile.farm
        val farms = (0 until farm.groups).flatMap { group ->
            val offset = farm.base + group * farm.stride
            if (id(offset) == 0L) emptyList()
            else List(farm.residents) { id(offset + farm.residentOffset + it * 4) }.filter { it != 0L }
        }
        val slots = (0 until region.count).filter {
            val id = id(region.base + it * region.stride)
            id != 0L && id != profile.emptyRecordId
        }
        val ids = slots.map { id(region.base + it * region.stride) }
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val records = slots.map { slot ->
            val start = base + region.base + slot * region.stride
            fun signed16(offset: Int) = u16(bytes, start + offset).toShort().toInt()
            fun signed32(offset: Int) = u32(bytes, start + offset).toInt()
            val nativeId = u32(bytes, start + layout.offsetLocalId)
            val partyCount = party.count { it == nativeId }
            val farmCount = farms.count { it == nativeId }
            val conflict = partyCount + farmCount > 1
            val flags = u16(bytes, start + layout.offsetUnk108)
            val issues = buildSet {
                if (nativeId in duplicates) add(DsResearchIssue.DUPLICATE_LOCAL_ID)
                if (conflict) add(DsResearchIssue.CONFLICTING_LOCATION)
                if (flags and 2 != 0) add(DsResearchIssue.GUEST)
                if (flags and 4 != 0) add(DsResearchIssue.LENT)
                if (flags and 0x10 != 0) add(DsResearchIssue.EGG)
                if (flags and 0x20 != 0) add(DsResearchIssue.OTHER_DS_RECORD)
            }
            val nativeBytes = bytes.copyOfRange(start, start + region.stride)
            val encoded = try { Base64.getEncoder().encodeToString(nativeBytes) } finally { nativeBytes.fill(0) }
            DsResearchRecord(slot, nativeId, u32(bytes, start + layout.offsetSpeciesId),
                List(layout.nameSlots) { u16(bytes, start + layout.offsetName + it * 2) }.takeWhile { it != layout.nameTerminator },
                signed16(layout.offsetLevel), signed16(layout.offsetMaxLevel),
                DsNativeStats(signed32(layout.offsetHp), signed32(layout.offsetMaxHp),
                    signed16(layout.offsetMp), signed16(layout.offsetMaxMp),
                    signed16(layout.offsetAttack), signed16(layout.offsetDefense),
                    signed16(layout.offsetSpirit), signed16(layout.offsetSpeed)),
                List(layout.expChannels) { u32(bytes, start + layout.offsetExp + it * 4).toInt().toLong() },
                when { conflict -> DsResearchLocation.CONFLICT
                    partyCount == 1 -> DsResearchLocation.PARTY_REFERENCE
                    farmCount == 1 -> DsResearchLocation.FARM_REFERENCE
                    else -> DsResearchLocation.UNASSIGNED }, issues, encoded)
        }
        return DsResearchBlock(base, records, (party + farms).count { it !in ids })
    }

    private fun matches(bytes: ByteArray, offset: Int, expected: ByteArray): Boolean =
        offset >= 0 && offset <= bytes.size - expected.size && expected.indices.all { bytes[offset + it] == expected[it] }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 255) or ((bytes[offset + 1].toInt() and 255) shl 8)

    private fun u32(bytes: ByteArray, offset: Int): Long =
        (0 until 4).fold(0L) { value, i -> value or ((bytes[offset + i].toLong() and 255) shl (8 * i)) }
}
