package com.github.nacabaro.vbhelper.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class CardImportResult(val cardId: Long, val cardName: String, val isNew: Boolean)
data class CardImportDocument(val key: String, val displayName: String? = null)
data class CardBatchImportIssue(val fileNumber: Int, val displayName: String?, val originOnly: Boolean = false)
data class CardBatchImportState(
    val total: Int = 0,
    val completed: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val failed: Int = 0,
    val issues: List<CardBatchImportIssue> = emptyList(),
    val isRunning: Boolean = false,
    val cancelled: Boolean = false,
    val preparing: Boolean = false,
    val stopping: Boolean = false,
    val currentName: String? = null,
)

/** One parsed card at a time, with no selection-count limit and independent file failures. */
class CardBatchImporter(
    private val importOne: suspend (CardImportDocument) -> CardImportResult,
    private val onNewCard: suspend (CardImportResult) -> Unit,
    private val onReimport: suspend (CardImportResult) -> Unit = {},
) {
    suspend fun run(documents: List<CardImportDocument>, onProgress: (CardBatchImportState) -> Unit): CardBatchImportState {
        val unique = documents.distinctBy { it.key }
        var state = CardBatchImportState(total = unique.size, isRunning = true, currentName = unique.firstOrNull()?.displayName)
        val issues = mutableListOf<CardBatchImportIssue>()
        onProgress(state)
        try {
            unique.forEachIndexed { index, document ->
                currentCoroutineContext().ensureActive()
                val result = try { importOne(document) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    issues += CardBatchImportIssue(index + 1, document.displayName)
                    null
                }
                state = state.copy(
                    completed = index + 1,
                    added = state.added + if (result?.isNew == true) 1 else 0,
                    updated = state.updated + if (result?.isNew == false) 1 else 0,
                    failed = state.failed + if (result == null) 1 else 0,
                    currentName = unique.getOrNull(index + 1)?.displayName,
                )
                if (result != null) {
                    try {
                        // Once committed, finish its origin bookkeeping even if Stop was tapped.
                        withContext(NonCancellable) {
                            if (result.isNew) onNewCard(result) else onReimport(result)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { issues += CardBatchImportIssue(index + 1, document.displayName, originOnly = true) }
                }
                onProgress(state)
                currentCoroutineContext().ensureActive()
            }
        } catch (e: CancellationException) {
            state = state.copy(cancelled = true)
            throw e
        } finally {
            // Error details are copied only at completion, not quadratically for every file.
            state = state.copy(isRunning = false, issues = issues.toList(), currentName = null)
            onProgress(state)
        }
        return state
    }
}
