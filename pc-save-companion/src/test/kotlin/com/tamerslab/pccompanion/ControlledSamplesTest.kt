package com.tamerslab.pccompanion

import com.tamerslab.gamesave.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class ControlledSamplesTest {
    @get:Rule val temporary = TemporaryFolder()
    private val reader = object : GameSaveReader {
        override val gameId = "fixture"
        override val maxInputBytes = 16
        override fun read(bytes: ByteArray): SaveObservation {
            val farm = bytes[1] == 1.toByte()
            val record = ObservedDigimon("record:1", if (farm) SaveContainer.FARM else SaveContainer.PARTY,
                1, 10, if (bytes[0] == 1.toByte()) "Private renamed name" else "Private original name", 42, 16,
                123, if (farm) RecordMembership.NEEDS_REVIEW else RecordMembership.ACTIVE_RECORD,
                nativeRecordBase64 = "AQID")
            return SaveObservation(gameId, "fixture-v1", 1, "ignored", listOf(record), listOf(ObservationNotice.IDENTITY_RESEARCH_PENDING))
        }
    }

    @Test fun captureAndComparisonKeepOriginalsImmutableAndReportsFreeOfRosterNames() {
        val root = temporary.newFolder("game").toPath()
        val slot = root.resolve("0001.bin")
        val original = ByteArray(16)
        Files.write(slot, original)
        val state = temporary.newFolder("state").toPath()
        val samples = ControlledSamples(state, SaveReaderRegistry(listOf(reader)))
        val before = samples.capture(listOf(root), "session", "before", "0001.bin", ControlledChange.BASELINE)
        assertArrayEquals(original, Files.readAllBytes(slot))
        Files.write(slot, original.copyOf().also { it[0] = 1 }) // Fixture simulates an actual game-written rename.
        val changed = Files.readAllBytes(slot)
        samples.capture(listOf(root), "session", "renamed", "0001.bin", ControlledChange.RENAME)
        assertArrayEquals(changed, Files.readAllBytes(slot))
        val report = samples.compare("session", "before", "renamed")
        assertEquals(1, report.renamedCandidates)
        assertTrue(report.expectedChangeObserved)
        assertFalse(report.establishesNativeIdentity)
        assertEquals(before.contentRevision, samples.summary("session", "before").contentRevision)
        val output = com.google.gson.Gson().toJson(report)
        assertFalse(output.contains("Private"))
        assertThrows(IllegalArgumentException::class.java) { samples.capture(listOf(root), "session", "before", "0001.bin", ControlledChange.BASELINE) }
    }

    @Test fun farmEvidenceCanBeInspectedWithoutPromotingReviewRowsToOwnedMatches() {
        val root = temporary.newFolder("game").toPath()
        val slot = root.resolve("0001.bin")
        Files.write(slot, ByteArray(16))
        val samples = ControlledSamples(temporary.newFolder("state").toPath(), SaveReaderRegistry(listOf(reader)))
        samples.capture(listOf(root), "session", "party", "0001.bin", ControlledChange.BASELINE)
        Files.write(slot, ByteArray(16).also { it[1] = 1 })
        samples.capture(listOf(root), "session", "farm", "0001.bin", ControlledChange.FARM)
        val report = samples.compare("session", "party", "farm")
        assertEquals(0, report.activeCandidatePairs)
        assertEquals(1, report.researchFarmPersistenceCandidates)
        assertFalse(report.establishesNativeIdentity)
    }

    @Test fun rejectsTraversalAndTamperedCapturedBytes() {
        val root = temporary.newFolder("game").toPath()
        Files.write(root.resolve("0001.bin"), ByteArray(16))
        val state = temporary.newFolder("state").toPath()
        val samples = ControlledSamples(state, SaveReaderRegistry(listOf(reader)))
        assertThrows(IllegalArgumentException::class.java) { samples.capture(listOf(root), "../outside", "before", "0001.bin", ControlledChange.BASELINE) }
        assertThrows(IllegalArgumentException::class.java) { samples.capture(listOf(root), "session", "before", "../0001.bin", ControlledChange.BASELINE) }
        samples.capture(listOf(root), "session", "before", "0001.bin", ControlledChange.BASELINE)
        samples.capture(listOf(root), "session", "after", "0001.bin", ControlledChange.RESAVE)
        Files.write(state.resolve("captures/session/capture-after.bin"), ByteArray(16).also { it[0] = 1 })
        assertThrows(IllegalArgumentException::class.java) { samples.compare("session", "before", "after") }
    }

    @Test fun privateCapturesCannotBeWrittenInsideAnActiveSaveFolder() {
        val root = temporary.newFolder("game").toPath()
        Files.write(root.resolve("0001.bin"), ByteArray(16))
        val state = root.resolve("private-state")
        val samples = ControlledSamples(state, SaveReaderRegistry(listOf(reader)))
        assertThrows(IllegalArgumentException::class.java) { samples.capture(listOf(root), "session", "before", "0001.bin", ControlledChange.BASELINE) }
        assertFalse(Files.exists(state))
        assertArrayEquals(ByteArray(16), Files.readAllBytes(root.resolve("0001.bin")))
    }

    @Test fun originalEvidenceCanBeReanalyzedAfterDecoderUpgradeWithoutEditingItsManifest() {
        val root = temporary.newFolder("game").toPath()
        Files.write(root.resolve("0001.bin"), ByteArray(16))
        val state = temporary.newFolder("state").toPath()
        val initial = ControlledSamples(state, SaveReaderRegistry(listOf(reader)))
        initial.capture(listOf(root), "session", "before", "0001.bin", ControlledChange.BASELINE)
        initial.capture(listOf(root), "session", "after", "0001.bin", ControlledChange.RESAVE)
        val manifest = Files.readAllBytes(state.resolve("captures/session/capture-before.json"))
        val newer = object : GameSaveReader by reader {
            override fun read(bytes: ByteArray) = reader.read(bytes).copy(codecVersion = 2)
        }
        val report = ControlledSamples(state, SaveReaderRegistry(listOf(newer))).compare("session", "before", "after")
        assertTrue(report.analysisDiffersFromCapture)
        assertEquals(2, report.codecVersion)
        assertFalse(report.establishesNativeIdentity)
        assertArrayEquals(manifest, Files.readAllBytes(state.resolve("captures/session/capture-before.json")))
    }

    @Test fun packagedCommandModesCaptureAndCompareOfflineWithoutCreatingPairingCredentials() {
        val root = temporary.newFolder("cli-game").toPath()
        val state = temporary.newFolder("cli-state").toPath()
        val plain = ByteArray(TimeStrangerSaveReader.FILE_BYTES)
        "5, 3097152, 176, 0, Synthetic CLI fixture".toByteArray().copyInto(plain)
        val offset = 0x1000 + 2 * 0x150
        val data = java.nio.ByteBuffer.wrap(plain).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        data.putInt(offset, 10)
        "CLI private fixture".toByteArray().copyInto(plain, offset + 4)
        data.putInt(offset + 4 + 0x60, 42)
        plain[offset + 4 + 0xEE] = 16
        data.putInt(offset + 4 + 0x104, 10)
        data.putInt(offset + 4 + 0x140, 1)
        data.putInt(offset + 4 + 0x148, 123)
        val cipher = javax.crypto.Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec("3962776754555883".toByteArray(), "AES"))
        val encrypted = cipher.doFinal(plain)
        Files.write(root.resolve("0001.bin"), encrypted)
        val output = java.io.ByteArrayOutputStream()
        val originalOutput = System.out
        try {
            System.setOut(java.io.PrintStream(output, true, "UTF-8"))
            main(arrayOf("--save-dir", root.toString(), "--state-dir", state.toString(), "--capture", "0001.bin",
                "--session", "cli", "--label", "before", "--expected", "baseline"))
            main(arrayOf("--save-dir", root.toString(), "--state-dir", state.toString(), "--capture", "0001.bin",
                "--session", "cli", "--label", "after", "--expected", "resave"))
            main(arrayOf("--state-dir", state.toString(), "--compare-captures", "before", "after", "--session", "cli"))
        } finally { System.setOut(originalOutput) }
        val text = output.toString("UTF-8")
        assertTrue(text.contains("\"expectedChangeObserved\":true"))
        assertTrue(text.contains("\"establishesNativeIdentity\":false"))
        assertFalse(text.contains("CLI private fixture"))
        assertFalse(text.contains("vbhelper-companion://"))
        assertFalse(Files.exists(state.resolve("identity.p12")))
        assertArrayEquals(encrypted, Files.readAllBytes(root.resolve("0001.bin")))
    }
}
