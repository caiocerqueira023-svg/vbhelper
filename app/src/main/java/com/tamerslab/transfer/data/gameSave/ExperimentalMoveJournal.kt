package com.tamerslab.transfer.data.gameSave

import android.util.AtomicFile
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.SaveObservation
import com.tamerslab.gamesave.NdsStoryProfiles
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.DataInputStream

internal interface ExperimentalEditOperation {
    val operationId: String
    val sourceId: String
    val phase: String
}

internal data class ExperimentalMoveRecord(
    override val operationId: String,
    override val sourceId: String,
    val documentKey: String,
    val sourceRevision: String,
    val candidateRevision: String,
    val observation: SaveObservation,
    val record: ObservedDigimon,
    val individualId: String,
    val bindingId: String,
    val canonicalSpeciesId: String?,
    val createdAt: Long,
    val canonicalAuthority: String? = null,
    override val phase: String = "PREPARED",
) : ExperimentalEditOperation

/** One retained or synthesized native record inserted into a destination; mirrors the move operation shape. */
internal data class ExperimentalSendRecord(
    override val operationId: String,
    override val sourceId: String,
    val documentKey: String,
    val sourceRevision: String,
    val candidateRevision: String,
    val observation: SaveObservation,
    val record: ObservedDigimon,
    val individualId: String,
    val bindingId: String,
    val canonicalSpeciesId: String?,
    val createdAt: Long,
    /** True when the record was synthesized from app state and its local identity is destination-assigned. */
    val generated: Boolean = false,
    /** True when an existing destination record is repaired in place instead of inserted. */
    val replaced: Boolean = false,
    override val phase: String = "PREPARED",
) : ExperimentalEditOperation

/** A storage record admitted to the party in place; no vault acquisition or materialization. */
internal data class ExperimentalAdmitRecord(
    override val operationId: String,
    override val sourceId: String,
    val documentKey: String,
    val sourceRevision: String,
    val candidateRevision: String,
    val observation: SaveObservation,
    val record: ObservedDigimon,
    val createdAt: Long,
    override val phase: String = "PREPARED",
) : ExperimentalEditOperation

/** Durable app-private originals and native payloads precede every external write. */
internal class ExperimentalEditJournal<T : ExperimentalEditOperation>(private val root: File, private val type: Class<T>) {
    private val gson = Gson()
    private fun directory(id: String): File {
        require(Regex("[a-f0-9-]{36}").matches(id))
        return File(root, id)
    }

    suspend fun prepare(record: T, original: ByteArray, candidate: ByteArray) = withContext(Dispatchers.IO) {
        val directory = directory(record.operationId)
        check(!directory.exists() && directory.mkdirs())
        atomicWrite(File(directory, "original.sav"), original)
        atomicWrite(File(directory, "candidate.sav"), candidate)
        update(record)
    }

    suspend fun update(record: T) = withContext(Dispatchers.IO) {
        atomicWrite(File(directory(record.operationId), "operation.json"), gson.toJson(record).toByteArray(Charsets.UTF_8))
    }

    suspend fun original(id: String): ByteArray = withContext(Dispatchers.IO) {
        readSave(File(directory(id), "original.sav"))
    }
    suspend fun candidate(id: String): ByteArray = withContext(Dispatchers.IO) {
        readSave(File(directory(id), "candidate.sav"))
    }
    suspend fun records(): List<T> = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().filter { it.isDirectory }.mapNotNull { directory ->
            val file = AtomicFile(File(directory, "operation.json"))
            if (!file.baseFile.exists()) null else {
                require(file.baseFile.length() <= 256 * 1024)
                file.openRead().bufferedReader().use { gson.fromJson(it, type) }
                    .also { require(it.operationId == directory.name) }
            }
        }
    }
    suspend fun pending(sourceId: String): List<T> = records().filter {
        it.sourceId == sourceId && it.phase !in setOf("ACKNOWLEDGED", "ABORTED")
    }

    private fun atomicWrite(target: File, bytes: ByteArray) {
        val file = AtomicFile(target)
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) }
        catch (failure: Throwable) { file.failWrite(stream); throw failure }
    }

    private fun readSave(file: File): ByteArray {
        val bytes = ByteArray(NdsStoryProfiles.WORLD_DS.fileBytes)
        try {
            AtomicFile(file).openRead().use { stream ->
                val input = DataInputStream(stream)
                input.readFully(bytes)
                require(input.read() == -1) { "Unexpected backup size" }
            }
            return bytes
        } catch (failure: Throwable) { bytes.fill(0); throw failure }
    }
}
