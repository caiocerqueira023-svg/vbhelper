package com.github.nacabaro.vbhelper.source.gameSave

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.github.nacabaro.vbhelper.gamesave.SaveDiscoveryPolicy
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.gamesave.SaveReaderRegistry
import com.github.nacabaro.vbhelper.gamesave.CapturedSave
import com.github.nacabaro.vbhelper.source.readCardDocument
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.ArrayDeque

data class SaveDocument(val key: String, val uri: String, val grantUri: String, val name: String)
data class SaveFolderResult(val documents: List<SaveDocument>, val truncated: Boolean)

fun interface SaveDocumentAccess {
    suspend fun read(uri: String, registry: SaveReaderRegistry): SaveObservation
    suspend fun capture(uri: String, registry: SaveReaderRegistry): CapturedSave = CapturedSave(read(uri, registry), null)
}

/** Read access only: no save writes, ownership markers, broad filesystem permissions or network access. */
class AndroidSaveDocuments(context: Context) : SaveDocumentAccess {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    suspend fun persistReadGrant(uri: Uri) = withContext(Dispatchers.IO) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission }) {
            throw SecurityException("Persistent read access unavailable")
        }
    }

    /** Keep granted write access for explicit native moves; read-only providers remain usable for browsing. */
    suspend fun persistTransferGrant(uri: Uri) = withContext(Dispatchers.IO) {
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: SecurityException) { persistReadGrant(uri) }
        if (resolver.persistedUriPermissions.none { it.uri == uri && it.isReadPermission })
            throw SecurityException("Persistent read access unavailable")
    }

    suspend fun describe(uri: Uri, grantUri: Uri = uri): SaveDocument = withContext(Dispatchers.IO) {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME)) {
            if (it.moveToFirst()) it.getString(0) else null
        }?.take(160) ?: "save.bin"
        val documentId = if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri) else uri.toString()
        SaveDocument("${uri.authority}:$documentId", uri.toString(), grantUri.toString(), name)
    }

    override suspend fun read(uri: String, registry: SaveReaderRegistry): SaveObservation =
        readCardDocument({ resolver.openInputStream(Uri.parse(uri)) }, registry::read)
    override suspend fun capture(uri: String, registry: SaveReaderRegistry): CapturedSave =
        readCardDocument({ resolver.openInputStream(Uri.parse(uri)) }, registry::capture)

    /** Enumerate only granted trees with explicit bounds and a visited set. */
    suspend fun discover(tree: Uri): SaveFolderResult = withTimeout(30_000) { withContext(Dispatchers.IO) {
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree))
        val root = DocumentsContract.getTreeDocumentId(tree)
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.add(root to 0)
        val visited = mutableSetOf<String>()
        val found = linkedMapOf<String, SaveDocument>()
        var seen = 0
        var truncated = false
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (id, depth) = pending.removeFirst()
            if (!visited.add(id)) continue
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE)) { rows ->
                while (rows.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    if (++seen > 2000 || found.size >= 32) { truncated = true; break }
                    val childId = rows.getString(0)
                    val name = rows.getString(1).orEmpty()
                    val directory = rows.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                    if (directory) {
                        if (depth < 4 && !name.equals("backups", true)) pending.add(childId to depth + 1)
                        else if (depth >= 4) truncated = true
                    } else if (SaveDiscoveryPolicy.isCandidate(name, if (rows.isNull(3)) null else rows.getLong(3))) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, childId)
                        val key = "${tree.authority}:$childId"
                        found[key] = SaveDocument(key, uri.toString(), tree.toString(), name.take(160))
                    }
                }
            }
            if (seen > 2000 || found.size >= 32) {
                if (pending.isNotEmpty()) truncated = true
                break
            }
        }
        SaveFolderResult(found.values.toList(), truncated)
    } }

    /** A blocked provider query receives cancellation too, not just the file's input stream. */
    private suspend fun <T> query(uri: Uri, projection: Array<String>, read: suspend (Cursor) -> T): T =
        withTimeout(30_000) { withContext(Dispatchers.IO) { coroutineScope {
            val signal = CancellationSignal()
            val watcher = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { signal.cancel() }
            }
            try {
                val cursor = resolver.query(uri, projection, null, null, null, signal)
                    ?: throw IOException("Document unavailable")
                cursor.use { read(it) }
            } finally { watcher.cancelAndJoin() }
        } } }
}
