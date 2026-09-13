package com.github.nacabaro.vbhelper.domain.identity

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcDataTranslator
import com.github.cfogrady.vbnfc.be.BENfcCharacter
import com.github.cfogrady.vbnfc.be.BENfcDataTranslator
import com.github.cfogrady.vbnfc.CryptographicTransformer
import org.junit.Assert.*
import org.junit.Test

class WatchTransferTest {
    private val individualId = "00112233445566778899aa"
    private val token = "112233445566778899aabb"

    private fun character() = VBNfcCharacter(
        dimId = 1u,
        charIndex = 3u,
        ageInDays = 4,
        generation = 2u,
        totalBattlesWon = 10u,
        totalBattlesLost = 5u,
        appReserved1 = IndividualIdentity.encode(token),
        transformationHistory = arrayOf(
            NfcCharacter.Transformation(0u, 2026u, 9u, 1u),
            NfcCharacter.Transformation(3u, 2026u, 9u, 2u),
        ),
    )

    private fun receipt() = WatchTransfer.capture(token, individualId, character())

    @Test fun backupPayloadWithLastSentTokenCannotResolveItsOwnPendingReceipt() {
        val first = character()
        val secondToken = "aabbccddeeff0011223344"
        val secondId = "ffeeddccbbaa9988776655"
        val second = VBNfcCharacter(dimId = 2u, charIndex = 3u,
            appReserved1 = IndividualIdentity.encode(secondToken),
            transformationHistory = first.transformationHistory.copyOf())
        val firstReceipt = receipt().copy(cardId = 11, deviceKey = "watch-A")
        val secondReceipt = WatchTransfer.capture(secondToken, secondId, second)
            .copy(cardId = 22, deviceKey = "watch-A")
        val pending = mutableMapOf(token to firstReceipt, secondToken to secondReceipt)

        // Fault injection: active/backup switch keeps the last app block, but restores
        // the first character's actual DIM and stats. This does not emulate firmware.
        first.appReserved1 = second.appReserved1.copyOf()
        val selected = pending[IndividualIdentity.decode(first.appReserved1)]!!
        assertEquals(22L, selected.cardId)
        assertNotEquals(first.dimId.toInt(), selected.dimId)
        assertNull(resolveReturningIndividual(first, pending::get, { true }, { false }))

        // Returning the second consumes its receipt. The first still has an outstanding
        // receipt, but the current token-only resolver no longer finds it.
        assertEquals(secondId, resolveReturningIndividual(second, pending::get, { true }, { false }))
        pending.remove(secondToken)
        assertTrue(firstReceipt.matches(first))
        assertEquals(firstReceipt, pending[token])
        assertNull(resolveReturningIndividual(first, pending::get, { true }, { false }))
    }

    @Test fun lineageAndStatsAreNotAUniqueIdentifierForTwoBackupOccupants() {
        val first = receipt()
        val second = first.copy(token = "aabbccddeeff0011223344", individualId = "ffeeddccbbaa9988776655")
        // Two genuine individuals can have identical lineage dates, age, generation,
        // species and counters. Matching those fields cannot establish ownership.
        assertNotEquals(first.individualId, second.individualId)
        assertTrue(first.matches(character()))
        assertTrue(second.matches(character()))
    }

    @Test fun returningIndividualKeepsIdentityAfterEvolutionAndTraining() {
        val incoming = character().apply {
            charIndex = 5u
            ageInDays = 6
            totalBattlesWon = 14u
            mood = 75
            vitalPoints = 5000u
            transformationHistory += NfcCharacter.Transformation(5u, 2026u, 9u, 3u)
        }
        assertEquals(individualId, resolveReturningIndividual(incoming, { receipt() }, { true }, { false }))
    }

    @Test fun sameSpeciesWithStaleLegacyIdDoesNotReviveRemovedIndividual() {
        val incoming = character().apply { appReserved1 = IndividualIdentity.encode(individualId) }
        assertNull(resolveReturningIndividual(incoming, { null }, { true }, { false }))
    }

    @Test fun sameSpeciesBornLaterDoesNotInheritIdentityEvenAfterEvolving() {
        val incoming = character().apply {
            ageInDays = 7
            transformationHistory = transformationHistory.map { it.copy(day = 8u) }.toTypedArray()
        }
        assertFalse(receipt().matches(incoming))
    }

    @Test fun rebornGenerationCannotReuseOutstandingExport() {
        val incoming = character().apply { generation = 3u }
        assertFalse(receipt().matches(incoming))
    }

    @Test fun regressedAgeOrLifetimeBattlesRejectsContinuity() {
        assertFalse(receipt().matches(character().apply { ageInDays = 2 }))
        assertFalse(receipt().matches(character().apply { totalBattlesWon = 0u }))
        assertFalse(receipt().matches(character().apply { totalBattlesLost = 0u }))
    }

    @Test fun ageAbove127IsUnsigned() {
        val older = character().apply { ageInDays = 130.toByte() }
        assertTrue(receipt().matches(older))
        val sentOlder = WatchTransfer.capture(token, individualId, older)
        assertFalse(sentOlder.matches(character()))
    }

    @Test fun differentCardCannotReuseIdentity() {
        assertFalse(receipt().copy(dimId = 2).matches(character()))
    }

