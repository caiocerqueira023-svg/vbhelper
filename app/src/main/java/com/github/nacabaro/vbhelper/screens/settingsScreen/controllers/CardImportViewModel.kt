package com.github.nacabaro.vbhelper.screens.settingsScreen.controllers

import android.net.Uri
import android.provider.OpenableColumns
import android.os.CancellationSignal
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.nacabaro.vbhelper.di.VBHelper
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import com.github.nacabaro.vbhelper.source.CardBatchImportState
import com.github.nacabaro.vbhelper.source.CardBatchImporter
import com.github.nacabaro.vbhelper.source.CardImportDocument
import com.github.nacabaro.vbhelper.source.CardImportRunGate
import com.github.nacabaro.vbhelper.source.readCardDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/** Retained across rotation; holds application services and URI descriptors, never an Activity. */
class CardImportViewModel(private val application: VBHelper, private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(CardBatchImportState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null
    private val runs = CardImportRunGate()
    private val settings = application.container.speciesSettingsRepository
    private val recovering = MutableStateFlow(true)
    private val originWork = Mutex()
    private val recovery = viewModelScope.async(Dispatchers.IO) {
        try {
            val unknownIds = application.container.db.cardDao().getAllCards()
                .filter { it.officialStatus == OfficialStatus.UNKNOWN }.mapTo(hashSetOf()) { it.id }
            settings.recoverInterruptedCardOriginImports(unknownIds)
        } finally { recovering.value = false }
    }
    val pendingOrigins = combine(settings.pendingCardOriginPrompts, recovering) { prompts, inRecovery ->
        if (inRecovery) emptyList() else prompts
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun preparePicker(alwaysAskForNewCards: Boolean) { savedState["card_picker_ask_origin"] = alwaysAskForNewCards }

    fun start(uris: List<Uri>) {
        if (mutableState.value.isRunning || uris.isEmpty()) return
        val owner = runs.begin() ?: return
        val unique = uris.distinct()
        val forceOrigin = savedState.get<Boolean>("card_picker_ask_origin") ?: true
        mutableState.value = CardBatchImportState(total = unique.size, isRunning = true, preparing = true)
        val work = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
            val signal = CancellationSignal()
            val cancelProvider = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() }
                finally { withContext(NonCancellable + Dispatchers.IO) {
                    try { signal.cancel() } catch (_: Exception) { }
                } }
            }
            var terminal: CardBatchImportState? = null
            try {
                recovery.await()
                val askOrigin = forceOrigin || settings.promptOriginAtImportTime.first()
                val documents = unique.map { uri ->
                    currentCoroutineContext().ensureActive()
                    CardImportDocument(uri.toString(), readName(uri, signal))
                }
                val importer = CardImportController(application.container.db)
                CardBatchImporter(importOne = { document ->
                    val parsed = readCardDocument(open = {
                        val descriptor = application.contentResolver.openAssetFileDescriptor(document.key.toUri(), "r", signal)
                        try { descriptor?.createInputStream() }
                        catch (e: Exception) {
                            try { descriptor?.close() } catch (_: Exception) { }
                            throw e
                        }
                    }, parse = importer::parseCard)
                    // A failing close cannot occur after this database commit.
                    importer.importParsedCardWithResult(parsed, document.displayName)
                }, onNewCard = { result ->
                    if (askOrigin) settings.setPendingCardOriginPrompt(result.cardId, result.cardName, isImporting = false)
                    // The batch has recorded the committed result before enrichment runs,
                    // so Stop cannot hide a saved card or lose its origin choice.
                    application.container.speciesRepository.matchSpeciesForCard(result.cardId)
                }, onReimport = { result ->
                    if (settings.pendingCardOriginPrompts.first().any { it.cardId == result.cardId })
                        settings.refreshPendingCardName(result.cardId, result.cardName)
                    application.container.speciesRepository.matchSpeciesForCard(result.cardId)
                }).run(documents) { progress ->
                    if (progress.isRunning) publish(owner, progress)
                    else terminal = progress
                }
            } catch (e: CancellationException) {
                terminal = (terminal ?: mutableState.value).copy(cancelled = true)
            } catch (_: Exception) {
                // A preparation failure affects no cards. Report the selected files coherently.
                terminal = CardBatchImportState(total = unique.size, completed = unique.size,
                    failed = unique.size, issues = unique.mapIndexed { index, _ ->
                        com.github.nacabaro.vbhelper.source.CardBatchImportIssue(index + 1, null)
                    })
            } finally {
                withContext(NonCancellable) { cancelProvider.cancelAndJoin() }
                publish(owner, (terminal ?: mutableState.value).copy(isRunning = false, preparing = false, stopping = false))
            }
        }
        job = work
        work.invokeOnCompletion { cause ->
            // Cancellation can happen before the lazy dispatched body ever starts.
            if (runs.owns(owner) && mutableState.value.isRunning) {
                publish(owner, mutableState.value.copy(isRunning = false, preparing = false, stopping = false,
                    cancelled = cause is CancellationException))
            }
            runs.finish(owner)
        }
        work.start()
    }

    private fun publish(owner: Long, progress: CardBatchImportState) {
        if (runs.owns(owner)) mutableState.value = progress.copy(stopping = mutableState.value.stopping && progress.isRunning)
    }

    fun stop() {
        if (mutableState.value.isRunning) {
            mutableState.value = mutableState.value.copy(stopping = true)
            job?.cancel()
        }
    }

    fun dismissResult() { if (!mutableState.value.isRunning) mutableState.value = CardBatchImportState() }

    fun selectOrigin(cardId: Long, status: OfficialStatus) {
        viewModelScope.launch(Dispatchers.IO) {
            recovery.await()
            if (!settings.reservePendingCardOrigin(cardId, status)) return@launch
            try {
                originWork.withLock {
                    // Persist the chosen status before releasing its queue entry.
                    withContext(NonCancellable) {
                        application.container.db.cardDao().updateOfficialStatus(cardId, status)
                        settings.clearPendingCardOriginPrompt(cardId)
                    }
                    if (status != OfficialStatus.UNKNOWN) {
                        try { application.container.speciesRepository.matchSpeciesForCard(cardId) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Timber.w(e, "Species recognition failed after saving card origin") }
                    }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { settings.releasePendingCardOrigin(cardId) }
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not save imported card origin")
                settings.releasePendingCardOrigin(cardId)
            }
        }
    }

    private fun readName(uri: Uri, signal: CancellationSignal): String? = try {
        application.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null, signal)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
        }
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
}
