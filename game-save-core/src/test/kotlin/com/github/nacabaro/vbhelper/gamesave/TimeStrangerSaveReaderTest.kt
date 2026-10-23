package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class TimeStrangerSaveReaderTest {
    private fun fixture(block: (ByteArray) -> Unit = {}): ByteArray {
        val plain = ByteArray(TimeStrangerSaveReader.FILE_BYTES)
        "5, 3097152, 176, 0, Synthetic, 100.0".toByteArray().copyInto(plain)
        entry(plain, 0x1000 + 2 * 0x150, "A", 10, 0x12345678)
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

    @Test fun readsIdentityFieldsWithoutMutatingSaveOrInventingCanonicalSpecies() {
        val bytes = fixture()
        val before = bytes.copyOf()
        val result = SaveReaderRegistry().read(ByteArrayInputStream(bytes))
        assertArrayEquals(before, bytes)
        assertEquals(1, result.activeCount)
        val individual = result.records.single()
        assertEquals("A", individual.storedName) // Single-character nicknames are valid.
        assertEquals(10L, individual.nativeSpeciesId)
        assertEquals(42, individual.level)
        assertEquals(16, individual.personalityId)
        assertEquals(0x150, java.util.Base64.getDecoder().decode(individual.nativeRecordBase64).size)
        assertEquals(SaveContainer.PARTY, individual.container)
        assertTrue(result.capabilities.canImportSnapshot)
        assertTrue(result.capabilities.provisionalSnapshotCopy)
        assertFalse(result.capabilities.canRemove)
        assertFalse(result.capabilities.canInsert)
        assertEquals(64, result.contentRevision.length)
    }

    @Test fun captureRetainsExactContainerAndClearsOnlyItsOwnWorkingCopy() {
        val bytes = fixture()
        val original = bytes.copyOf()
        val captured = SaveReaderRegistry().capture(bytes.inputStream())
        assertArrayEquals(original, captured.container)
        captured.close()
        assertTrue(requireNotNull(captured.container).all { it == 0.toByte() })
        assertArrayEquals(original, bytes)
    }

    @Test fun controlledRenameChangesOnlyStoredNameAndNeverSpeciesIdentityOrProgress() {
        // Independent synthetic case based on controlled name-buffer evidence; no private save bytes/IDs.
        fun renamed(name: String) = TimeStrangerSaveReader().read(fixture {
            val offset = 0x1000 + 2 * 0x150 + 4
            it.fill(0, offset, offset + 32)
            name.toByteArray(Charsets.UTF_8).copyInto(it, offset)
        })
        val before = renamed("OldLabel")
        val after = renamed("NewLabel9")
        val a = before.records.single()
        val b = after.records.single()
        assertEquals(a.identityCandidate, b.identityCandidate)
        assertEquals(a.nativeSpeciesId, b.nativeSpeciesId)
        assertEquals(a.personalityId, b.personalityId)
        assertEquals(a.level, b.level)
        assertEquals(a.totalExp, b.totalExp)
        assertEquals(a.recordKey, b.recordKey)
        val changes = IdentityEvidenceComparison.compare(before, after)
        assertEquals(setOf(ObservedField.NAME), changes.candidates.single().changedFields)
        val original = java.util.Base64.getDecoder().decode(a.nativeRecordBase64)
        val updated = java.util.Base64.getDecoder().decode(b.nativeRecordBase64)
        assertFalse(original.contentEquals(updated))
        original.fill(0, 4, 36)
        updated.fill(0, 4, 36)
        assertArrayEquals(original, updated)
        assertFalse(changes.establishesIdentity)
        assertTrue(after.capabilities.provisionalSnapshotCopy)
        assertFalse(after.capabilities.canRemove)
        assertFalse(after.capabilities.canInsert)
    }

    @Test fun inactiveBoundaryAndTailNeverBecomeActiveBankMembers() {
        val result = TimeStrangerSaveReader().read(fixture {
            entry(it, 0x1000 + 8 * 0x150, "Bank", 11, 100)
            entry(it, 0x1000 + 9 * 0x150, "Boundary", 12, 0, active = 0)
            entry(it, 0x1000 + 10 * 0x150, "Stale", 13, 101, active = 0)
        })
        assertEquals(2, result.activeCount)
        assertEquals(2, result.reviewCount)
        assertTrue(result.records.single { it.storedName == "Boundary" }.issues.contains(RecordIssue.INACTIVE_BOUNDARY))
        assertTrue(result.records.single { it.storedName == "Stale" }.issues.contains(RecordIssue.INACTIVE_TAIL))
    }

    @Test fun duplicatesAreRetainedForReviewRatherThanMerged() {
        val result = TimeStrangerSaveReader().read(fixture {
            entry(it, 0x1000 + 3 * 0x150, "A", 10, 0x12345678)
        })
        assertEquals(2, result.records.size)
        assertEquals(0, result.activeCount)
        assertTrue(result.records.all { RecordIssue.DUPLICATE_IDENTITY in it.issues })
    }

    @Test fun farmHashUsesItsOwnOffsetAndFarmOccupancyRemainsUnverified() {
        val result = TimeStrangerSaveReader().read(fixture {
            entry(it, 0x53000 + 3 * 0x158, "テスト", 20, 12345, farm = true)
            put(it, 0x53000 + 3 * 0x158 + 4 + 0x148, 2) // Farm position, not the identity hash.
        })
        val farm = result.records.single { it.container == SaveContainer.FARM }
        assertEquals(12345L, farm.identityCandidate)
        assertEquals(0x158, java.util.Base64.getDecoder().decode(farm.nativeRecordBase64).size)
        assertEquals(RecordMembership.NEEDS_REVIEW, farm.membership)
        assertTrue(RecordIssue.FARM_OWNERSHIP_UNVERIFIED in farm.issues)
    }

    @Test fun codecReencodesDecodedBytesByteIdenticallyForRungOneRehearsal() {
        val original = fixture()
        val decoded = TimeStrangerContainer.decode(original)
        val reencoded = TimeStrangerContainer.encode(decoded)
        assertFalse(decoded.contentEquals(original))
        assertArrayEquals(original, reencoded)
        decoded.fill(0)
        reencoded.fill(0)
    }

    @Test fun registryRejectsOversizedTruncatedAndUnrecognizedContainers() {
        assertEquals(SaveReadFailure.INPUT_TOO_LARGE, assertThrows(SaveReadException::class.java) {
            SaveReaderRegistry().read(ByteArrayInputStream(ByteArray(TimeStrangerSaveReader.FILE_BYTES + 1)))
        }.reason)
        assertEquals(SaveReadFailure.UNSUPPORTED_FORMAT, assertThrows(SaveReadException::class.java) {
            SaveReaderRegistry().read(ByteArrayInputStream(ByteArray(720)))
        }.reason)
        assertEquals(SaveReadFailure.INVALID_CONTAINER, assertThrows(SaveReadException::class.java) {
            TimeStrangerSaveReader().read(ByteArray(TimeStrangerSaveReader.FILE_BYTES))
        }.reason)
    }

    @Test fun completeRecordsEndingAtARegionBoundaryAreRetained() {
        val result = TimeStrangerSaveReader().read(fixture {
            it.fill(0, 0x1000, 0x5C000)
            // These phases put the final complete record exactly against each region's end.
            entry(it, 0x1000 + 208 + 2 * 0x150, "Party", 10, 100)
            entry(it, 0x53000 - 0x150, "Last bank", 11, 101)
            entry(it, 0x53000 + 56, "First farm", 12, 102, farm = true)
            entry(it, 0x5C000 - 0x158, "Last farm", 13, 103, farm = true)
        })
        assertEquals(setOf("Party", "Last bank", "First farm", "Last farm"), result.records.map { it.storedName }.toSet())
        assertEquals(RecordMembership.NEEDS_REVIEW, result.records.single { it.storedName == "Last bank" }.membership)
    }

    @Test fun discoveryExcludesMetadataBackupsAndWrongSizedFiles() {
        assertTrue(SaveDiscoveryPolicy.isCandidate("0001.bin", TimeStrangerSaveReader.FILE_BYTES.toLong()))
        assertFalse(SaveDiscoveryPolicy.isCandidate("slot_0001.bin", 720))
        assertFalse(SaveDiscoveryPolicy.isCandidate("sysdata_dx11.bin", 992))
        assertFalse(SaveDiscoveryPolicy.isCandidate("0001.bin.bak", null))
        assertFalse(SaveDiscoveryPolicy.isCandidate("0001.bin", 720))
    }
}
