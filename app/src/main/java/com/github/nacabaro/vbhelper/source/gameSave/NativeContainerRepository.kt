package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.transfer.*
import com.github.nacabaro.vbhelper.gamesave.CapturedSave
import java.security.MessageDigest

/** Retains the exact source document, not an assertion that all sidecars/return context are known. */
class NativeContainerRepository(private val db: AppDatabase) {
    private val dao get() = db.transferContinuityDao()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

    suspend fun retain(captured: CapturedSave, snapshots: List<Long>) {
        check(db.inTransaction())
        val bytes = captured.container ?: return
        require(bytes.size in 1..(64 * 1024 * 1024) && hash(bytes) == captured.observation.contentRevision)
        val revision = captured.observation.contentRevision
        if (dao.container(NativeSaveContainer(revision, bytes.size, System.currentTimeMillis())) != -1L) {
            dao.chunks((bytes.indices step 65536).map { offset ->
                NativeSaveContainerChunk(revision, offset / 65536, bytes.copyOfRange(offset, minOf(bytes.size, offset + 65536)))
            })
        }
        dao.bindContainers(snapshots.map { NativeSnapshotContainer(it, revision) })
    }

    suspend fun originalDocument(snapshot: Long): ByteArray? {
        val container = dao.containerForSnapshot(snapshot) ?: return null
        require(container.byteCount in 1..(64 * 1024 * 1024))
        val result = ByteArray(container.byteCount)
        var offset = 0
        try {
            dao.chunks(container.revision).forEachIndexed { index, chunk ->
                require(chunk.chunkIndex == index && chunk.bytes.size in 1..65536 && offset + chunk.bytes.size <= result.size)
                chunk.bytes.copyInto(result, offset); offset += chunk.bytes.size
            }
            require(offset == result.size && hash(result) == container.revision) { "Native document archive is incomplete" }
            return result
        } catch (failure: Exception) { result.fill(0); throw failure }
    }
}
