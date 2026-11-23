package com.tamerslab.transfer.data.gameSave

import android.util.Log
import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.transfer.LinkedGameSave
import com.tamerslab.domain.transfer.LinkedSaveObservation
import com.tamerslab.gamesave.SaveObservation
import com.tamerslab.gamesave.SaveReadException
import com.tamerslab.gamesave.SaveReaderRegistry
import com.tamerslab.gamesave.CapturedSave
import com.tamerslab.gamesave.WorldDsCopySelection
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class LinkedSaveView(val source: LinkedGameSave, val observation: SaveObservation?)
class SaveDiscoverySuppressedException : IllegalStateException("Source was explicitly unlinked")

class LinkedGameSaveRepository(
    private val db: AppDatabase,
    private val documents: SaveDocumentAccess,
    private val registry: SaveReaderRegistry = SaveReaderRegistry(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val beforeRefresh: suspend () -> Unit = {},
    private val beforeUnlink: suspend (LinkedGameSave) -> Unit = {},
    private val explicitLink: suspend (String) -> Unit = {},
    private val allowDiscovered: suspend (String) -> Boolean = { true },
    private val afterRefresh: suspend (LinkedSaveView) -> Unit = {},
) {
    private val dao get() = db.linkedGameSaveDao()
    private val gson = Gson()
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val readPermits = Semaphore(3)
    private val mutableRefreshing = MutableStateFlow<Set<String>>(emptySet())
    val refreshing = mutableRefreshing.asStateFlow()
    val sources = dao.observe().map { rows -> rows.map { row ->
        val cached = row.observation?.let {
            runCatching { gson.fromJson(it.snapshotJson, SaveObservation::class.java) }.getOrNull()
                ?.takeIf { parsed -> parsed.contentRevision == it.contentRevision && parsed.codecVersion == it.codecVersion }
        }
        LinkedSaveView(row.source, cached)
    } }.flowOn(Dispatchers.IO)

    suspend fun link(document: SaveDocument, discovered: Boolean = false): String {
        if (!discovered) explicitLink(document.key)
        val id = db.withTransaction {
            if (discovered && !allowDiscovered(document.key)) throw SaveDiscoverySuppressedException()
            dao.byDocument(document.key)?.sourceId ?: UUID.randomUUID().toString().let { candidateId ->
                dao.insert(LinkedGameSave(candidateId, document.key, document.uri, document.grantUri,
                    document.name, document.name, clock()))
                requireNotNull(dao.byDocument(document.key)).sourceId
            }
        }
        relink(id, document)
        return id
    }

    suspend fun refreshAll() = coroutineScope {
        val discovery = launch { beforeRefresh() }
        val initial = dao.all().map { it.sourceId }.toSet()
        initial.forEach { id -> launch { refresh(id) } }
        discovery.join()
        dao.all().filter { it.sourceId !in initial }.forEach { source -> launch { refresh(source.sourceId) } }
    }

    /** Reselecting the same document repairs its grant; another save stays a distinct source. */
    suspend fun relink(id: String, document: SaveDocument) {
        locks.getOrPut(id) { Mutex() }.withLock {
            db.withTransaction {
                val source = requireNotNull(dao.get(id))
                require(source.documentKey == document.key) { "Different source document" }
                if (source.documentUri != document.uri || source.grantUri != document.grantUri) {
                    dao.updateAccess(id, document.uri, document.grantUri, document.name)
                }
            }
        }
    }

    suspend fun refresh(id: String): Unit = withContext(Dispatchers.IO) {
        val lock = locks.getOrPut(id) { Mutex() }
        if (!lock.tryLock()) return@withContext // Use the visible in-flight result, without queuing more reads.
        mutableRefreshing.update { it + id }
        try {
            readPermits.withPermit {
                try {
                    val observation = readLocked(id).use { it.observation }
                    val source = dao.get(id)
                    if (source != null) {
                        try { afterRefresh(LinkedSaveView(source, observation)) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { Log.e("LinkedGameSave", "After-refresh reconciliation failed", failure) }
                    }
                } catch (_: TimeoutCancellationException) { /* A source timeout must not cancel other refreshes. */ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Source health was recorded by readLocked. */ }
            }
        } finally {
            mutableRefreshing.update { it - id }
            lock.unlock()
        }
    }

    /** Preview preparation waits for earlier work, then performs its own fresh bounded read. */
    suspend fun readFresh(id: String): SaveObservation = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            mutableRefreshing.update { it + id }
            try { readPermits.withPermit { readLocked(id).use { it.observation } } }
            finally { mutableRefreshing.update { it - id } }
        }
    }

    suspend fun readCapturedFresh(id: String): CapturedSave = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            mutableRefreshing.update { it + id }
            try { readPermits.withPermit { readLocked(id, capture = true) } }
            finally { mutableRefreshing.update { it - id } }
        }
    }

    /** Serialize an explicit move with refresh/relink and expose fresh reads under the same source lock. */
    internal suspend fun <T> withMoveSourceLock(id: String, action: suspend (suspend () -> CapturedSave) -> T): T =
        withContext(Dispatchers.IO) {
            locks.getOrPut(id) { Mutex() }.withLock {
                mutableRefreshing.update { it + id }
                try { readPermits.withPermit { action { readLocked(id, capture = true) } } }
                finally { mutableRefreshing.update { it - id } }
            }
        }

    private suspend fun readLocked(id: String, capture: Boolean = false): CapturedSave {
        val source = requireNotNull(dao.get(id)) { "Source unavailable" }
        dao.markAttempt(id, clock())
        fun sameAccess(current: LinkedGameSave?) = current != null && current.documentUri == source.documentUri && current.grantUri == source.grantUri
        var captured: CapturedSave? = null
        try {
            captured = withTimeout(30_000) { if (capture) documents.capture(source.documentUri, registry)
                else CapturedSave(documents.read(source.documentUri, registry), null) }
            val reviewed = WorldDsCopySelection.apply(captured.observation, source.reviewedCopyRevision, source.reviewedSaveCopy)
            val result = if (reviewed === captured.observation) WorldDsCopySelection.applyAutomatic(reviewed) else reviewed
            val now = clock()
            db.withTransaction {
                check(sameAccess(dao.get(id))) { "Source access changed during read" }
                // A game session that saved over our write leaves no native record behind; release
                // those individuals so they can be sent again instead of staying "away" forever.
                val presences = db.nativeMaterializationDao().presenceForSource(id)
                PresenceReconciliation.vanished(presences, result.records)
                    .forEach { db.nativeMaterializationDao().clearPresence(it.individualId) }
                dao.saveObservation(LinkedSaveObservation(id, result.contentRevision, now,
                    result.gameId, result.formatProfile, result.codecVersion, gson.toJson(result)))
                dao.markSuccess(id, now)
            }
            return if (result === captured.observation) captured else CapturedSave(result, captured.container)
        } catch (failure: Exception) {
            captured?.close()
            if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
            val code = when (failure) {
                is TimeoutCancellationException -> "TIMEOUT"
                is SecurityException -> "PERMISSION"
                is SaveReadException -> failure.reason.name
                else -> "UNAVAILABLE"
            }
            db.withTransaction { if (sameAccess(dao.get(id))) dao.markFailure(id, code) }
            throw failure
        }
    }

    suspend fun rename(id: String, label: String) {
        val cleaned = label.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 160)
        dao.rename(id, cleaned)
    }

    /** Re-read before recording authority. This affects app review only, never the game document. */
    suspend fun selectWorldDsCopy(id: String, expectedRevision: String, copy: Int) = withContext(Dispatchers.IO) {
        locks.getOrPut(id) { Mutex() }.withLock {
            mutableRefreshing.update { it + id }
            try {
                readPermits.withPermit { readLocked(id).use { captured ->
                    val fresh = captured.observation
                    if (fresh.contentRevision != expectedRevision || !TransferGameCapabilities.isNdsStory(fresh))
                        throw ImportSourceChangedException()
                    val reviewed = WorldDsCopySelection.apply(fresh, expectedRevision, copy)
                    db.withTransaction {
                        check(dao.get(id) != null)
                        dao.selectCopy(id, expectedRevision, copy)
                        dao.saveObservation(LinkedSaveObservation(id, reviewed.contentRevision, clock(), reviewed.gameId,
                            reviewed.formatProfile, reviewed.codecVersion, gson.toJson(reviewed)))
                    }
                } }
            } finally { mutableRefreshing.update { it - id } }
        }
    }

    suspend fun unlink(id: String) = locks.getOrPut(id) { Mutex() }.withLock {
        db.withTransaction {
            dao.get(id)?.let { beforeUnlink(it) }
            dao.unlink(id)
        }
    }
}
