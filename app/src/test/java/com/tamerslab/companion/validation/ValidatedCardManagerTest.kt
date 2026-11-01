package com.tamerslab.companion.validation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedCardManagerTest {
    private class FakeDao(initial: List<Int> = emptyList()) : ValidatedCardDao {
        val ids = initial.toMutableList()
        override suspend fun getIds(): List<Int> = ids.toList()
        override suspend fun insert(validatedCardEntity: ValidatedCardEntity) {
            if (validatedCardEntity.cardId !in ids) ids += validatedCardEntity.cardId
        }
    }

    @Test fun validationIsIdempotentAndCached() = runTest {
        val dao = FakeDao(listOf(7))
        val manager = ValidatedCardManager(dao)
        assertTrue(manager.isValidatedCard(7))
        assertFalse(manager.isValidatedCard(8))
        manager.addValidatedCard(8)
        manager.addValidatedCard(8)
        assertTrue(manager.isValidatedCard(8))
        assertEquals(listOf(7, 8), dao.ids)
    }
}
