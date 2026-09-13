package com.github.cfogrady.vbnfc.vb

import com.github.cfogrady.vbnfc.CryptographicTransformer
import com.github.cfogrady.vbnfc.data.NfcCharacter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the plaintext writer and duplicated blocks used by physical VB transfers. */
class EvolutionStatsTransferTest {
    // Encryption is not used by setCharacterInByteArray/finalizeByteArrayFormat.
    private val translator = VBNfcDataTranslator(CryptographicTransformer("", "", "", IntArray(16)))

    private fun trainedCharacter() = VBNfcCharacter(
        dimId = 13u,
        charIndex = 3u,
        stage = 3,
        ageInDays = 4,
        vitalPoints = 9999u,
        trophies = 30u,
        totalTrophies = 60u,
        currentPhaseBattlesWon = 25u,
        currentPhaseBattlesLost = 0u,
        totalBattlesWon = 25u,
        totalBattlesLost = 75u,
        transformationCountdownInMinutes = 1u,
        generation = 2u,
        transformationHistory = Array(9) { index ->
            if (index == 0) NfcCharacter.Transformation(3u, 2026u, 9u, 12u)
            else NfcCharacter.Transformation(255u, 65535u, 255u, 255u)
        },
    )

    private fun write(character: VBNfcCharacter, initialByte: Byte = 0): ByteArray =
        ByteArray(1024) { initialByte }.also {
            translator.setCharacterInByteArray(character, it)
            translator.finalizeByteArrayFormat(it)
        }

    @Test fun trainedStatsSurviveWritingReadingAndBlockDuplication() {
        val original = trainedCharacter()
        val bytes = write(original)
        val restored = translator.parseNfcCharacter(bytes)
        assertEquals(original.vitalPoints, restored.vitalPoints)
        assertEquals(original.trophies, restored.trophies)
        assertEquals(original.totalTrophies, restored.totalTrophies)
        assertEquals(original.currentPhaseBattlesWon, restored.currentPhaseBattlesWon)
        assertEquals(original.currentPhaseBattlesLost, restored.currentPhaseBattlesLost)
        assertEquals(original.totalBattlesWon, restored.totalBattlesWon)
        assertEquals(original.totalBattlesLost, restored.totalBattlesLost)
        assertEquals(original.transformationCountdownInMinutes, restored.transformationCountdownInMinutes)
        for (block in listOf(6, 8)) {
            val offset = block * 16
            assertArrayEquals(bytes.copyOfRange(offset, offset + 16), bytes.copyOfRange(offset + 16, offset + 32))
        }
    }

    @Test fun reservedIdentityBytesCannotOverwriteEvolutionStats() {
        val original = trainedCharacter()
        val before = write(original)
        original.appReserved1 = ByteArray(12) { (it + 100).toByte() }
        val after = write(original)
        // Identity occupies the first block and its duplicate only.
        assertArrayEquals(before.copyOfRange(32, before.size), after.copyOfRange(32, after.size))
        assertArrayEquals(original.appReserved1, translator.parseNfcCharacter(after).appReserved1)
    }

    @Test fun wireWinRateUsesCurrentPhaseInsteadOfLifetime() {
        val character = trainedCharacter() // Current phase: 100%; lifetime: 25%.
        val bytes = write(character)
        assertEquals(100, bytes[6 * 16 + 10].toInt())
        character.currentPhaseBattlesWon = 0u
        character.currentPhaseBattlesLost = 0u
        assertEquals(0, write(character)[6 * 16 + 10].toInt())
    }

    @Test fun watchBaseDataDoesNotReplaceProvidedStats() {
        val character = trainedCharacter()
        val zeroBase = write(character)
        val populatedBase = write(character, 0x55)
        for (block in listOf(6, 8)) {
            val offset = block * 16
            // Byte 12 in the status block is reserved and intentionally retained.
            val indices = (offset until offset + 15).filter { block != 8 || it != offset + 12 }
            for (index in indices) assertEquals("byte $index", zeroBase[index], populatedBase[index])
        }
    }
}
