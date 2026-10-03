package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CardBatchImporterTest {
    private fun document(number: Int) = CardImportDocument("document-$number", "card-$number.bin")

    @Test fun mixedBatchPromptsOnlyForNewCardsAndReportsBothImportKinds() = runTest {
        val prompts = mutableListOf<Long>()
        val progress = mutableListOf<CardBatchImportState>()
        val runner = CardBatchImporter(importOne = { doc ->
            val id = doc.key.substringAfterLast('-').toLong()
            CardImportResult(id, "Card $id", isNew = id != 2L)
        }, onNewCard = { prompts += it.cardId })
        val result = runner.run((1..3).map(::document)) { progress += it }
        assertEquals(listOf(1L, 3L), prompts)
        assertEquals(2, result.added)
        assertEquals(1, result.updated)
        assertEquals(0, result.failed)
        assertEquals(3, result.completed)
        assertFalse(result.isRunning)
        assertEquals(listOf(0, 1, 2, 3, 3), progress.map { it.completed })
    }

    @Test fun oneInvalidFileDoesNotStopLaterImportsOrCreateAnOriginPrompt() = runTest {
        val visited = mutableListOf<String>()
        val prompts = mutableListOf<Long>()
        val runner = CardBatchImporter(importOne = { doc ->
            visited += doc.key
            if (doc.key == "document-2") throw IllegalArgumentException("Invalid card")
            CardImportResult(doc.key.substringAfterLast('-').toLong(), doc.displayName!!, true)
        }, onNewCard = { prompts += it.cardId })
        val result = runner.run((1..3).map(::document)) {}
        assertEquals(listOf("document-1", "document-2", "document-3"), visited)
        assertEquals(listOf(1L, 3L), prompts)
        assertEquals(2, result.added)
        assertEquals(1, result.failed)
        assertEquals("card-2.bin", result.issues.single().displayName)
        assertFalse(result.issues.single().originOnly)
    }

    @Test fun duplicateDocumentUrisAreReadOnceButDistinctAliasesAreDetectedAsReimports() = runTest {
        var reads = 0
        val prompts = mutableListOf<Long>()
        val runner = CardBatchImporter(importOne = {
            reads++
            CardImportResult(7, "Shared card", isNew = reads == 1)
        }, onNewCard = { prompts += it.cardId })
        val result = runner.run(listOf(document(1), document(1), document(2))) {}
        assertEquals(2, reads)
        assertEquals(2, result.total)
        assertEquals(1, result.added)
        assertEquals(1, result.updated)
        assertEquals(listOf(7L), prompts)
    }

    @Test fun arbitraryBatchSizeHasNoArtificialFileCountLimit() = runTest {
        var prompts = 0
        val runner = CardBatchImporter(importOne = {
            CardImportResult(it.key.substringAfterLast('-').toLong(), it.displayName!!, true)
        }, onNewCard = { prompts++ })
        val result = runner.run((1..1001).map(::document)) {}
        assertEquals(1001, result.completed)
        assertEquals(1001, prompts)
    }

    @Test fun cancellationStopsTheRemainingFilesAndRetainsCompletedProgress() = runTest {
        val enteredSecond = CompletableDeferred<Unit>()
        val waitForever = CompletableDeferred<Unit>()
        val visited = mutableListOf<String>()
        var latest = CardBatchImportState()
        val runner = CardBatchImporter(importOne = {
            visited += it.key
            if (it.key == "document-2") { enteredSecond.complete(Unit); waitForever.await() }
            CardImportResult(1, "One", true)
        }, onNewCard = {})
        val job = launch { runner.run((1..3).map(::document)) { latest = it } }
        enteredSecond.await()
        job.cancelAndJoin()
        assertEquals(listOf("document-1", "document-2"), visited)
        assertEquals(1, latest.completed)
        assertEquals(1, latest.added)
        assertTrue(latest.cancelled)
        assertFalse(latest.isRunning)
    }

    @Test fun originQueueFailureDoesNotMisreportACommittedCardAsAnImportFailure() = runTest {
        val runner = CardBatchImporter(importOne = { CardImportResult(7, "Saved", true) },
            onNewCard = { throw IllegalStateException("Preferences unavailable") })
        val result = runner.run(listOf(document(1))) {}
        assertEquals(1, result.added)
        assertEquals(0, result.failed)
        assertTrue(result.issues.single().originOnly)
    }

    @Test fun reimportsNeverPromptAndCanRefreshAnAlreadyQueuedDisplayName() = runTest {
        val reimportedNames = mutableListOf<String>()
        val runner = CardBatchImporter(importOne = { CardImportResult(7, "New filename", false) },
            onNewCard = { fail("Re-import must not ask origin") },
            onReimport = { reimportedNames += it.cardName })
        val result = runner.run(listOf(document(1))) {}
        assertEquals(listOf("New filename"), reimportedNames)
        assertEquals(1, result.updated)
    }
}
