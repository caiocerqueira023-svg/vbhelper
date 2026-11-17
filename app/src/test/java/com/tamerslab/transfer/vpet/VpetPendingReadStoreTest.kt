package com.tamerslab.transfer.vpet

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tamerslab.domain.vpet.VpetPendingReason
import com.tamerslab.domain.vpet.VpetReadSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM coverage for the DataStore-backed pending list. A memory [DataStore] replaces the
 * Android preferences file, so this test runs without Robolectric or a real Context.
 */
class VpetPendingReadStoreTest {

    private class MemoryPreferencesStore(
        initial: Preferences = emptyPreferences(),
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun snapshot(readAtMillis: Long = 1_700_000_000_000L) = VpetReadSnapshot(
        modelKey = "dm20",
        modelLabel = "Digital Monster Ver.20th",
        version = 3,
        index = 11,
        name = "Mysterymon",
        stage = 2,
        attribute = 1,
        power = 33,
        payloadHex = "0A0B",
        valid = true,
        readAtMillis = readAtMillis,
    )

    private fun store(initial: Preferences = emptyPreferences()): VpetPendingReadStore =
        VpetPendingReadStore(MemoryPreferencesStore(initial))

    @Test
    fun addedReadIsEmittedAndCanBeLookedUp() = runTest {
        val store = store()
        val read = store.add(snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon")
        assertTrue(read.id.isNotBlank())
        assertEquals(listOf(read), store.pendingReads.first())
        assertEquals(read, store.find(read.id))
        assertNull(store.find("missing"))
    }

    @Test
    fun everyAddKeepsItsOwnPendingEntryAndId() = runTest {
        // D4/D7: pending entries are never deduplicated; each read keeps its own provenance.
        val store = store()
        val first = store.add(snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon")
        val second = store.add(snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon")
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(first, second), store.pendingReads.first())
    }

    @Test
    fun removeDropsOnlyTheRequestedRead() = runTest {
        val store = store()
        val first = store.add(snapshot(1L), VpetPendingReason.UNKNOWN_SPECIES, "One")
        val second = store.add(snapshot(2L), VpetPendingReason.PARTICIPATION_BLOCKED, "Two")
        store.remove(first.id)
        assertEquals(listOf(second), store.pendingReads.first())
        store.remove(second.id)
        assertTrue(store.pendingReads.first().isEmpty())
    }

    @Test
    fun removingAnUnknownIdIsANoOp() = runTest {
        val store = store()
        val read = store.add(snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon")
        store.remove("missing")
        assertEquals(listOf(read), store.pendingReads.first())
    }

    @Test
    fun missingPreferenceReadsAsAnEmptyList() = runTest {
        assertTrue(store().pendingReads.first().isEmpty())
    }

    @Test
    fun corruptJsonDecodesAsEmptyAndIsReplacedByTheNextWrite() = runTest {
        val corrupt = preferencesOf(
            stringPreferencesKey(VPET_PENDING_READS_PREFERENCE) to """{"not":"an array"}""",
        )
        val store = store(corrupt)
        assertTrue(store.pendingReads.first().isEmpty())
        val read = store.add(snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Mysterymon")
        assertEquals(listOf(read), store.pendingReads.first())
    }
}
