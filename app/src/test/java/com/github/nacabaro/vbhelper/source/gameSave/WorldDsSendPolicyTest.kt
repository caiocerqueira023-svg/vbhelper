package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.NativeStatParameters
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordIssue
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldDsSendPolicyTest {
    private fun record(container: SaveContainer, issues: List<RecordIssue>, base64: String? = "AAAA",
        identity: String? = "save-local:25"): ObservedDigimon =
        ObservedDigimon("copy:1:record:0", container, 1, 20, "Poyomon", 3, null, 0,
            if (issues.isEmpty()) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW,
            issues, nativeRecordBase64 = base64, nativeIdentity = identity,
            nativeParameters = NativeStatParameters(1, 2, 3, 4, 5, 6, 7, 8, 9, emptyList(), 1))

    @Test fun cleanPartyRecordsAreReturnable() {
        assertTrue(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY, emptyList())))
    }

    @Test fun storageRecordsWithOnlyTheUnreferencedFlagAreReturnable() {
        assertTrue(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.UNASSIGNED, listOf(RecordIssue.UNASSIGNED_LOCATION))))
        assertTrue(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))))
    }

    @Test fun reviewedImageFlagsDoNotHideAnExactBankRecord() {
        assertTrue(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.UNASSIGNED,
            listOf(RecordIssue.UNASSIGNED_LOCATION, RecordIssue.IMPLAUSIBLE_VALUES))))
        assertTrue(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY,
            listOf(RecordIssue.UNKNOWN_NAME_CODES))))
    }

    @Test fun recordsWithoutExactBytesOrIdentityAreNeverReturnable() {
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY, emptyList(), base64 = null)))
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY, emptyList(), identity = null)))
    }

    @Test fun recordsCarryingOtherReviewFlagsAreNeverReturnable() {
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.UNASSIGNED,
            listOf(RecordIssue.UNASSIGNED_LOCATION, RecordIssue.GUEST_RECORD))))
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.PARTY,
            listOf(RecordIssue.CONFLICTING_LOCATION))))
    }

    @Test fun farmAndForeignRecordsAreNeverReturnable() {
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.FARM, emptyList())))
        assertFalse(WorldDsSendPolicy.nativeReturnable(record(SaveContainer.BANK, emptyList())))
    }
}
