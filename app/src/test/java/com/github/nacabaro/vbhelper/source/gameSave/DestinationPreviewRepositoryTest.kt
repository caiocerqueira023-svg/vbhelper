package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.gamesave.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DestinationPreviewRepositoryTest {
    private val record = ObservedDigimon("bank:1", SaveContainer.BANK, 1, 1, "Partner", 50, 1, 10,
        RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val origin = NativeTransferOrigin("individual", "1", "original", "game", "profile", 1, record, true)
    private val destination = SaveObservation("game", "profile", 1, "revision", emptyList(), emptyList())

    @Test fun preparesFromFreshDestinationAndRechecksBothSides() = runBlocking {
        var reads = 0
        var current = destination
        val repository = DestinationPreviewRepository({ origin }, { reads++; current })
        val prepared = repository.prepare(1, "save", SaveContainer.BANK)
        assertEquals(1, reads)
        assertTrue(repository.isCurrent(prepared))
        current = current.copy(contentRevision = "new-revision")
        assertFalse(repository.isCurrent(prepared))
        assertEquals(3, reads)
    }

    @Test fun nativeRefreshInvalidatesAnEarlierPreview() = runBlocking {
        var currentOrigin = origin
        val repository = DestinationPreviewRepository({ currentOrigin }, { destination })
        val prepared = repository.prepare(1, "save", SaveContainer.BANK)
        currentOrigin = origin.copy(snapshotKey = "2", contentRevision = "new-native")
        assertFalse(repository.isCurrent(prepared))
    }

    @Test fun nativeChangeWhileReadingDestinationRejectsPreparation() = runBlocking {
        var currentOrigin = origin
        val repository = DestinationPreviewRepository({ currentOrigin }, {
            currentOrigin = origin.copy(snapshotKey = "2")
            destination
        })
        try {
            repository.prepare(1, "save", SaveContainer.BANK)
            fail("Concurrent native refresh must invalidate planning")
        } catch (_: DestinationOriginChangedException) { }
    }

    @Test fun unavailableDestinationDoesNotFallBackToCachedObservation() = runBlocking {
        val repository = DestinationPreviewRepository({ origin }, { throw SecurityException("revoked") })
        try {
            repository.prepare(1, "save", SaveContainer.BANK)
            fail("A fresh read is required")
        } catch (_: SecurityException) { }
    }

    @Test fun batchReadsDestinationOnceRechecksEveryOriginAndBindsRecordedProgress() = runBlocking {
        var reads = 0
        var first = origin
        val second = origin.copy(individualId = "individual:2", snapshotKey = "2",
            record = record.copy(recordKey = "bank:2", identityCandidate = 11))
        val repository = DestinationPreviewRepository({ if (it == 1L) first else second }, { reads++; destination })
        val prepared = repository.prepareBatch(listOf(1L, 2L), "save", SaveContainer.BANK)
        assertEquals(1, reads)
        assertEquals(listOf(1L, 2L), prepared.snapshotIds)
        assertEquals(listOf("1", "2"), prepared.preview.items.map { it.snapshotKey })
        assertTrue(repository.isCurrent(prepared))
        first = first.copy(progress = TransferProgressContext(1, 0, 0, ProgressVector.of(), emptyList(), emptyList()))
        assertFalse(repository.isCurrent(prepared))
        assertEquals(3, reads)
    }

    @Test fun batchChangeWhileReadingDestinationRejectsPreparation() = runBlocking {
        var first = origin
        val repository = DestinationPreviewRepository({ if (it == 1L) first else
            origin.copy(individualId = "individual:2", snapshotKey = "2", record = record.copy(identityCandidate = 11))
        }, {
            first = first.copy(snapshotKey = "changed")
            destination
        })
        try {
            repository.prepareBatch(listOf(1L, 2L), "save", SaveContainer.BANK)
            fail("Concurrent native refresh must invalidate batch planning")
        } catch (_: DestinationOriginChangedException) { }
    }

    @Test fun batchRejectsDuplicateSnapshotIdsAndOversizedSelections() = runBlocking {
        val repository = DestinationPreviewRepository(
            { origin.copy(individualId = "individual:$it", snapshotKey = "$it") }, { destination })
        try {
            repository.prepareBatch(listOf(1L, 1L), "save", SaveContainer.BANK)
            fail("Duplicate snapshot ids must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            repository.prepareBatch((1L..17L).toList(), "save", SaveContainer.BANK)
            fail("Oversized selections must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
