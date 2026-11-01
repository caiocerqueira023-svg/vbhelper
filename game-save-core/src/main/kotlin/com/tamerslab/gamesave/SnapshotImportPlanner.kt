package com.tamerslab.gamesave

enum class ImportBlocker {
    ADAPTER_VIEW_ONLY, OWNERSHIP_UNVERIFIED, IDENTITY_UNVERIFIED, RECORD_NEEDS_REVIEW,
    AMBIGUOUS_IDENTITY, NATIVE_DATA_MISSING,
}
enum class ImportItemAction { NEW_SNAPSHOT, ALREADY_IMPORTED, BLOCKED }

data class SnapshotImportItem(val record: ObservedDigimon, val action: ImportItemAction, val blockers: List<ImportBlocker>)

data class SnapshotImportPreview(
    val gameId: String,
    val formatProfile: String,
    val codecVersion: Int,
    val contentRevision: String,
    val plannerVersion: Int,
    val fingerprint: String,
    val mappingPending: Boolean,
    val items: List<SnapshotImportItem>,
    val requiresIdentityReview: Boolean = false,
) {
    val newItemCount: Int get() = items.count { it.action == ImportItemAction.NEW_SNAPSHOT }
    val canCommit: Boolean get() = newItemCount > 0 && items.none { it.action == ImportItemAction.BLOCKED }
    val removesFromGame: Boolean get() = false
}

/** Revision-bound snapshot-copy planning. Candidate evidence never becomes a permanent identity here. */
object SnapshotImportPlanner {
    const val VERSION = 2

    fun preview(observation: SaveObservation, selectedKeys: List<String>, importedRecordKeys: Set<String> = emptySet()): SnapshotImportPreview {
        require(selectedKeys.isNotEmpty() && selectedKeys.distinct().size == selectedKeys.size)
        val records = observation.records.associateBy { it.recordKey }
        require(records.size == observation.records.size && selectedKeys.all { it in records })
        val identityScope = if (observation.capabilities.provisionalSnapshotCopy)
            observation.records.filter { it.membership == RecordMembership.ACTIVE_RECORD && it.issues.isEmpty() }
            else observation.records
        val duplicates = identityScope.filter { it.identityEvidenceKey() != null }.groupingBy { it.identityEvidenceKey() }
            .eachCount().filterValues { it > 1 }.keys
        val items = selectedKeys.sorted().map { key ->
            val record = records.getValue(key)
            val blockers = buildList {
                if (!observation.capabilities.canView || !observation.capabilities.canImportSnapshot) add(ImportBlocker.ADAPTER_VIEW_ONLY)
                if (ObservationNotice.OWNERSHIP_RESEARCH_PENDING in observation.notices ||
                    record.container == SaveContainer.FARM && ObservationNotice.FARM_RESEARCH_PENDING in observation.notices) add(ImportBlocker.OWNERSHIP_UNVERIFIED)
                if ((ObservationNotice.IDENTITY_RESEARCH_PENDING in observation.notices && !observation.capabilities.provisionalSnapshotCopy) || record.identityEvidenceKey() == null) add(ImportBlocker.IDENTITY_UNVERIFIED)
                if (record.membership != RecordMembership.ACTIVE_RECORD || record.issues.isNotEmpty()) add(ImportBlocker.RECORD_NEEDS_REVIEW)
                if (record.identityEvidenceKey() in duplicates) add(ImportBlocker.AMBIGUOUS_IDENTITY)
                if (record.nativeRecordBase64.isNullOrEmpty()) add(ImportBlocker.NATIVE_DATA_MISSING)
            }
            SnapshotImportItem(record, when {
                key in importedRecordKeys -> ImportItemAction.ALREADY_IMPORTED
                blockers.isNotEmpty() -> ImportItemAction.BLOCKED
                else -> ImportItemAction.NEW_SNAPSHOT
            }, blockers)
        }
        // Length-prefix every component so locators containing separators cannot alias another selection.
        val parts = listOf(VERSION.toString(), observation.gameId, observation.formatProfile,
            observation.codecVersion.toString(), observation.contentRevision,
            (ObservationNotice.SPECIES_MAPPING_PENDING in observation.notices).toString(), observation.capabilities.provisionalSnapshotCopy.toString()) + selectedKeys.sorted() +
            items.flatMap { listOf(it.action.name, it.blockers.joinToString(",") { blocker -> blocker.name }) }
        val canonical = parts.joinToString("") { "${it.length}:$it" }
        return SnapshotImportPreview(observation.gameId, observation.formatProfile, observation.codecVersion,
            observation.contentRevision, VERSION, revision(canonical.toByteArray(Charsets.UTF_8)),
            ObservationNotice.SPECIES_MAPPING_PENDING in observation.notices, items, observation.capabilities.provisionalSnapshotCopy)
    }
}
