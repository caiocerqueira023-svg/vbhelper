package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test

class WorldDsCopySelectionTest {
    private fun record(copy: Int, sequence: Long? = null) = ObservedDigimon("copy:$copy:record:0", SaveContainer.PARTY, 1, 20,
        "Test", 3, null, 0, RecordMembership.NEEDS_REVIEW, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK),
        nativeRecordBase64 = "AQID", nativeIdentity = "save-local:7",
        nativeParameters = NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25, List(8) { 0 }, copy, sequence))
    private val observation = SaveObservation(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1,
        "revision", listOf(record(1), record(2)), listOf(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED),
        SaveCapabilities(canImportSnapshot = true, provisionalSnapshotCopy = true))

    @Test fun theMostRecentlyWrittenCopyIsTheNewestCopy() {
        val sequenced = observation.copy(records = listOf(record(1, 10), record(2, 11)))
        assertEquals(2, WorldDsCopySelection.latestCopy(sequenced))
        assertEquals(1, WorldDsCopySelection.latestCopy(sequenced.copy(records = listOf(record(1, 12), record(2, 11)))))
    }

    @Test fun equalCountersChooseNothingAndFormatsWithoutCountersChooseNothing() {
        val tied = observation.copy(records = listOf(record(1, 11), record(2, 11)))
        assertNull(WorldDsCopySelection.latestCopy(tied))
        assertNull(WorldDsCopySelection.latestCopy(observation))
        assertNull(WorldDsCopySelection.latestCopy(observation.copy(gameId = "other")))
    }

    @Test fun aCopyWithABrokenIntegrityHeaderIsNeverTheAutomaticChoice() {
        val newerButBroken = observation.copy(records = listOf(record(1, 10), record(2, 11)),
            copyIntegrity = mapOf(1 to true, 2 to false))
        assertEquals(1, WorldDsCopySelection.latestCopy(newerButBroken))
        assertEquals(1, WorldDsCopySelection.latestCopy(newerButBroken.copy(records = listOf(record(1, 11), record(2, 11)))))
        assertEquals(2, WorldDsCopySelection.latestCopy(newerButBroken.copy(copyIntegrity = emptyMap())))
    }

    @Test fun newestValidCopyIsSelectedAutomaticallyWithoutClaimingUserReview() {
        val sequenced = observation.copy(records = listOf(record(1, 10), record(2, 11)))
        val auto = WorldDsCopySelection.applyAutomatic(sequenced)
        assertEquals(2, WorldDsCopySelection.authoritativeCopy(auto))
        assertEquals("copy:2:record:0", auto.records.single { it.membership == RecordMembership.ACTIVE_RECORD }.recordKey)
        assertTrue(RecordIssue.AMBIGUOUS_SAVE_BLOCK in auto.records.first { it.nativeParameters?.saveBlock == 1 }.issues)
        assertTrue(ObservationNotice.SAVE_COPY_AUTO_SELECTED in auto.notices)
        assertFalse(ObservationNotice.SAVE_COPY_USER_CONFIRMED in auto.notices)
        assertFalse(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED in auto.notices)
        assertEquals(listOf(record(2, 11).recordKey), WorldDsCopySelection.recordsForCopy(auto, 2).map { it.recordKey })
    }

    @Test fun automaticSelectionNeverOverridesAManualRevisionBoundChoice() {
        val sequenced = observation.copy(records = listOf(record(1, 10), record(2, 11)))
        val manual = WorldDsCopySelection.apply(sequenced, "revision", 1)
        val auto = WorldDsCopySelection.applyAutomatic(manual)
        assertEquals(manual, auto)
        assertEquals(1, WorldDsCopySelection.authoritativeCopy(auto))
        assertTrue(ObservationNotice.SAVE_COPY_USER_CONFIRMED in auto.notices)
        assertFalse(ObservationNotice.SAVE_COPY_AUTO_SELECTED in auto.notices)
    }

    @Test fun noUniqueNewestCopyMeansNoAutomaticSelection() {
        assertEquals(observation, WorldDsCopySelection.applyAutomatic(observation))
        val tied = observation.copy(records = listOf(record(1, 11), record(2, 11)))
        assertEquals(tied, WorldDsCopySelection.applyAutomatic(tied))
        val brokenNewest = observation.copy(records = listOf(record(1, 10), record(2, 11)),
            copyIntegrity = mapOf(1 to true, 2 to false))
        val auto = WorldDsCopySelection.applyAutomatic(brokenNewest)
        assertEquals(1, WorldDsCopySelection.authoritativeCopy(auto))
        assertEquals("copy:1:record:0", auto.records.single { it.membership == RecordMembership.ACTIVE_RECORD }.recordKey)
    }

    @Test fun reviewedCopyAloneBecomesEligibleAndAllNativeRecordsRemainVisible() {
        val selected = WorldDsCopySelection.apply(observation, "revision", 2)
        assertEquals(2, selected.records.size)
        assertEquals(1, selected.activeCount)
        assertEquals("copy:2:record:0", selected.records.single { it.membership == RecordMembership.ACTIVE_RECORD }.recordKey)
        assertEquals(observation.records.map { it.nativeRecordBase64 }, selected.records.map { it.nativeRecordBase64 })
        assertEquals(observation.contentRevision, selected.contentRevision)
        assertTrue(ObservationNotice.SAVE_COPY_USER_CONFIRMED in selected.notices)
        assertFalse(ObservationNotice.SAVE_BLOCK_SELECTION_REQUIRED in selected.notices)
        assertTrue(SnapshotImportPlanner.preview(selected, listOf("copy:2:record:0")).canCommit)
        assertFalse(SnapshotImportPlanner.preview(selected, listOf("copy:1:record:0")).canCommit)
    }

    @Test fun selectionNeverSurvivesAnotherRevisionOrProfile() {
        assertEquals(observation, WorldDsCopySelection.apply(observation, "older", 2))
        val other = observation.copy(gameId = "other")
        assertEquals(other, WorldDsCopySelection.apply(other, "revision", 2))
        val upgraded = observation.copy(codecVersion = 2)
        assertEquals(upgraded, WorldDsCopySelection.apply(upgraded, "revision", 2))
        assertThrows(IllegalArgumentException::class.java) { WorldDsCopySelection.apply(observation, "revision", 3) }
    }

    @Test fun changingChoiceRecomputesEligibilityWithoutClearingOtherIssues() {
        val source = observation.copy(records = listOf(record(1), record(2).copy(issues =
            listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK, RecordIssue.LENT_RECORD))))
        val second = WorldDsCopySelection.apply(source, "revision", 2)
        assertEquals(0, second.activeCount)
        assertTrue(RecordIssue.LENT_RECORD in second.records.last().issues)
        val first = WorldDsCopySelection.apply(second, "revision", 1)
        assertEquals(1, first.activeCount)
        assertEquals(RecordMembership.ACTIVE_RECORD, first.records.first().membership)
        assertTrue(RecordIssue.AMBIGUOUS_SAVE_BLOCK in first.records.last().issues)
    }
}
