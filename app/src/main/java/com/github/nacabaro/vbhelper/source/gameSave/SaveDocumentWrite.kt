package com.github.nacabaro.vbhelper.source.gameSave

import android.content.Context
import android.net.Uri
import com.github.nacabaro.vbhelper.gamesave.NdsStoryProfiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

interface SaveDocumentWrite {
    fun canWrite(documentUri: String, grantUri: String): Boolean
    suspend fun write(documentUri: String, bytes: ByteArray)
}

/** Local SAF writer. The coordinator must durably back up and preflight before calling write. */
class AndroidSaveDocumentWrite(context: Context) : SaveDocumentWrite {
    private val resolver = context.applicationContext.contentResolver
    override fun canWrite(documentUri: String, grantUri: String): Boolean {
        val document = Uri.parse(documentUri)
        if (document.scheme != "content") return false
        val grant = Uri.parse(grantUri)
        // A picked document and a discovered document under a granted tree both authorize writes.
        return resolver.persistedUriPermissions.any {
            (it.uri == document || it.uri == grant) && it.isReadPermission && it.isWritePermission
        }
    }

    override suspend fun write(documentUri: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        require(bytes.size == NdsStoryProfiles.WORLD_DS.fileBytes)
        require(Uri.parse(documentUri).scheme == "content")
        requireNotNull(resolver.openOutputStream(Uri.parse(documentUri), "rwt")) { "Writable document unavailable" }.use { output ->
            var offset = 0
            while (offset < bytes.size) {
                coroutineContext.ensureActive()
                val count = minOf(8192, bytes.size - offset)
                output.write(bytes, offset, count)
                offset += count
            }
            output.flush()
        }
    }
}
