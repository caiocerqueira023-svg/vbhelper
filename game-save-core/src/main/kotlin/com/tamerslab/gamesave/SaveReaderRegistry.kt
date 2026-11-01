package com.tamerslab.gamesave

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

class SaveReaderRegistry(private val readers: List<GameSaveReader> = defaultReaders()) {
    init { require(readers.isNotEmpty() && readers.all { it.maxInputBytes in 1..(64 * 1024 * 1024) }) }
    val maxInputBytes: Int = readers.maxOf { it.maxInputBytes }

    companion object {
        /**
         * Every registered NDS Story layout becomes one reader, so adding a profile never means
         * editing this registry. Layout resolution is content-led; a profile whose bytes cannot
         * decode throws its own failure and never silently claims another profile's save.
         */
        fun defaultReaders(): List<GameSaveReader> =
            listOf(TimeStrangerSaveReader()) + NdsStoryProfiles.all.map { NdsStorySaveReader(it) }
    }

    /** Registration order is a contract: broad readers first, specific readers last (see SAVE_READING_STANDARD.md). */
    fun read(input: InputStream): SaveObservation = capture(input).use { it.observation }

    fun capture(input: InputStream): CapturedSave {
        val bytes = readBoundedSaveBytes(input, maxInputBytes)
        try {
            val candidates = readers.filter { it.acceptsLength(bytes.size) }
            val decoded = mutableListOf<SaveObservation>()
            var failure: SaveReadException? = null
            // Readers are ordered broad to specific: the most specific candidate's failure reason wins.
            for (reader in candidates) {
                try { decoded += reader.read(bytes) }
                catch (error: SaveReadException) { failure = error }
            }
            if (decoded.size > 1) throw SaveReadException(SaveReadFailure.AMBIGUOUS_FORMAT)
            val observation = decoded.singleOrNull()?.copy(contentRevision = revision(bytes))
                ?: throw (failure ?: SaveReadException(SaveReadFailure.UNSUPPORTED_FORMAT))
            return CapturedSave(observation, bytes)
        } catch (failure: Throwable) { bytes.fill(0); throw failure }
    }
}

/** Shared stream boundary; callers retain ownership of the stream and clear returned working bytes. */
internal fun readBoundedSaveBytes(input: InputStream, maxInputBytes: Int): ByteArray {
    require(maxInputBytes in 1..(64 * 1024 * 1024))
    val output = object : ByteArrayOutputStream() {
        fun clearWorkingBytes() { buf.fill(0); reset() }
    }
    val buffer = ByteArray(32 * 1024)
    try {
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, maxInputBytes + 1 - output.size()))
            if (count < 0) return output.toByteArray()
            if (count == 0) {
                val value = input.read()
                if (value < 0) return output.toByteArray()
                output.write(value)
            } else output.write(buffer, 0, count)
            if (output.size() > maxInputBytes) throw SaveReadException(SaveReadFailure.INPUT_TOO_LARGE)
        }
    } finally { buffer.fill(0); output.clearWorkingBytes() }
}

internal fun revision(bytes: ByteArray): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
    val digits = "0123456789abcdef"
    return String(CharArray(hash.size * 2) { index ->
        val value = hash[index / 2].toInt() and 255
        digits[if (index % 2 == 0) value ushr 4 else value and 15]
    })
}