    @Test fun missingOrShortenedHistoryIsInsufficientEvidence() {
        assertFalse(receipt().matches(character().apply { transformationHistory = emptyArray() }))
        assertFalse(receipt().matches(character().apply { transformationHistory = transformationHistory.take(1).toTypedArray() }))
        assertFalse(receipt().copy(history = "").matches(character()))
    }

    @Test fun legitimateYoungIndividualWithHistoryKeepsIdentity() {
        val young = character().apply { ageInDays = 1 }
        val sent = WatchTransfer.capture(token, individualId, young)
        assertTrue(sent.matches(young))
    }

    @Test fun consumedTokenCannotRestoreAgain() {
        val transfers = mutableMapOf(token to receipt())
        assertEquals(individualId, resolveReturningIndividual(character(), transfers::get, { true }, { false }))
        transfers.remove(token)
        assertNull(resolveReturningIndividual(character(), transfers::get, { true }, { false }))
    }

    @Test fun localIndividualCannotBeAttachedToSecondCharacter() {
        assertNull(resolveReturningIndividual(character(), { receipt() }, { true }, { true }))
    }

    @Test fun missingIndividualIsNotResurrectedByReceipt() {
        assertNull(resolveReturningIndividual(character(), { receipt() }, { false }, { false }))
    }

    @Test fun emptyOrForeignReservedBytesNeverResolveIdentity() {
        assertNull(resolveReturningIndividual(character().apply { appReserved1 = ByteArray(12) }, { receipt() }, { true }, { false }))
    }

    @Test fun staleHistoryCannotValidateAnUnrelatedCurrentSpecies() {
        assertFalse(receipt().matches(character().apply { charIndex = 0u }))
    }

    @Test fun differentDeviceFamilyCannotReuseReceipt() {
        assertFalse(receipt().copy(deviceType = 999).matches(character()))
    }

    @Test fun fingerprintDistinguishesEveryIdentityRelevantPayloadChange() {
        val baseline = TransferFingerprint.of(character(), "watch-A")
        val changes: List<VBNfcCharacter.() -> Unit> = listOf(
            { charIndex = 4u }, { generation = 3u }, { ageInDays = 5 }, { trophies = 10u },
            { totalBattlesWon = 11u }, { totalBattlesLost = 6u }, { vitalPoints = 3000u },
            { transformationCountdownInMinutes = 5u }, { appReserved1 = IndividualIdentity.encode(individualId) },
            { transformationHistory = transformationHistory.reversedArray() },
        )
        changes.forEach { change -> assertNotEquals(baseline, TransferFingerprint.of(character().apply(change), "watch-A")) }
        assertNotEquals(baseline, TransferFingerprint.of(character(), "watch-B"))
        assertEquals(baseline, TransferFingerprint.of(character(), "watch-A"))
    }

    @Test fun paddingDoesNotChangeLineageContinuity() {
        val incoming = character().apply {
            transformationHistory += Array(7) { NfcCharacter.Transformation(255u, 65535u, 255u, 255u) }
        }
        assertTrue(receipt().matches(incoming))
    }

    @Test fun lifetimeCountersAtUnsignedMaximumStayStable() {
        val sent = character().apply { totalBattlesWon = 65535u; totalBattlesLost = 65535u; ageInDays = 255.toByte() }
        assertTrue(WatchTransfer.capture(token, individualId, sent).matches(sent))
    }

    @Test fun tokenMustMatchReturnedReceiptEvenWhenAllStatsMatch() {
        assertNull(resolveReturningIndividual(character(), { receipt().copy(token = individualId) }, { true }, { false }))
    }

    @Test fun allIdentityBitsSurviveBothPhysicalDeviceFormats() {
        val crypto = CryptographicTransformer("", "", "", IntArray(16))
        val translators = listOf(VBNfcDataTranslator(crypto), BENfcDataTranslator(crypto))
        repeat(100) {
            val id = IndividualIdentity.generate()
            val forms = listOf<NfcCharacter>(
                character().apply { transformationHistory += Array(7) { NfcCharacter.Transformation(255u, 65535u, 255u, 255u) } },
                BENfcCharacter(dimId = 1u, charIndex = 3u, otp0 = ByteArray(8), otp1 = ByteArray(8)),
            )
            forms.zip(translators).forEach { (character, translator) ->
                character.appReserved1 = IndividualIdentity.encode(id)
                val bytes = ByteArray(1024)
                translator.setCharacterInByteArray(character, bytes)
                translator.finalizeByteArrayFormat(bytes)
                assertEquals(id, IndividualIdentity.decode(translator.parseNfcCharacter(bytes).appReserved1))
            }
        }
    }

    @Test fun beIndividualKeepsIdentityAfterValidEvolution() {
        val outgoing = BENfcCharacter(dimId = 1u, charIndex = 3u, otp0 = ByteArray(8), otp1 = ByteArray(8),
            appReserved1 = IndividualIdentity.encode(token), transformationHistory = character().transformationHistory)
        val sent = WatchTransfer.capture(token, individualId, outgoing)
        outgoing.charIndex = 5u
        outgoing.transformationHistory += NfcCharacter.Transformation(5u, 2026u, 9u, 13u)
        assertEquals(individualId, resolveReturningIndividual(outgoing, { sent }, { true }, { false }))
        assertFalse(receipt().matches(outgoing))
    }
}
