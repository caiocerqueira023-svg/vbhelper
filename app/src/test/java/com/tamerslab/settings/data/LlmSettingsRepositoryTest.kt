package com.tamerslab.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tamerslab.chat.ChatApiProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmSettingsRepositoryTest {
    @Test fun `raw prompt saves are denied after debug access is revoked`() = runBlocking {
        var enabled = true
        val repository = LlmSettingsRepository(FakeDataStore(), debugEnabled = { enabled })
        repository.setSystemPromptTemplate("Saved companion preferences")
        repository.setWildSystemPromptTemplate("Saved encounter preferences")
        enabled = false
        repository.setSystemPromptTemplate("Late callback must not save")
        repository.setWildSystemPromptTemplate(null)
        assertEquals("Saved companion preferences", repository.systemPromptTemplate.first())
        assertEquals("Saved encounter preferences", repository.wildSystemPromptTemplate.first())
    }

    @Test fun `normal connection setup remains available while raw prompt edits are denied`() = runBlocking {
        val repository = LlmSettingsRepository(FakeDataStore())
        repository.setSystemPromptTemplate("Not authorized")
        repository.setWildSystemPromptTemplate("Not authorized")
        repository.saveProviderSettings(ChatApiProvider.CUSTOM, "connection-key", "local-model", "https://example.invalid/v1/")
        assertEquals(null, repository.systemPromptTemplate.first())
        assertEquals(null, repository.wildSystemPromptTemplate.first())
        assertEquals("connection-key", repository.apiKey.first())
    }
    @Test
    fun `openrouter defaults to the recommended free roleplay model`() = runBlocking {
        val repository = LlmSettingsRepository(FakeDataStore())

        assertEquals("google/gemma-4-31b-it:free", repository.model.first())
        assertEquals(0.95, repository.temperature.first(), 0.0001)
    }

    @Test
    fun `legacy openrouter auto model is replaced by stable free roleplay default`() = runBlocking {
        val preferences = mutablePreferencesOf().apply {
            this[stringPreferencesKey("chat_api_openrouter_model")] = "openrouter/auto"
        }
        val store = FakeDataStore(preferences)
        val repository = LlmSettingsRepository(store)

        assertEquals("google/gemma-4-31b-it:free", repository.model.first())
    }

    @Test
    fun `model and temperature are stored independently per provider`() = runBlocking {
        val repository = LlmSettingsRepository(FakeDataStore())

        repository.saveProviderSettings(
            provider = ChatApiProvider.OPENROUTER,
            apiKey = "openrouter-key",
            model = "google/gemma-4-26b-a4b-it:free",
            baseUrl = ChatApiProvider.OPENROUTER.baseUrl.orEmpty(),
            temperature = 0.85
        )
        repository.saveProviderSettings(
            provider = ChatApiProvider.UNO_ROUTER,
            apiKey = "uno-key",
            model = "qwen/qwen3.8-27b:free",
            baseUrl = ChatApiProvider.UNO_ROUTER.baseUrl.orEmpty(),
            temperature = 1.1
        )

        val settings = repository.providerSettings.first()
        assertEquals(0.85, settings.getValue(ChatApiProvider.OPENROUTER).temperature, 0.0001)
        assertEquals(1.1, settings.getValue(ChatApiProvider.UNO_ROUTER).temperature, 0.0001)
        assertEquals("qwen/qwen3.8-27b:free", settings.getValue(ChatApiProvider.UNO_ROUTER).model)
    }

    @Test
    fun `invalid temperatures are normalized to safe bounds`() = runBlocking {
        val store = FakeDataStore()
        val repository = LlmSettingsRepository(store)

        repository.saveProviderSettings(
            provider = ChatApiProvider.OPENROUTER,
            apiKey = "key",
            model = "google/gemma-4-26b-a4b-it:free",
            baseUrl = ChatApiProvider.OPENROUTER.baseUrl.orEmpty(),
            temperature = Double.NaN
        )
        assertEquals(0.95, repository.temperature.first(), 0.0001)

        store.updatePreferences {
            mutablePreferencesOf().apply {
                this[doublePreferencesKey("chat_api_openrouter_temperature")] = 9.0
            }
        }
        assertEquals(2.0, repository.temperature.first(), 0.0001)
    }

    @Test
    fun `custom provider does not silently receive an OpenRouter model`() = runBlocking {
        val repository = LlmSettingsRepository(FakeDataStore())
        val settings = repository.providerSettings.first()

        assertEquals("", settings.getValue(ChatApiProvider.CUSTOM).model)
        assertTrue(settings.getValue(ChatApiProvider.CUSTOM).baseUrl.isEmpty())
    }

    @Test
    fun `legacy auto model is removed from custom providers`() = runBlocking {
        val preferences = mutablePreferencesOf().apply {
            this[stringPreferencesKey("chat_api_custom_model")] = "openrouter/auto"
        }
        val repository = LlmSettingsRepository(FakeDataStore(preferences))

        assertEquals("", repository.providerSettings.first().getValue(ChatApiProvider.CUSTOM).model)
    }

    private class FakeDataStore(
        initial: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data = state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }

        suspend fun updatePreferences(transform: (Preferences) -> Preferences) {
            updateData { transform(it) }
        }
    }
}
