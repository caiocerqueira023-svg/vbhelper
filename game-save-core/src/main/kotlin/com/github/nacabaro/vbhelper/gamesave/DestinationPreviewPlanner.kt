package com.github.nacabaro.vbhelper.gamesave

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest

/** One retained source-native baseline for an individual; retention is evidence, not a write capability. */
data class RetainedNativeAnchor(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val snapshotKey: String,
    val contentRevision: String,
    val originalDocumentRetained: Boolean,
    val progressRetained: Boolean,
)

/**
 * Read-only planning input. [recordedChanges] stays in the app's own semantic units and
 * [pendingNativeSnapshotIds] lists only unresolved raw native differences — never a currency.
 */
data class TransferProgressContext(
    val accountRevision: Long,
    val latestAppDeltaId: Long,
    val latestNativeSnapshotId: Long,
    val recordedChanges: ProgressVector,
    val pendingNativeSnapshotIds: List<Long>,
    val anchors: List<RetainedNativeAnchor>,
) {
    /** Ids are the reviewable set (bounded by the caller); the review queue remains the total source. */
    val pendingNativeCount: Int get() = pendingNativeSnapshotIds.size
}

/** A retained native anchor, not a projected APP/DIM stat sheet or proof of native ownership. */
data class NativeTransferOrigin(
    val individualId: String,
    val snapshotKey: String,
    val contentRevision: String,
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val record: ObservedDigimon,
    val provisionalIdentity: Boolean,
    val progress: TransferProgressContext? = null,
)

enum class DestinationBlocker {
    WRITER_UNAVAILABLE, INSERT_UNSUPPORTED, CONVERSION_UNAVAILABLE, IDENTITY_REVIEW_REQUIRED,
    POSSIBLE_EXISTING_INDIVIDUAL, SOURCE_RECORD_UNCERTAIN, NATIVE_DATA_MISSING, FARM_OUT_OF_SCOPE,
    PROGRESS_CONTEXT_UNAVAILABLE, NATIVE_ANCHOR_UNAVAILABLE, NATIVE_PROGRESS_UNRESOLVED,
    DESTINATION_RECORD_CHANGED, PROGRESS_MAPPING_UNAVAILABLE, BATCH_IDENTITY_CONFLICT,
}
enum class TransferField { NATIVE_RECORD, SPECIES, NAME, PERSONALITY, PROGRESSION, SKILLS_EQUIPMENT }
enum class TransferFieldDisposition { RETAINED_IN_STORAGE, UNVALIDATED, UNMAPPED }

/** Describes recorded growth and unresolved native differences without asserting any conversion. */
enum class DestinationProgressStatus {
    CONTEXT_UNAVAILABLE, NO_RECORDED_CHANGES, RECORDED_CHANGES_UNMAPPED, NATIVE_CHANGES_UNRESOLVED,
}
data class DestinationProgressAssessment(
    val status: DestinationProgressStatus,
    val recordedChanges: ProgressVector?,
    val targetAnchor: RetainedNativeAnchor?,
    val pendingNativeCount: Int,
    val differingDestinationRecords: Int,
)

data class DestinationPreview(
    val plannerVersion: Int,
    val individualId: String,
    val snapshotKey: String,
    val destinationKey: String,
    val destinationRevision: String,
    val destinationGameId: String,
    val destinationContainer: SaveContainer,
    val sameNativeFormat: Boolean,
    val possibleNativeMatches: Int,
    val blockers: Set<DestinationBlocker>,
    val fields: Map<TransferField, TransferFieldDisposition>,
    val progress: DestinationProgressAssessment,
    val fingerprint: String,
) {
    /** Planning never grants execution, even for an adapter advertising insertion. */
    val canExecute: Boolean get() = false
}

/** Plan 4.4: one destination read evaluated for every selected individual, still without execution. */
data class BatchDestinationPreview(
    val plannerVersion: Int,
    val destinationKey: String,
    val destinationRevision: String,
    val destinationGameId: String,
    val destinationContainer: SaveContainer,
    val items: List<DestinationPreview>,
    val fingerprint: String,
) {
    init { require(items.isNotEmpty()) }
    val canExecute: Boolean get() = false
    /** Restrictions every selected individual shares; per-item blockers stay on each item. */
    val commonBlockers: Set<DestinationBlocker> get() = items.map { it.blockers }.reduce { shared, next -> shared intersect next }
}

