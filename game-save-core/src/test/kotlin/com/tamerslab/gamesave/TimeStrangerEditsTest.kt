package com.tamerslab.gamesave

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Synthetic fixtures shaped by the controlled rename evidence; no private save bytes. */
class TimeStrangerEditsTest {
    private fun fixture(block: (ByteArray) -> Unit = {}): ByteArray {
        val plain = ByteArray(TimeStrangerSaveReader.FILE_BYTES)
        "5, 3097152, 176, 1, Tester, 100.0".toByteArray().copyInto(plain)
        entry(plain, 0x1000 + 2 * 0x150, "OldName", 10, 0x12345678)
        block(plain)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("3962776754555883".toByteArray(), "AES"))
        return cipher.doFinal(plain)
    }

    private fun put(data: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }

    private fun entry(data: ByteArray, offset: Int, name: String, species: Int, hash: Int, active: Int = 1, farm: Boolean = false) {
        val nameOffset = offset + 4
        put(data, offset, species)
        name.toByteArray(Charsets.UTF_8).copyInto(data, nameOffset)
        put(data, nameOffset + 0x60, 42)
        data[nameOffset + 0xEE] = 16
        put(data, nameOffset + 0x104, species)
        put(data, nameOffset + 0x140, active)
        put(data, nameOffset + if (farm) 0x150 else 0x148, hash)
    }

    private fun singleRecord(fixture: ByteArray): ObservedDigimon = TimeStrangerSaveReader().read(fixture).records.single()

    @Test fun renameChangesOnlyTheNameWindowAndReReadsWithTheNewName() {
        val source = fixture()
        val before = singleRecord(source)
        assertEquals("OldName", before.storedName)
        val candidate = TimeStrangerEdits.renameIndividual(source, before.recordKey, "NewName9".toByteArray())
        assertFalse(source.contentEquals(candidate))

        val sourcePlain = TimeStrangerContainer.decode(source)
        val candidatePlain = TimeStrangerContainer.decode(candidate)
        val nameOffset = before.recordKey.substringAfter(':').toInt() + 4
        for (index in sourcePlain.indices) {
            val inWindow = index in nameOffset until (nameOffset + TimeStrangerEdits.NAME_WINDOW_BYTES)
            if (inWindow) continue
            assertEquals("byte 0x${index.toString(16)} changed", sourcePlain[index], candidatePlain[index])
        }
        sourcePlain.fill(0)
        candidatePlain.fill(0)

        val after = singleRecord(candidate)
        assertEquals("NewName9", after.storedName)
        assertEquals(before.recordKey, after.recordKey)
        assertEquals(before.identityCandidate, after.identityCandidate)
        assertEquals(before.nativeSpeciesId, after.nativeSpeciesId)
        assertEquals(before.level, after.level)
        assertEquals(before.personalityId, after.personalityId)
    }

    @Test fun renameKeepsStaleTailResidueExactlyLikeTheGame() {
        val source = fixture { "OldName\u0000zz\u0000".toByteArray().copyInto(it, 0x1000 + 2 * 0x150 + 4) }
        val before = singleRecord(source)
        assertEquals("OldName", before.storedName)
        val candidate = TimeStrangerEdits.renameIndividual(source, before.recordKey, "New".toByteArray())
        val plain = TimeStrangerContainer.decode(candidate)
        val window = plain.copyOfRange(0x1000 + 2 * 0x150 + 4, 0x1000 + 2 * 0x150 + 4 + 11)
        plain.fill(0)
        assertEquals("New\u0000ame\u0000zz\u0000", window.toString(Charsets.ISO_8859_1))
        assertEquals("New", singleRecord(candidate).storedName)
    }

    @Test fun renameAcceptsTheLongestWindowFittingName() {
        val source = fixture()
        val before = singleRecord(source)
        val longest = "n".repeat(TimeStrangerEdits.NAME_WINDOW_BYTES - 1)
        val candidate = TimeStrangerEdits.renameIndividual(source, before.recordKey, longest.toByteArray())
        assertEquals(longest, singleRecord(candidate).storedName)
    }

    @Test fun renameRejectsInvalidNamesSizesAndTargets() {
        val source = fixture()
        val before = singleRecord(source)
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, before.recordKey, ByteArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, before.recordKey, ByteArray(TimeStrangerEdits.NAME_WINDOW_BYTES) { 0x61 })
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, before.recordKey, byteArrayOf(0x01, 0x62))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, before.recordKey, byteArrayOf(0xC3.toByte()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, before.recordKey, byteArrayOf('N'.code.toByte(), 0, 'm'.code.toByte()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(source, "main:999999", "Ok".toByteArray())
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(ByteArray(720), before.recordKey, "Ok".toByteArray())
        }
    }

    @Test fun renameRejectsFarmAndReviewRecords() {
        val farmSource = fixture { entry(it, 0x53000 + 3 * 0x158, "Farmy", 20, 12345, farm = true) }
        val farm = TimeStrangerSaveReader().read(farmSource).records.single { it.container == SaveContainer.FARM }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(farmSource, farm.recordKey, "Ok".toByteArray())
        }
        val boundarySource = fixture { entry(it, 0x1000 + 9 * 0x150, "Edge", 11, 100, active = 0) }
        val boundary = TimeStrangerSaveReader().read(boundarySource).records.single { it.membership == RecordMembership.NEEDS_REVIEW }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.renameIndividual(boundarySource, boundary.recordKey, "Ok".toByteArray())
        }
    }

    @Test fun activateFlipsOnlyTheStoredFlagAndReReadsAsActive() {
        val source = fixture {
            entry(it, 0x1000 + 8 * 0x150, "First", 9, 90)
            entry(it, 0x1000 + 9 * 0x150, "Boundary", 11, 110, active = 0)
            entry(it, 0x1000 + 10 * 0x150, "Waiting", 12, 120, active = 0)
        }
        val observation = TimeStrangerSaveReader().read(source)
        val boundary = observation.records.single { it.storedName == "Boundary" }
        assertEquals(listOf(RecordIssue.INACTIVE_BOUNDARY), boundary.issues)
        assertEquals(listOf(RecordIssue.INACTIVE_TAIL), observation.records.single { it.storedName == "Waiting" }.issues)
        val candidate = TimeStrangerEdits.activateInactiveIndividual(source, boundary.recordKey)
        assertFalse(source.contentEquals(candidate))

        val sourcePlain = TimeStrangerContainer.decode(source)
        val candidatePlain = TimeStrangerContainer.decode(candidate)
        val flagOffset = boundary.recordKey.substringAfter(':').toInt() + 4 + 0x140
        var changed = 0
        for (index in sourcePlain.indices) {
            if (sourcePlain[index] == candidatePlain[index]) continue
            assertTrue("byte 0x${index.toString(16)} changed", index in flagOffset until flagOffset + 4)
            changed++
        }
        assertEquals(1, changed)
        assertEquals(1.toByte(), candidatePlain[flagOffset])
        sourcePlain.fill(0)
        candidatePlain.fill(0)

        val reread = TimeStrangerSaveReader().read(candidate)
        val after = reread.records.single { it.recordKey == boundary.recordKey }
        assertEquals(RecordMembership.ACTIVE_RECORD, after.membership)
        assertTrue(after.issues.isEmpty())
        assertEquals(SaveContainer.BANK, after.container)
        assertEquals(boundary.storedName, after.storedName)
        assertEquals(boundary.nativeSpeciesId, after.nativeSpeciesId)
        assertEquals(boundary.identityCandidate, after.identityCandidate)
        assertEquals(boundary.level, after.level)
        assertEquals(listOf(RecordIssue.INACTIVE_BOUNDARY), reread.records.single { it.storedName == "Waiting" }.issues)
    }

    @Test fun activateRejectsActiveFarmIdentitylessAndUnknownRecords() {
        val source = fixture {
            entry(it, 0x1000 + 8 * 0x150, "First", 9, 90)
            entry(it, 0x1000 + 9 * 0x150, "Tail", 11, 0, active = 0)
            entry(it, 0x1000 + 10 * 0x150, "Waiting", 12, 120, active = 0)
            entry(it, 0x53000 + 3 * 0x158, "Farmy", 20, 12345, farm = true)
        }
        val observation = TimeStrangerSaveReader().read(source)
        val party = observation.records.single { it.container == SaveContainer.PARTY }
        val activeBank = observation.records.single { it.storedName == "First" }
        val identityless = observation.records.single { it.storedName == "Tail" }
        assertEquals(listOf(RecordIssue.INACTIVE_BOUNDARY, RecordIssue.MISSING_IDENTITY), identityless.issues)
        val tail = observation.records.single { it.storedName == "Waiting" }
        assertEquals(listOf(RecordIssue.INACTIVE_TAIL), tail.issues)
        val farm = observation.records.single { it.container == SaveContainer.FARM }
        listOf(party.recordKey, activeBank.recordKey, identityless.recordKey, tail.recordKey, farm.recordKey, "main:999999").forEach { key ->
            assertThrows(IllegalArgumentException::class.java) {
                TimeStrangerEdits.activateInactiveIndividual(source, key)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            TimeStrangerEdits.activateInactiveIndividual(ByteArray(720), party.recordKey)
        }
    }
}
