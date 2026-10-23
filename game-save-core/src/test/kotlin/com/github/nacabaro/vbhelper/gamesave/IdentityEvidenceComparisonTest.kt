package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test

class IdentityEvidenceComparisonTest {
    private fun record(key: String, candidate: Long = 12) = ObservedDigimon(
        key, SaveContainer.PARTY, 1, 10, "Same name", 42, 1, candidate, RecordMembership.ACTIVE_RECORD)

    private fun observation(vararg records: ObservedDigimon) = SaveObservation(
        "game", "profile", 1, "a".repeat(64), records.toList(), emptyList())

    @Test fun candidateCanSuggestContinuityThroughRenameEvolutionPersonalityAndReorder() {
        val before = record("main:1")
        val after = before.copy(recordKey = "main:9", container = SaveContainer.BANK, position = 3,
            storedName = "New name", nativeSpeciesId = 11, personalityId = 16, level = 43)
        val result = IdentityEvidenceComparison.compare(observation(before), observation(after))
        val pair = result.candidates.single()
        assertEquals(setOf(ObservedField.NAME, ObservedField.SPECIES, ObservedField.PERSONALITY,
            ObservedField.LOCATION, ObservedField.PROGRESSION), pair.changedFields)
        assertEquals("main:1", pair.beforeRecordKey)
        assertEquals("main:9", pair.afterRecordKey)
        assertFalse(result.establishesIdentity)
    }

    @Test fun sameNameSpeciesAndPositionDoNotMatchDifferentCandidates() {
        val result = IdentityEvidenceComparison.compare(observation(record("same", 12)), observation(record("same", 13)))
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.onlyBefore)
        assertEquals(1, result.onlyAfter)
    }

    @Test fun duplicatesAndReviewRowsCannotProduceCandidatePairs() {
        val one = record("one")
        val duplicate = record("two")
        val review = record("farm", 99).copy(membership = RecordMembership.NEEDS_REVIEW,
            issues = listOf(RecordIssue.FARM_OWNERSHIP_UNVERIFIED))
        val result = IdentityEvidenceComparison.compare(observation(one, duplicate, review), observation(one, record("other", 99)))
        assertTrue(result.candidates.isEmpty())
        assertEquals(2, result.ambiguousCandidates)
        assertEquals(1, result.reviewRecordsBefore)
    }

    @Test fun zeroIdentityAndAnActiveRowCollidingWithATailStayAmbiguous() {
        val active = record("one")
        val tail = active.copy(recordKey = "tail", membership = RecordMembership.NEEDS_REVIEW,
            issues = listOf(RecordIssue.INACTIVE_TAIL))
        val result = IdentityEvidenceComparison.compare(observation(active, tail, record("zero", 0)), observation(active, record("zero", 0)))
        assertTrue(result.candidates.isEmpty())
        assertEquals(1, result.ambiguousCandidates)
        assertEquals(1, result.missingCandidatesBefore)
        assertEquals(1, result.missingCandidatesAfter)
    }

    @Test fun identicalRevisionStillDoesNotEstablishIdentityOrOwnership() {
        val observation = observation(record("one"))
        val result = IdentityEvidenceComparison.compare(observation, observation)
        assertTrue(result.sameRevision)
        assertTrue(result.candidates.single().changedFields.isEmpty())
        assertFalse(result.establishesIdentity)
    }

    @Test fun incompatibleGamesProfilesAndCodecVersionsCannotBeCompared() {
        val original = observation(record("one"))
        for (other in listOf(original.copy(gameId = "other"), original.copy(formatProfile = "other"), original.copy(codecVersion = 2))) {
            assertThrows(IllegalArgumentException::class.java) { IdentityEvidenceComparison.compare(original, other) }
        }
    }
}
