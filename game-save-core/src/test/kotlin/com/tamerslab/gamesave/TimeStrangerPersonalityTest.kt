package com.tamerslab.gamesave

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Synthetic fixtures for the controlled personality edit. Same shape as TimeStrangerEditsTest:
 * no private save bytes, only the layout the reader/writer agree on.
 */
class TimeStrangerPersonalityTest {
    private val recordOffset = 0x1000 + 2 * 0x150
    private val recordKey = "main:$recordOffset"

    private fun fixture(active: Int = 1, block: (ByteArray) -> Unit = {}): ByteArray {
        val plain = ByteArray(TimeStrangerSaveReader.FILE_BYTES)
        "5, 3097152, 176, 1, Tester, 100.0".toByteArray().copyInto(plain)
        entry(plain, recordOffset, "OldName", 10, 0x12345678, active)
        block(plain)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec("3962776754555883".toByteArray(), "AES"))
        return cipher.doFinal(plain)
    }

    private fun put(data: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
    }

    private fun entry(
        data: ByteArray,
        offset: Int,
        name: String,
        species: Int,
        hash: Int,
        active: Int = 1
    ) {
        val nameOffset = offset + 4
        put(data, offset, species)
        name.toByteArray(Charsets.UTF_8).copyInto(data, nameOffset)
        put(data, nameOffset + 0x60, 42)
        data[nameOffset + TimeStrangerEdits.PERSONALITY_OFFSET] = 16
        put(data, nameOffset + 0x104, species)
        put(data, nameOffset + 0x140, active)
        put(data, nameOffset + 0x148, hash)
    }

    private fun record(fixture: ByteArray): ObservedDigimon =
        TimeStrangerSaveReader().read(fixture).records.single { it.recordKey == recordKey }

    @Test
    fun writesOnlyThePersonalityByteAndReReadsTheNewValue() {
        val source = fixture()
        val before = record(source)
        assertEquals(16, before.personalityId)

        val candidate = TimeStrangerEdits.setPersonality(source, recordKey, 6)
        assertFalse(source.contentEquals(candidate))

        val sourcePlain = TimeStrangerContainer.decode(source)
        val candidatePlain = TimeStrangerContainer.decode(candidate)
        val personalityOffset = recordOffset + 4 + TimeStrangerEdits.PERSONALITY_OFFSET
        var changed = 0
        for (index in sourcePlain.indices) {
            if (sourcePlain[index] != candidatePlain[index]) {
                changed++
                assertEquals("only the personality byte may change", personalityOffset, index)
            }
        }
        sourcePlain.fill(0)
        candidatePlain.fill(0)
        assertEquals(1, changed)

        val after = record(candidate)
        assertEquals(6, after.personalityId)
        assertEquals(before.recordKey, after.recordKey)
        assertEquals(before.storedName, after.storedName)
        assertEquals(before.nativeSpeciesId, after.nativeSpeciesId)
        assertEquals(before.level, after.level)
        assertEquals(before.identityCandidate, after.identityCandidate)
        assertEquals(before.container, after.container)
    }

    @Test
    fun repeatedDifferingWritesAreDeterministic() {
        val source = fixture()
        val first = TimeStrangerEdits.setPersonality(source, recordKey, 3)
        val second = TimeStrangerEdits.setPersonality(source, recordKey, 3)
        assertTrue(first.contentEquals(second))
        assertEquals(3, record(first).personalityId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroPersonality() {
        TimeStrangerEdits.setPersonality(fixture(), recordKey, 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPersonalityAboveSixteen() {
        TimeStrangerEdits.setPersonality(fixture(), recordKey, 17)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWritingTheValueThatIsAlreadyStored() {
        TimeStrangerEdits.setPersonality(fixture(), recordKey, 16)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownRecordKey() {
        TimeStrangerEdits.setPersonality(fixture(), "main:999999", 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesInactiveRecords() {
        TimeStrangerEdits.setPersonality(fixture(active = 0), recordKey, 2)
    }

    @Test
    fun modeSwitchDoesNotTouchOtherBytesOfOtherRecords() {
        val source = fixture()
        val candidate = TimeStrangerEdits.setPersonality(source, recordKey, 11)
        // Does not weaken the existing rename guard: rename still works on the edited document.
        val renamed = TimeStrangerEdits.renameIndividual(candidate, recordKey, "Renamed".toByteArray())
        val reread = record(renamed)
        assertEquals(11, reread.personalityId)
        assertEquals("Renamed", reread.storedName)
    }

    @Test
    fun personalityIdsRoundTripAcrossTheWholeLegalRange() {
        val source = fixture()
        val current = record(source).personalityId
        for (id in 1..16) {
            if (id == current) continue
            val candidate = TimeStrangerEdits.setPersonality(source, recordKey, id)
            assertEquals(id, record(candidate).personalityId)
        }
    }
}
