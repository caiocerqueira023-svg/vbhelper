package com.github.cfogrady.vbnfc

import com.github.cfogrady.vbnfc.data.DeviceType
import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcDataTranslator
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

/** Real encryption, checksums and translator, with only the NFC radio replaced. */
class TransferProtocolIntegrityTest {
    private val aes = "test-only-aes-key-1234567"
    private val crypto = CryptographicTransformer(
        CryptographicTransformerHelper.generateHMacKey(aes, "test-hmac-1"),
        CryptographicTransformerHelper.generateHMacKey(aes, "test-hmac-2"), aes, IntArray(16) { it },
    )
    private val translator = VBNfcDataTranslator(crypto)

    private fun character() = VBNfcCharacter(
        dimId = 27u, charIndex = 2u, stage = 2, ageInDays = 4, generation = 2u,
        vitalPoints = 9999u, trophies = 30u, currentPhaseBattlesWon = 25u, currentPhaseBattlesLost = 1u,
        totalBattlesWon = 40u, totalBattlesLost = 10u, transformationCountdownInMinutes = 1u,
        appReserved1 = ByteArray(12) { (it + 1).toByte() },
        transformationHistory = Array(9) { i -> if (i < 3) NfcCharacter.Transformation(i.toUByte(), 2026u, 9u, 12u)
            else NfcCharacter.Transformation(255u, 65535u, 255u, 255u) },
    )

    private inner class Radio : NfcTransport {
        override val tagId = byteArrayOf(1, 2, 3, 4, 5, 6, 7)
        val events = mutableListOf<String>()
        var nakPage: Int? = null
        var droppedPage: Int? = null
        var failCommit = false
        var shortRead = false
        var authResponse = byteArrayOf(0, 0)
        val header = ByteArray(16).apply {
            this[4] = (DeviceType.VitalSeriesDeviceType.toInt() ushr 8).toByte()
            this[5] = DeviceType.VitalSeriesDeviceType.toByte()
            this[8] = 3; this[9] = 27; this[10] = 1
        }
        val memory = crypto.encryptData(ByteArray(864).also {
            translator.setCharacterInByteArray(character().apply { appReserved1 = ByteArray(12) }, it)
            translator.finalizeByteArrayFormat(it)
        }, tagId)
        val communicator = TagCommunicator(this, ChecksumCalculator(),
            NfcDataTranslatorFactory(mutableMapOf(DeviceType.VitalSeriesDeviceType to translator)))

        override fun transceive(command: ByteArray): ByteArray { return when (command[0]) {
            TagCommunicator.NFC_PASSWORD_COMMAND -> authResponse
            TagCommunicator.NFC_READ_COMMAND -> {
                val page = command[1].toInt() and 255
                if (page == 4) header.copyOf()
                else if (shortRead) byteArrayOf(0)
                else memory.copyOfRange((page - 8) * 4, (page - 8) * 4 + 16)
            }
            TagCommunicator.NFC_WRITE_COMMAND -> {
                val page = command[1].toInt() and 255
                if (page == 6) {
                    val operation = command[4].toInt()
                    if (operation == 2 || operation == 4) {
                        events += "commit-$operation"
                        if (failCommit) throw IOException("Lost final acknowledgement")
                    }
                } else {
                    if (page == nakPage) return byteArrayOf(5)
                    if (page != droppedPage) command.copyInto(memory, (page - 8) * 4, 2, 6)
                }
                byteArrayOf(0x0a)
            }
            else -> error("Unexpected command")
        }
        }
    }

    private fun fails(action: () -> Unit) {
        try { action() } catch (_: Exception) { return }
        fail("Expected transfer failure")
    }

    @Test fun receiveCommitsOnlyAfterCallerHasPersistedTheCharacter() {
        val radio = Radio()
        radio.communicator.receiveCharacter { radio.events += "database-committed"; true }
        assertEquals(listOf("database-committed", "commit-2"), radio.events)
    }

