package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.ObservationNotice
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordIssue
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.SaveObservation
import com.tamerslab.gamesave.WorldDsCopySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldDsMoveCopyPolicyTest {
    private fun record(block: Int, identity: String?, base64: String?, container: SaveContainer = SaveContainer.PARTY,
        issues: List<RecordIssue> = emptyList(), sequence: Long? = null): ObservedDigimon =
        ObservedDigimon("copy:$block:record:0", container, 1, 20, "Agumon", 3, null, 0,
            if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
            issues, nativeRecordBase64 = base64, nativeIdentity = identity,
            nativeParameters = NativeStatParameters(1, 2, 3, 4, 5, 6, 7, 8, 9, emptyList(), block, sequence))

    private fun observation(records: List<ObservedDigimon>, confirmed: Boolean = false,
        copyIntegrity: Map<Int, Boolean> = emptyMap()) = SaveObservation(
        "digimon_world_ds_nds", "world-ds-usa-drastic-raw-v1", 1, "r", records,
        if (confirmed) listOf(ObservationNotice.SAVE_COPY_USER_CONFIRMED) else emptyList(), copyIntegrity = copyIntegrity)

    private val recordBase = "AAAA"

    @Test fun farmResidentsAreOnlyRemovableWhereTheLayoutOptsIn() {
        val farm = record(1, "save-local:7", recordBase, container = SaveContainer.FARM)
        assertFalse(WorldDsMoveRecordPolicy.removable(farm))
        assertTrue(WorldDsMoveRecordPolicy.removable(farm, farmRecordsMovable = true))
        assertTrue(WorldDsMoveRecordPolicy.removable(farm.copy(issues = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK)),
            farmRecordsMovable = true))
        assertFalse(WorldDsMoveRecordPolicy.removable(farm.copy(issues = listOf(RecordIssue.GUEST_RECORD)),
            farmRecordsMovable = true))
        assertTrue(NdsStoryProfiles.LOST_EVOLUTION.farmRecordsMovable)
        assertFalse(NdsStoryProfiles.WORLD_DS.farmRecordsMovable)
        assertFalse(NdsStoryProfiles.DAWN_DUSK.farmRecordsMovable)
        val secondFarm = record(1, "save-local:8", recordBase, container = SaveContainer.FARM)
        assertTrue(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(farm, secondFarm), farm))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(farm), farm))
    }

    @Test fun singlePopulatedCopyIsConsistent() {
        val chosen = record(1, "save-local:7", recordBase)
        assertTrue(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen)), chosen))
    }

    @Test fun identicalCopiesAreConsistentEvenWhenTheRawReadFlagsAmbiguity() {
        val chosen = record(1, "save-local:7", recordBase)
        val other = record(2, "save-local:7", recordBase, issues = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        assertTrue(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, other)), chosen))
    }

    @Test fun aCopyMissingTheIndividualDoesNotBlockMovingTheCopyThatHasIt() {
        val chosen = record(1, "save-local:7", recordBase)
        val unrelated = record(2, "save-local:8", "BBBB")
        assertTrue(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, unrelated)), chosen))
    }

    @Test fun unreviewedDifferingBytesWithoutAuthorityBlockButAReviewedCopyIsAuthoritative() {
        val chosen = record(1, "save-local:7", recordBase)
        val other = record(2, "save-local:7", "BBBB", issues = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        val raw = observation(listOf(chosen, other))
        assertNull(WorldDsMoveCopyPolicy.resolve(raw, chosen))
        val reviewed = WorldDsCopySelection.apply(raw, "r", 1)
        assertEquals("copy:1:record:0", WorldDsMoveCopyPolicy.resolve(reviewed, reviewed.records.first())?.recordKey)
    }

    @Test fun newestValidCopyWinsEvenWhenOlderCopiesDiffer() {
        val older = record(1, "save-local:7", recordBase, sequence = 10)
        val newest = record(2, "save-local:7", "BBBB", issues = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK), sequence = 11)
        val raw = observation(listOf(older, newest), copyIntegrity = mapOf(1 to true, 2 to true))
        val resolved = WorldDsMoveCopyPolicy.resolve(raw, older)
        assertNotNull(resolved)
        assertEquals("copy:2:record:0", resolved!!.recordKey)
        assertEquals("BBBB", resolved.nativeRecordBase64)
        assertFalse(WorldDsMoveCopyPolicy.onlyOutsideAuthority(raw, older))
    }

    @Test fun aDigimonOnlyInAnOlderCopyResolvesToNoAuthority() {
        val older = record(1, "save-local:7", recordBase, sequence = 10)
        val newestOther = record(2, "save-local:8", "BBBB", sequence = 11)
        val raw = observation(listOf(older, newestOther), copyIntegrity = mapOf(1 to true, 2 to true))
        assertNull(WorldDsMoveCopyPolicy.resolve(raw, older))
        assertTrue(WorldDsMoveCopyPolicy.onlyOutsideAuthority(raw, older))
    }

    @Test fun lastMemberSafetyIgnoresAnOlderCopy() {
        val olderOther = record(1, "save-local:8", recordBase, sequence = 10)
        val newestTarget = record(2, "save-local:7", recordBase, sequence = 11)
        val raw = observation(listOf(olderOther, newestTarget), copyIntegrity = mapOf(1 to true, 2 to true))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(raw, newestTarget))
    }

    @Test fun admissionNeedsAFreePartySlotInTheAuthoritativeCopy() {
        val target = record(2, "save-local:7", recordBase, sequence = 11)
        val full = (0 until 7).map { index ->
            record(2, "save-local:${8 + index}", recordBase, sequence = 11).copy(recordKey = "copy:2:record:${index + 1}")
        }
        val raw = observation(listOf(target) + full, copyIntegrity = mapOf(2 to true))
        assertFalse(WorldDsMoveRecordPolicy.hasFreePartySlot(raw, target))
    }

    @Test fun aCopiesCounterpartOutsideThePartyBlocksTheMove() {
        val chosen = record(1, "save-local:7", recordBase)
        val other = record(2, "save-local:7", recordBase, container = SaveContainer.FARM)
        assertFalse(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, other)), chosen))
    }

    @Test fun identitiesWithoutNativeKeysAreNeverConsistent() {
        val chosen = record(1, null, null)
        assertFalse(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen)), chosen))
        val counterpart = record(1, null, null)
        assertFalse(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, counterpart.copy(recordKey = "copy:2:record:0"))), chosen))
    }

    @Test fun recordsWithoutExactBytesAreNeverConsistent() {
        val chosen = record(1, "save-local:7", null)
        assertFalse(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen)), chosen))
    }

    @Test fun storageRecordsAreConsistentAcrossCopiesRegardlessOfPartyPlacement() {
        val issues = listOf(RecordIssue.UNASSIGNED_LOCATION)
        val chosen = record(1, "save-local:25", recordBase, SaveContainer.UNASSIGNED, issues)
        val other = record(2, "save-local:25", recordBase, SaveContainer.UNASSIGNED, issues)
        assertTrue(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, other)), chosen))
        assertFalse(WorldDsMoveCopyPolicy.consistent(observation(listOf(chosen, other.copy(container = SaveContainer.PARTY))), chosen))
    }

    @Test fun onlyOrdinaryPartyMembersAndUnreferencedStorageRecordsAreRemovableRegardlessOfCopyReview() {
        val clean = record(1, "save-local:7", recordBase)
        assertTrue(WorldDsMoveRecordPolicy.removable(clean))
        assertTrue(WorldDsMoveRecordPolicy.removable(clean.copy(issues = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK),
            membership = RecordMembership.NEEDS_REVIEW)))
        assertFalse(WorldDsMoveRecordPolicy.removable(clean.copy(issues = listOf(RecordIssue.GUEST_RECORD))))
        assertFalse(WorldDsMoveRecordPolicy.removable(clean.copy(nativeIdentity = null)))
        val storage = record(1, "save-local:25", recordBase, SaveContainer.UNASSIGNED, listOf(RecordIssue.UNASSIGNED_LOCATION))
        assertTrue(WorldDsMoveRecordPolicy.removable(storage))
        assertTrue(WorldDsMoveRecordPolicy.removable(storage.copy(issues = listOf(RecordIssue.UNASSIGNED_LOCATION,
            RecordIssue.AMBIGUOUS_SAVE_BLOCK))))
        assertFalse(WorldDsMoveRecordPolicy.removable(storage.copy(issues = emptyList(), membership = RecordMembership.ACTIVE_RECORD)))
        assertFalse(WorldDsMoveRecordPolicy.removable(storage.copy(issues = listOf(RecordIssue.GUEST_RECORD))))
        assertFalse(WorldDsMoveRecordPolicy.removable(storage.copy(container = SaveContainer.FARM)))
    }

    @Test fun duplicatedCopiesCountOnceSoTheSolePartyMemberStaysProtected() {
        val ambiguous = listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK)
        val partyA = record(1, "save-local:7", recordBase, SaveContainer.PARTY, ambiguous)
        val partyACopy = partyA.copy(recordKey = "copy:2:record:0", nativeParameters = NativeStatParameters(1, 2, 3, 4, 5, 6, 7, 8, 9, emptyList(), 2))
        val storage = record(1, "save-local:25", recordBase, SaveContainer.UNASSIGNED,
            listOf(RecordIssue.UNASSIGNED_LOCATION, RecordIssue.AMBIGUOUS_SAVE_BLOCK)).copy(recordKey = "copy:1:record:1")
        assertTrue(WorldDsMoveRecordPolicy.removable(partyACopy))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, partyACopy), partyA))
        assertTrue(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, partyACopy, storage), storage))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, partyACopy, storage), partyA))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(storage, storage.copy(recordKey = "copy:2:record:1")), storage))
    }

    @Test fun aMoveMustLeaveOneUsableDigimonAndAPartyMoveKeepsThePartyNonEmpty() {
        val partyA = record(1, "save-local:7", recordBase)
        val partyB = record(1, "save-local:12", recordBase).copy(recordKey = "copy:1:record:1")
        val storage = record(1, "save-local:25", recordBase, SaveContainer.UNASSIGNED,
            listOf(RecordIssue.UNASSIGNED_LOCATION)).copy(recordKey = "copy:1:record:2")
        assertTrue(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, partyB), partyA))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA), partyA))
        assertTrue(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, storage), storage))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(partyA, storage), partyA))
        assertFalse(WorldDsMoveRecordPolicy.keepsUsableRecord(listOf(storage), storage))
    }
}

