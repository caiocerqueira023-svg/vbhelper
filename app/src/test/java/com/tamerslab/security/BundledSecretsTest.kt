package com.tamerslab.security

import androidx.datastore.core.DataStore
import com.tamerslab.hardware.nfc.CryptographicTransformer
import com.tamerslab.hardware.nfc.data.DeviceType
import com.tamerslab.security.proto.Secrets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert
import org.junit.Test

/**
 * Guards the keys committed in [BundledSecrets].
 *
 * These tests are the reason the baked-in values are safe to ship: if any constant is truncated,
 * mistyped or replaced, the regenerated NFC password no longer matches the password the known
 * test tag produces, and the suite fails instead of the app silently refusing to talk to watches.
 */
class BundledSecretsTest {

    @Test
    fun bundledSecretsAreComplete() {
        Assert.assertFalse("Baked-in secrets must satisfy isMissingSecrets()", BundledSecrets.value.isMissingSecrets())
    }

    @Test
    fun bundledSecretsAuthenticateEveryWatchFamily() {
        val transformers = BundledSecrets.value.getCryptographicTransformerMap()
        Assert.assertEquals(
            "BE (Vital Bracelet BE) password mismatch",
            "be29a87e",
            password(transformers, DeviceType.VitalBraceletBEDeviceType),
        )
        Assert.assertEquals(
            "VBC (Vital Characters) password mismatch",
            "a71dfb22",
            password(transformers, DeviceType.VitalCharactersDeviceType),
        )
        Assert.assertEquals(
            "VBDM (Vital Series) password mismatch",
            "6ea33673",
            password(transformers, DeviceType.VitalSeriesDeviceType),
        )
    }

    @Test
    fun emptyStoreFallsBackToBundledSecrets() = runBlocking {
        val store = InMemorySecretsStore()
        val repository = DataStoreSecretsRepository(store)

        Assert.assertEquals(BundledSecrets.value, repository.secretsFlow.first())
        Assert.assertEquals(BundledSecrets.value, repository.getSecrets())
    }

    @Test
    fun importedSecretsOverrideBundledSecrets() = runBlocking {
        val store = InMemorySecretsStore()
        val repository = DataStoreSecretsRepository(store)
        val imported = BundledSecrets.value.toBuilder()
            .setVbdmHmacKeys(
                Secrets.HmacKeys.newBuilder()
                    .setHmacKey1("000000000000000000000000")
                    .setHmacKey2("000000000000000000000000")
                    .build()
            )
            .build()
        Assert.assertFalse(imported.isMissingSecrets())

        repository.updateSecrets(imported)

        Assert.assertEquals(imported, repository.secretsFlow.first())
        Assert.assertEquals(imported, repository.getSecrets())
    }

    @Test
    fun partiallyStoredSecretsStillFallBackToBundledSecrets() = runBlocking {
        val store = InMemorySecretsStore(initial = Secrets.newBuilder().setAesKey("short").build())
        val repository = DataStoreSecretsRepository(store)

        Assert.assertEquals(BundledSecrets.value, repository.secretsFlow.first())
    }

    @Test
    fun nullBundledSecretsKeepTheOldEmptyBehaviour() = runBlocking {
        val store = InMemorySecretsStore()
        val repository = DataStoreSecretsRepository(store, bundledSecrets = null)

        Assert.assertTrue(repository.secretsFlow.first().isMissingSecrets())
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun password(
        transformers: Map<UShort, CryptographicTransformer>,
        deviceType: UShort,
    ): String = transformers.getValue(deviceType)
        .createNfcPassword(DexFileSecretsImporter.TEST_TAG)
        .toHexString()

    private class InMemorySecretsStore(
        initial: Secrets = Secrets.getDefaultInstance(),
    ) : DataStore<Secrets> {
        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()

        override val data: Flow<Secrets> = state

        override suspend fun updateData(transform: suspend (t: Secrets) -> Secrets): Secrets =
            mutex.withLock {
                val updated = transform(state.value)
                state.value = updated
                updated
            }
    }
}
