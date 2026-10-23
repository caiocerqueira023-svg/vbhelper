package com.github.nacabaro.vbhelper.pccompanion

import com.github.nacabaro.vbhelper.gamesave.SaveReadException
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WorldDsResearchCommandTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun standaloneInspectionExitsOfflineAndPreservesTheSource() {
        val file = temporary.newFolder("game").toPath().resolve("World DS.sav")
        val source = ByteArray(0x10000)
        "DSDIGI\u0000\u0000".toByteArray().copyInto(source, 0x10)
        Files.write(file, source)
        val out = ByteArrayOutputStream()
        val original = System.out
        try {
            System.setOut(PrintStream(out, true, "UTF-8"))
            main(arrayOf("--inspect-world-ds", file.toString()))
        } finally { System.setOut(original) }
        val text = out.toString("UTF-8")
        assertTrue(text.contains("research_only=true"))
        assertTrue(text.contains("blocks=1"))
        assertFalse(text.contains(file.toString()))
        assertFalse(text.contains("vbhelper://"))
        assertArrayEquals(source, Files.readAllBytes(file))
        assertEquals(1, Files.list(file.parent).use { it.count() }.toInt())
    }

    @Test fun inspectionCannotBeMixedWithNetworkOrCaptureOptions() {
        val privateState = temporary.root.toPath().resolve("must-not-exist")
        assertThrows(IllegalArgumentException::class.java) {
            main(arrayOf("--inspect-world-ds", "file.sav", "--state-dir", privateState.toString()))
        }
        assertFalse(Files.exists(privateState))
    }

    @Test fun unsupportedInputFailsWithoutCreatingCompanionState() {
        val file = temporary.newFile("invalid.sav").toPath()
        assertThrows(SaveReadException::class.java) { main(arrayOf("--inspect-world-ds", file.toString())) }
        assertEquals(0L, Files.size(file))
    }
}