/** Read-only capability/loss evaluation. No writer, identity merge or progress receipt is produced. */
object DestinationPreviewPlanner {
    const val VERSION = 1
    const val MAX_BATCH_ITEMS = 16

    fun preview(origin: NativeTransferOrigin, destinationKey: String, destination: SaveObservation,
        container: SaveContainer, extraBlockers: Set<DestinationBlocker> = emptySet()): DestinationPreview {
        require(origin.individualId.isNotBlank() && origin.snapshotKey.isNotBlank() && destinationKey.isNotBlank())
        require(origin.codecVersion > 0 && destination.codecVersion > 0)
        val same = origin.gameId == destination.gameId && origin.formatProfile == destination.formatProfile &&
            origin.codecVersion == destination.codecVersion
        val identity = origin.record.identityEvidenceKey()
        val matches = if (same && identity != null) destination.records.count { it.identityEvidenceKey() == identity } else 0
        val originBytes = origin.record.nativeRecordBase64
        // A record that still matches the candidate identity but no longer matches retained bytes
        // is a destination change requiring review, not a merge proposal.
        val differing = if (same && identity != null && originBytes != null)
            destination.records.count { it.identityEvidenceKey() == identity &&
                it.nativeRecordBase64 != null && it.nativeRecordBase64 != originBytes }
        else 0
        val context = origin.progress
        val targetAnchor = context?.anchors?.firstOrNull { it.gameId == destination.gameId &&
            it.formatProfile == destination.formatProfile && it.codecVersion == destination.codecVersion }
        val pendingCount = context?.pendingNativeCount ?: 0
        val recorded = context?.recordedChanges
        val status = when {
            context == null -> DestinationProgressStatus.CONTEXT_UNAVAILABLE
            pendingCount > 0 || differing > 0 -> DestinationProgressStatus.NATIVE_CHANGES_UNRESOLVED
            recorded == null || recorded.isZero -> DestinationProgressStatus.NO_RECORDED_CHANGES
            else -> DestinationProgressStatus.RECORDED_CHANGES_UNMAPPED
        }
        val progress = DestinationProgressAssessment(status, recorded, targetAnchor, pendingCount, differing)
        val blockers = buildSet {
            add(DestinationBlocker.WRITER_UNAVAILABLE)
            if (!destination.capabilities.canInsert) add(DestinationBlocker.INSERT_UNSUPPORTED)
            if (!same) add(DestinationBlocker.CONVERSION_UNAVAILABLE)
            if (origin.provisionalIdentity) add(DestinationBlocker.IDENTITY_REVIEW_REQUIRED)
            if (matches > 0) add(DestinationBlocker.POSSIBLE_EXISTING_INDIVIDUAL)
            if (origin.record.membership != RecordMembership.ACTIVE_RECORD || origin.record.issues.isNotEmpty())
                add(DestinationBlocker.SOURCE_RECORD_UNCERTAIN)
            if (origin.record.nativeRecordBase64.isNullOrBlank()) add(DestinationBlocker.NATIVE_DATA_MISSING)
            if (container == SaveContainer.FARM || origin.record.container == SaveContainer.FARM)
                add(DestinationBlocker.FARM_OUT_OF_SCOPE)
            if (context == null) add(DestinationBlocker.PROGRESS_CONTEXT_UNAVAILABLE)
            else {
                if (targetAnchor == null) add(DestinationBlocker.NATIVE_ANCHOR_UNAVAILABLE)
                if (pendingCount > 0) add(DestinationBlocker.NATIVE_PROGRESS_UNRESOLVED)
                if (differing > 0) add(DestinationBlocker.DESTINATION_RECORD_CHANGED)
                if (recorded != null && !recorded.isZero) add(DestinationBlocker.PROGRESS_MAPPING_UNAVAILABLE)
            }
            addAll(extraBlockers)
        }
        val fields = TransferField.entries.associateWith { field ->
            if (field == TransferField.NATIVE_RECORD && !origin.record.nativeRecordBase64.isNullOrBlank())
                TransferFieldDisposition.RETAINED_IN_STORAGE
            else if (field == TransferField.PROGRESSION && same && recorded != null && !recorded.isZero)
                TransferFieldDisposition.UNMAPPED
            else if (same) TransferFieldDisposition.UNVALIDATED else TransferFieldDisposition.UNMAPPED
        }
        // Length-prefixed values prevent names/opaque keys from introducing delimiter collisions.
        val values = listOf(VERSION, origin.individualId, origin.snapshotKey, origin.contentRevision,
            origin.gameId, origin.formatProfile, origin.codecVersion, origin.provisionalIdentity,
            origin.record.recordKey, origin.record.container, origin.record.position, origin.record.nativeSpeciesId,
            origin.record.storedName, origin.record.level, origin.record.personalityId, origin.record.identityCandidate,
            origin.record.nativeIdentity, origin.record.membership, origin.record.issues, origin.record.totalExp,
            origin.record.talentRaw, origin.record.bondRaw, origin.record.previousSpeciesIds, origin.record.nativeRecordBase64,
            origin.record.nativeParameters,
            context?.accountRevision, context?.latestAppDeltaId, context?.latestNativeSnapshotId, context?.recordedChanges,
            context?.pendingNativeSnapshotIds?.sorted(), context?.pendingNativeCount,
            context?.anchors?.sortedWith(compareBy({ it.gameId }, { it.formatProfile }, { it.codecVersion }, { it.snapshotKey })),
            progress,
            destinationKey, destination.contentRevision, destination.gameId, destination.formatProfile,
            destination.codecVersion, destination.capabilities.canInsert, container, matches,
            blockers.sortedBy { it.name }, fields)
        return DestinationPreview(VERSION, origin.individualId, origin.snapshotKey, destinationKey,
            destination.contentRevision, destination.gameId, container, same, matches, blockers, fields, progress,
            fingerprintOf(values))
    }

