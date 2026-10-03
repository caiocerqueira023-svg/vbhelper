package com.github.nacabaro.vbhelper.source

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.github.nacabaro.vbhelper.domain.card.OfficialStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SpeciesOriginQueueTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test fun refreshingAQueuedNameDoesNotCreateAChoiceForAnExistingCard() = runTest {
        val repository = SpeciesSettingsRepository(MemoryStore())
        repository.refreshPendingCardName(7, "Existing")
        assertTrue(repository.pendingCardOriginPrompts.first().isEmpty())
        repository.setPendingCardOriginPrompt(12, "Original")
        repository.refreshPendingCardName(12, "Renamed file")
        val prompt = repository.pendingCardOriginPrompts.first().single()
        assertEquals(12L, prompt.cardId)
        assertEquals("Renamed file", prompt.cardName)
        assertFalse(prompt.isImporting)
    }

    @Test fun choosingAnOriginByIdCannotAccidentallyConsumeTheNextNewCard() = runTest {
        val repository = SpeciesSettingsRepository(MemoryStore())
        repository.setPendingCardOriginPrompt(7, "One")
        repository.setPendingCardOriginPrompt(12, "Two")
        assertEquals(false, repository.selectPendingCardOrigin(7, OfficialStatus.CUSTOM))
        assertNull(repository.selectPendingCardOrigin(7, OfficialStatus.OFFICIAL))
        assertEquals(listOf(12L), repository.pendingCardOriginPrompts.first().map { it.cardId })
    }

    @Test fun reservedChoiceStaysRecoverableUntilItsStatusIsSaved() = runTest {
        val repository = SpeciesSettingsRepository(MemoryStore())
        repository.setPendingCardOriginPrompt(7, "One")
        repository.setPendingCardOriginPrompt(12, "Two")
        assertTrue(repository.reservePendingCardOrigin(7, OfficialStatus.CUSTOM))
        assertFalse(repository.reservePendingCardOrigin(7, OfficialStatus.OFFICIAL))
        assertEquals(OfficialStatus.CUSTOM, repository.pendingCardOriginPrompts.first().first().selectedStatus)
        repository.recoverInterruptedCardOriginImports(setOf(7, 12))
        assertNull(repository.pendingCardOriginPrompts.first().first().selectedStatus)
        repository.clearPendingCardOriginPrompt(7)
        assertEquals(listOf(12L), repository.pendingCardOriginPrompts.first().map { it.cardId })
    }

    @Test fun recoveryDropsDeletedOrAlreadyClassifiedCardPrompts() = runTest {
        val repository = SpeciesSettingsRepository(MemoryStore())
        repository.setPendingCardOriginPrompt(7, "Classified")
        repository.setPendingCardOriginPrompt(12, "Unknown")
        repository.setPendingCardOriginPrompt(99, "Deleted")
        repository.recoverInterruptedCardOriginImports(setOf(12))
        assertEquals(listOf(12L), repository.pendingCardOriginPrompts.first().map { it.cardId })
    }
}
