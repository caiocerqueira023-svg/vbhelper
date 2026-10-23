package com.github.nacabaro.vbhelper.gamesave

/** A location in one observation, never a permanent Digimon identity. */
enum class SaveContainer { PARTY, BANK, FARM, UNASSIGNED }
enum class RecordMembership { ACTIVE_RECORD, NEEDS_REVIEW }
enum class RecordIssue { INACTIVE_BOUNDARY, INACTIVE_TAIL, FARM_OWNERSHIP_UNVERIFIED, MISSING_IDENTITY, DUPLICATE_IDENTITY,
    GUEST_RECORD, LENT_RECORD, EGG_RECORD, FOREIGN_RECORD, CONFLICTING_LOCATION, UNKNOWN_NAME_CODES, UNASSIGNED_LOCATION, AMBIGUOUS_SAVE_BLOCK,
    IMPLAUSIBLE_VALUES }
enum class ObservationNotice { OWNERSHIP_RESEARCH_PENDING, SPECIES_MAPPING_PENDING, FARM_RESEARCH_PENDING, IDENTITY_RESEARCH_PENDING,
    SAVE_INTEGRITY_UNVERIFIED, SAVE_BLOCK_SELECTION_REQUIRED, SAVE_COPY_AUTO_SELECTED, SAVE_COPY_USER_CONFIRMED }
enum class SaveReadFailure { UNSUPPORTED_FORMAT, INVALID_CONTAINER, UNRECOGNIZED_LAYOUT, INPUT_TOO_LARGE, AMBIGUOUS_FORMAT }

class SaveReadException(val reason: SaveReadFailure) : IllegalArgumentException(reason.name)

/** Exact source-container bytes are optional for synthetic/observation-only transports. Close after archiving. */
class CapturedSave(val observation: SaveObservation, val container: ByteArray?) : java.io.Closeable {
    override fun close() { container?.fill(0) }
}

data class SaveCapabilities(
    val canView: Boolean = true,
    val canImportSnapshot: Boolean = false,
    val canRemove: Boolean = false,
    val canInsert: Boolean = false,
    val provisionalSnapshotCopy: Boolean = false,
)

/** Native values retain their original units. Multiple EXP channels are never collapsed into scalar EXP. */
data class NativeStatParameters(val hp: Int, val maxHp: Int, val mp: Int, val maxMp: Int,
    val attack: Int, val defense: Int, val spirit: Int, val speed: Int,
    val aptitude: Int, val expCounters: List<Long>, val saveBlock: Int,
    /** Source save counter when the format exposes one; higher is newer. Not a Digimon stat. */
    val saveSequence: Long? = null)

/** Retain the native ID and stored name independently: a nickname is not a species mapping. */
data class ObservedDigimon(
    val recordKey: String,
    val container: SaveContainer,
    val position: Int,
    val nativeSpeciesId: Long,
    val storedName: String,
    val level: Int,
    val personalityId: Int?,
    val identityCandidate: Long,
    val membership: RecordMembership,
    val issues: List<RecordIssue> = emptyList(),
    val totalExp: Long? = 0,
    val talentRaw: Long? = 0,
    val bondRaw: Float? = 0f,
    val previousSpeciesIds: List<Long> = emptyList(),
    /** Exact source-native record bytes. No claim that these alone reconstruct a writable save. */
    val nativeRecordBase64: String? = null,
    /** Adapter-native opaque/compound key, when available. A source/version policy still verifies its semantics. */
    val nativeIdentity: String? = null,
    val nativeParameters: NativeStatParameters? = null,
)

data class SaveObservation(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val contentRevision: String,
    val records: List<ObservedDigimon>,
    val notices: List<ObservationNotice>,
    val capabilities: SaveCapabilities = SaveCapabilities(),
    /** Per populated copy: whether its integrity header matches its bytes. Empty when unknown. */
    val copyIntegrity: Map<Int, Boolean> = emptyMap(),
    /** Byte offset of the save image inside the container; wrappers shift every block base by this much. */
    internal val imageBase: Int = 0,
) {
    val activeCount: Int get() = records.count { it.membership == RecordMembership.ACTIVE_RECORD }
    val reviewCount: Int get() = records.size - activeCount
}

interface GameSaveReader {
    val gameId: String
    val maxInputBytes: Int
    fun read(bytes: ByteArray): SaveObservation

    /**
     * Cheap candidate gate only, never a support decision: the default admits every size this reader could
     * possibly parse, so wrappers, padding and unexpected sizes still reach [read], which unwraps or fails
     * with an explicit reason. Narrow it only when probing a known-foreign size is demonstrably costly.
     * Binding for every game reader: see game-save-core/SAVE_READING_STANDARD.md.
     */
    fun acceptsLength(length: Int): Boolean = length <= maxInputBytes
}

fun ObservedDigimon.identityEvidenceKey(): String? = if (nativeIdentity != null) {
    nativeIdentity.takeIf { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) }?.let { "native:$it" }
} else identityCandidate.takeIf { it > 0 }?.let { "candidate:$it" }

/**
 * Folder discovery never decides support: it only lists cheap candidates whose contents are validated on
 * read. Known container size families are candidates under any name; extension matching only widens that
 * when the folder entry reports no size. See SAVE_READING_STANDARD.md.
 */
object SaveDiscoveryPolicy {
    private val worldDsRanges: List<LongRange> = listOf(
        WorldDsSaveReader.SAVE_BYTES.toLong() until (WorldDsSaveReader.SAVE_BYTES +
            WorldDsSaveReader.WRAPPER_SLACK_BYTES + WorldDsResearchInspector.DESMUME_FOOTER_BYTES + 1).toLong(),
        WorldDsSaveReader.FILE_BYTES.toLong() until (WorldDsSaveReader.FILE_BYTES +
            WorldDsSaveReader.WRAPPER_SLACK_BYTES + WorldDsResearchInspector.DESMUME_FOOTER_BYTES + 1).toLong(),
    )

    private fun knownWorldDsSize(bytes: Long): Boolean = worldDsRanges.any { bytes in it }

    fun isCandidate(name: String, bytes: Long?): Boolean =
        (Regex("[0-9]{4}\\.bin", RegexOption.IGNORE_CASE).matches(name) &&
            (bytes == null || bytes == TimeStrangerSaveReader.FILE_BYTES.toLong())) ||
            (bytes != null && knownWorldDsSize(bytes)) ||
            (name.length > 4 && (name.endsWith(".sav", ignoreCase = true) || name.endsWith(".dsv", ignoreCase = true)) &&
                (bytes == null || knownWorldDsSize(bytes)))
}
