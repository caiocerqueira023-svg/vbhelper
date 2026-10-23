package com.github.nacabaro.vbhelper.gamesave

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WriteRehearsalTest {
    @get:Rule val folder = TemporaryFolder()

    private fun put(bytes: ByteArray, at: Int, value: Long, width: Int = 4) {
        repeat(width) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    private fun fixture(): ByteArray = ByteArray(0x10000) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() }
        put(bytes, 0x208, 10); put(bytes, 0x20C, 7)
        val at = 0x234
        put(bytes, at, 7); put(bytes, at + 4, 20)
        listOf(0x63, 0x6E, 0x7C, 0x7D, 0xFFFF).forEachIndexed { i, code -> put(bytes, at + 8 + i * 2, code.toLong(), 2) }
        put(bytes, at + 0x3C, 100); put(bytes, at + 0x40, 110)
        put(bytes, at + 0x44, 20, 2); put(bytes, at + 0x46, 25, 2)
        for (offset in listOf(0x48, 0x4A, 0x4C, 0x4E)) put(bytes, at + offset, 30, 2)
        put(bytes, at + 0x52, 25, 2); put(bytes, at + 0x74, 3, 2)
        repeat(8) { put(bytes, at + 0x78 + it * 4, (it * 11).toLong()) }
    }

    @Test fun rehearsalVerifiesBytePreservingRewriteOnCopiesOnly() {
        val source = fixture()
        val sourceFile = File(folder.newFolder("source"), "synthetic.sav")
        sourceFile.writeBytes(source)
        val outputDir = folder.newFolder("output")

        val lines = writeRehearsalSummary(sourceFile.absolutePath, outputDir.absolutePath)
        assertTrue(lines.any { it.startsWith("rehearsal=verified") })
        assertTrue(lines.any { it.contains("backup_verified=true preflight_source_unchanged=true readback_verified=true reread_equivalent=true") })
        assertTrue(lines.contains("codec_roundtrip=not-applicable"))
        assertTrue(lines.any { it.contains("journal=PREPARED>BACKED_UP>CANDIDATE_READY>WRITTEN>VERIFIED>ACKNOWLEDGED") })
        assertTrue(lines.any { it.startsWith("writes_to_source=false") })
        assertArrayEquals(source, sourceFile.readBytes())
        assertArrayEquals(source, File(outputDir, "rehearsal-original.backup").readBytes())
        assertArrayEquals(source, File(outputDir, "rehearsal-candidate.bin").readBytes())
    }

    @Test fun rehearsalRefusesAnOutputDirectoryInsideTheSourceFolder() {
        val sourceDir = folder.newFolder("source")
        val sourceFile = File(sourceDir, "synthetic.sav")
        sourceFile.writeBytes(fixture())
        val nested = File(sourceDir, "nested").apply { mkdirs() }
        assertThrows(IllegalArgumentException::class.java) {
            writeRehearsalSummary(sourceFile.absolutePath, nested.absolutePath)
        }
    }
}
