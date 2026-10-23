package com.github.nacabaro.vbhelper.gamesave

enum class ObservedField { NAME, SPECIES, PERSONALITY, LOCATION, PROGRESSION }

/** Observation locations for a possible match, never a permanent identity binding. */
data class CandidateContinuity(
    val beforeRecordKey: String,
    val afterRecordKey: String,
    val changedFields: Set<ObservedField>,
)

data class IdentityEvidenceReport(
    val sameRevision: Boolean,
    val candidates: List<CandidateContinuity>,
    val ambiguousCandidates: Int,
    val onlyBefore: Int,
    val onlyAfter: Int,
    val missingCandidatesBefore: Int,
    val missingCandidatesAfter: Int,
    val reviewRecordsBefore: Int,
    val reviewRecordsAfter: Int,
) {
    // Hash persistence is research evidence; collisions, reuse and branches remain unverified.
    val establishesIdentity: Boolean get() = false
}

/** Compare immutable observations without merging sources, acquiring partners, or promoting hashes to IDs. */
object IdentityEvidenceComparison {
    fun compare(before: SaveObservation, after: SaveObservation): IdentityEvidenceReport {
        require(before.gameId == after.gameId && before.formatProfile == after.formatProfile &&
            before.codecVersion == after.codecVersion) { "Incompatible observation profiles" }
        val old = before.records.filter { it.identityEvidenceKey() != null }.groupBy { requireNotNull(it.identityEvidenceKey()) }
        val new = after.records.filter { it.identityEvidenceKey() != null }.groupBy { requireNotNull(it.identityEvidenceKey()) }
        fun List<ObservedDigimon>.eligible() = size == 1 && single().membership == RecordMembership.ACTIVE_RECORD && single().issues.isEmpty()
        val ambiguous = (old.keys + new.keys).filterTo(mutableSetOf()) { id ->
            old[id]?.eligible() == false || new[id]?.eligible() == false
        }
        val shared = (old.keys intersect new.keys) - ambiguous
        val candidates = shared.sorted().map { id ->
            val a = old.getValue(id).single()
            val b = new.getValue(id).single()
            val fields = buildSet {
                if (a.storedName != b.storedName) add(ObservedField.NAME)
                if (a.nativeSpeciesId != b.nativeSpeciesId) add(ObservedField.SPECIES)
                if (a.personalityId != b.personalityId) add(ObservedField.PERSONALITY)
                if (a.recordKey != b.recordKey || a.container != b.container || a.position != b.position) add(ObservedField.LOCATION)
                if (a.level != b.level || a.totalExp != b.totalExp || a.talentRaw != b.talentRaw ||
                    a.bondRaw != b.bondRaw || a.previousSpeciesIds != b.previousSpeciesIds ||
                    a.nativeParameters?.copy(saveBlock = 0) != b.nativeParameters?.copy(saveBlock = 0)) add(ObservedField.PROGRESSION)
            }
            CandidateContinuity(a.recordKey, b.recordKey, fields)
        }
        return IdentityEvidenceReport(
            sameRevision = before.contentRevision == after.contentRevision,
            candidates = candidates,
            ambiguousCandidates = ambiguous.size,
            onlyBefore = (old.keys - new.keys - ambiguous).size,
            onlyAfter = (new.keys - old.keys - ambiguous).size,
            missingCandidatesBefore = before.records.count { it.identityEvidenceKey() == null },
            missingCandidatesAfter = after.records.count { it.identityEvidenceKey() == null },
            reviewRecordsBefore = before.reviewCount,
            reviewRecordsAfter = after.reviewCount,
        )
    }
}
