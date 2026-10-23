package com.github.nacabaro.vbhelper.source.gameSave

import android.net.Uri
import android.provider.DocumentsContract
import androidx.room.withTransaction
import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID

class SaveLocationRepository(private val db: AppDatabase, private val local: AndroidSaveDocuments, private val pc: LocalCompanionClient) {
    private val dao get() = db.transferContinuityDao()
    private val scans = Mutex()
    val locations = dao.locations()

    suspend fun linkTree(tree: Uri): LinkedSaveLocation {
        // Native moves need write access; keep browsing available when the provider is read-only.
        local.persistTransferGrant(tree)
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val label = local.describe(root, tree).name
        return register("TREE", tree.toString(), label)
    }
    suspend fun linkPc(id: String, label: String) = register("PC", "companion://$id", label)
    private suspend fun register(kind: String, locator: String, label: String) = db.withTransaction {
        val existing = dao.location(kind, locator)
        (existing?.copy(label = label) ?: LinkedSaveLocation(UUID.randomUUID().toString(), kind, locator, label, System.currentTimeMillis()))
            .also { dao.saveLocation(it) }
    }

    suspend fun rescanAll(link: suspend (SaveDocument) -> String) = withContext(Dispatchers.IO) {
        if (!scans.tryLock()) return@withContext
        try {
            val permits = Semaphore(3)
            coroutineScope { dao.allLocations().forEach { location -> launch {
                permits.withPermit {
                    try {
                        val found = if (location.kind == "TREE") local.discover(Uri.parse(location.locator))
                            else pc.discover(requireNotNull(Uri.parse(location.locator).host))
                        val ignored = dao.exclusions(location.locationId).toSet()
                        found.documents.filterNot { it.key in ignored }.forEach {
                            try { link(it) } catch (_: SaveDiscoverySuppressedException) { /* Explicit unlink wins an overlapping scan. */ }
                        }
                        dao.locationHealth(location.locationId, System.currentTimeMillis(), null, found.truncated)
                    } catch (_: TimeoutCancellationException) { dao.locationHealth(location.locationId, System.currentTimeMillis(), "TIMEOUT", false) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: SecurityException) { dao.locationHealth(location.locationId, System.currentTimeMillis(), "PERMISSION", false) }
                    catch (_: Exception) { dao.locationHealth(location.locationId, System.currentTimeMillis(), "UNAVAILABLE", false) }
                }
            } } }
        } finally { scans.unlock() }
    }
    suspend fun exclude(source: LinkedGameSave) {
        for (location in dao.allLocations()) dao.exclude(SaveLocationExclusion(location.locationId, source.documentKey))
    }
    suspend fun include(documentKey: String) = dao.include(documentKey)
    suspend fun forget(location: LinkedSaveLocation) = db.withTransaction {
        dao.forgetLocation(location.locationId)
        if (location.kind == "PC") dao.forgetConnection(requireNotNull(Uri.parse(location.locator).host))
    }
}