    @Test fun rejectedOrFailedImportNeverRemovesWatchCopy() {
        val rejected = Radio()
        rejected.communicator.receiveCharacter { false }
        assertTrue(rejected.events.isEmpty())
        val failed = Radio()
        fails { failed.communicator.receiveCharacter { throw IOException("Database full") } }
        assertTrue(failed.events.isEmpty())
    }

    @Test fun failedReceiveAcknowledgementHappensAfterDurableSave() {
        val radio = Radio().apply { failCommit = true }
        fails { radio.communicator.receiveCharacter { radio.events += "saved"; true } }
        assertEquals(listOf("saved", "commit-2"), radio.events)
    }

    @Test fun fullEncryptedSendReadbackPreservesIdentityAndEvolutionStats() {
        val radio = Radio()
        val sent = character()
        radio.communicator.sendCharacter(sent)
        val bytes = crypto.decryptData(radio.memory, radio.tagId)
        ChecksumCalculator().checkChecksums(bytes)
        val restored = translator.parseNfcCharacter(bytes)
        assertArrayEquals(sent.appReserved1, restored.appReserved1)
        assertEquals(sent.vitalPoints, restored.vitalPoints)
        assertEquals(sent.trophies, restored.trophies)
        assertEquals(sent.currentPhaseBattlesWon, restored.currentPhaseBattlesWon)
        assertEquals(sent.currentPhaseBattlesLost, restored.currentPhaseBattlesLost)
        assertEquals(sent.generation, restored.generation)
        assertArrayEquals(sent.transformationHistory, restored.transformationHistory)
        assertEquals(listOf("commit-4"), radio.events)
    }

    @Test fun negativeAcknowledgementAtAnyDataPagePreventsCommit() {
        for (page in 8..223) {
            val radio = Radio().apply { nakPage = page }
            fails { radio.communicator.sendCharacter(character()) }
            assertTrue("Page $page incorrectly committed", radio.events.isEmpty())
        }
    }

    @Test fun acknowledgedButMissingIdentityPageIsDetectedByReadback() {
        val radio = Radio().apply { droppedPage = 8 }
        fails { radio.communicator.sendCharacter(character()) }
        assertTrue(radio.events.isEmpty())
    }

    @Test fun malformedReadCannotSaveOrCommitPartialCharacter() {
        val radio = Radio().apply { shortRead = true }
        fails { radio.communicator.receiveCharacter { fail("Partial payload reached persistence"); true } }
        assertTrue(radio.events.isEmpty())
    }

    @Test fun wrongDimNeverWritesCharacterData() {
        val radio = Radio()
        radio.header[9] = 28
        fails { radio.communicator.sendCharacter(character()) }
        assertTrue(radio.events.isEmpty())
    }

    @Test fun finalSendAcknowledgementFailureIsNeverReportedAsSuccess() {
        val radio = Radio().apply { failCommit = true }
        fails { radio.communicator.sendCharacter(character()) }
    }

    @Test fun onlyWriteAcknowledgementsAreAccepted() {
        NfcTransferSafety.requireWriteAck(byteArrayOf(0x0a))
        for (code in listOf(0, 1, 4, 5, 15)) fails { NfcTransferSafety.requireWriteAck(byteArrayOf(code.toByte())) }
        fails { NfcTransferSafety.requireWriteAck(byteArrayOf()) }
        fails { NfcTransferSafety.requireWriteAck(byteArrayOf(0x0a, 0x0a)) }
    }

    @Test fun malformedAuthenticationCannotProceedToImportOrCommit() {
        for (response in listOf(byteArrayOf(), byteArrayOf(0), byteArrayOf(0, 0, 0))) {
            val radio = Radio().apply { authResponse = response }
            fails { radio.communicator.receiveCharacter { fail("Unauthenticated payload was imported"); true } }
            assertTrue(radio.events.isEmpty())
        }
    }

    @Test fun corruptedCharacterChecksumNeverReachesPersistence() {
        val radio = Radio()
        radio.memory[0] = (radio.memory[0].toInt() xor 1).toByte()
        fails { radio.communicator.receiveCharacter { fail("Corrupt payload was imported"); true } }
        assertTrue(radio.events.isEmpty())
    }
}