    /**
     * Evaluates every selected individual against one destination observation. Selection order is
     * preserved for display, but the batch fingerprint is order-insensitive so the same set never
     * rebuilds spuriously. Two selected origins may not share an individual or a native anchor.
     */
    fun batch(origins: List<NativeTransferOrigin>, destinationKey: String, destination: SaveObservation,
        container: SaveContainer): BatchDestinationPreview {
        require(origins.isNotEmpty() && origins.size <= MAX_BATCH_ITEMS) { "Batch size must be 1..$MAX_BATCH_ITEMS" }
        require(origins.map { it.individualId }.distinct().size == origins.size) { "Duplicate individuals in one planning batch" }
        require(origins.map { it.snapshotKey }.distinct().size == origins.size) { "Duplicate native anchors in one planning batch" }
        val identityKeys = origins.mapNotNull { it.record.identityEvidenceKey() }
        val conflicted = identityKeys.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val items = origins.map { origin ->
            val conflict = origin.record.identityEvidenceKey()?.let { it in conflicted } == true
            preview(origin, destinationKey, destination, container,
                if (conflict) setOf(DestinationBlocker.BATCH_IDENTITY_CONFLICT) else emptySet())
        }
        val ordered = items.sortedBy { it.snapshotKey }
            .map { "${it.snapshotKey.length}:${it.snapshotKey}#${it.fingerprint}" }
        val values = listOf(VERSION, destinationKey, destination.contentRevision, destination.gameId,
            destination.formatProfile, destination.codecVersion, container, ordered)
        return BatchDestinationPreview(VERSION, destinationKey, destination.contentRevision, destination.gameId,
            container, items, fingerprintOf(values))
    }

    // Length-prefixed values prevent names/opaque keys from introducing delimiter collisions.
    private fun fingerprintOf(values: List<Any?>): String {
        val bytes = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output -> values.forEach { value ->
                val encoded = value?.toString()?.toByteArray(Charsets.UTF_8)
                output.writeInt(encoded?.size ?: -1)
                if (encoded != null) output.write(encoded)
            } }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
