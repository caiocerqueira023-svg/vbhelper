package com.github.nacabaro.vbhelper.gamesave

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class NativeProgressReviewTest {
    private fun record(ds: Boolean = true): ObservedDigimon {
        val raw = ByteArray(if (ds) 384 else 336)
        return ObservedDigimon("copy:1:record:0", SaveContainer.PARTY, 1, 20, "Test", 3,
            if (ds) null else 16, 7, RecordMembership.ACTIVE_RECORD,
            nativeRecordBase64 = Base64.getEncoder().encodeToString(raw),
            nativeParameters = if (ds) NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25, List(8) { 0L }, 1) else null)
    }
    private fun rawChange(record: ObservedDigimon, offset: Int): ObservedDigimon {
        val bytes = Base64.getDecoder().decode(record.nativeRecordBase64)
        bytes[offset] = 1
        return record.copy(nativeRecordBase64 = Base64.getEncoder().encodeToString(bytes))
    }
    private fun review(a: ObservedDigimon, b: ObservedDigimon, ds: Boolean = true) = NativeProgressReview.review(
        if (ds) WorldDsSaveReader.GAME_ID else TimeStrangerSaveReader.GAME_ID,
        if (ds) WorldDsSaveReader.PROFILE else TimeStrangerSaveReader.PROFILE, 1, a, b)

    @Test fun separateExpChannelsRemainNativeAndNeverAwardAppGrowth() {
        val a = record()
        val b = rawChange(a, 0x7C).copy(nativeParameters = a.nativeParameters!!.copy(expCounters = listOf(0, 50, 0, 0, 0, 0, 0, 0)))
        val report = review(a, b)
        val change = report.changes.single()
        assertEquals(NativeMeasure.EXP_CHANNEL, change.measure)
        assertEquals(2, change.channel)
        assertEquals(50L, change.delta)
        assertTrue(NativeChangeKind.PERMANENT_PARAMETERS in report.kinds)
        assertFalse(report.canNormalize)
        assertFalse(report.unknownNativeBytes)
    }
    @Test fun currentHpMpAreTemporaryRatherThanEarnedGrowth() {
        val a = record()
        val b = rawChange(a, 0x3C).copy(nativeParameters = a.nativeParameters!!.copy(hp = 90, mp = 10))
        val report = review(a, b)
        assertEquals(setOf(NativeChangeKind.TEMPORARY_STATE), report.kinds)
        assertEquals(setOf(NativeMeasure.CURRENT_HP, NativeMeasure.CURRENT_MP), report.changes.map { it.measure }.toSet())
        assertFalse(report.canNormalize)
    }
    @Test fun renameOnlyEvidenceDoesNotBecomeProgressOrHideUnknownBytes() {
        val a = record(false)
        val b = rawChange(a, 4).copy(storedName = "Renamed")
        val report = review(a, b, false)
        assertEquals(setOf(NativeChangeKind.PERSONAL_EDIT), report.kinds)
        assertTrue(report.changes.isEmpty())
        assertFalse(report.unknownNativeBytes)
        assertEquals(setOf(NativeChangeKind.PERSONAL_EDIT, NativeChangeKind.UNKNOWN_NATIVE_DATA),
            review(a, rawChange(b, 0x30), false).kinds)
    }
    @Test fun locationAndSaveCopyChangesAreNotGrowth() {
        val a = record()
        val b = a.copy(recordKey = "copy:2:record:0", position = 2,
            nativeParameters = a.nativeParameters!!.copy(saveBlock = 2))
        assertEquals(setOf(NativeChangeKind.LOCATION), review(a, b).kinds)
    }
    @Test fun formAndIdentityChangesRemainExplicitReview() {
        val a = record()
        val b = rawChange(a, 4).copy(nativeSpeciesId = 21, identityCandidate = 8)
        val report = review(a, b)
        assertTrue(NativeChangeKind.FORM in report.kinds)
        assertTrue(NativeChangeKind.IDENTITY in report.kinds)
        assertFalse(report.canNormalize)
    }
    @Test fun unavailableOrUnknownFormatsCannotClaimNoChange() {
        val a = record()
        assertTrue(NativeChangeKind.NATIVE_DATA_UNAVAILABLE in review(a, a.copy(nativeRecordBase64 = "invalid!")).kinds)
        assertEquals(setOf(NativeChangeKind.UNSUPPORTED_PROFILE), NativeProgressReview.review("other", "other", 1, a, a).kinds)
        assertEquals(setOf(NativeChangeKind.UNSUPPORTED_PROFILE), NativeProgressReview.review(WorldDsSaveReader.GAME_ID,
            WorldDsSaveReader.PROFILE, 2, a, a).kinds)
    }
    @Test fun identicalRecordsOnlyDescribeTheseSnapshotsAndDoNotGrantConversion() {
        val a = record()
        val report = review(a, a)
        assertEquals(setOf(NativeChangeKind.NO_CHANGE), report.kinds)
        assertFalse(report.canNormalize)
    }
}
