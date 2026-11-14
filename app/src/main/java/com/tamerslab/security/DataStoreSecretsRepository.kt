package com.tamerslab.security

import androidx.datastore.core.DataStore
import com.tamerslab.hardware.nfc.CryptographicTransformer
import com.tamerslab.hardware.nfc.data.DeviceType
import com.tamerslab.security.proto.Secrets
import com.tamerslab.security.proto.Secrets.HmacKeys
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSecretsRepository(
    private val secretsDataStore: DataStore<Secrets>,
    private val bundledSecrets: Secrets? = BundledSecrets.value,
): SecretsRepository {
    /**
     * Emits the stored secrets, falling back to [bundledSecrets] while the store is still empty
     * or incomplete. Imported secrets always take precedence, so a manual import still overrides
     * the baked-in values (e.g. after Bandai rotates the keys).
     */
    override val secretsFlow = secretsDataStore.data.map { stored ->
        if (stored.isMissingSecrets()) bundledSecrets ?: stored else stored
    }

    override suspend fun updateSecrets(secrets: Secrets) {
        secretsDataStore.updateData {
            secrets
        }
    }

    override suspend fun getSecrets(): Secrets {
        return secretsFlow.first()
    }
}

private fun Secrets.getHmacKeys(deviceTypeId: UShort): HmacKeys {
    return when(deviceTypeId) {
        DeviceType.VitalBraceletBEDeviceType -> this.beHmacKeys
        DeviceType.VitalCharactersDeviceType -> this.vbcHmacKeys
        DeviceType.VitalSeriesDeviceType -> this.vbdmHmacKeys
        else -> throw IllegalArgumentException("Unknown DeviceTypeId")
    }
}

fun Secrets.getCryptographicTransformerMap(): Map<UShort, CryptographicTransformer> {
    val cipher = this.vbCipherList.toIntArray()
    val beCipher = this.beCipherList.toIntArray()
    val vbdmHmacKeys = this.getHmacKeys(DeviceType.VitalSeriesDeviceType)
    val vbcHmacKeys = this.getHmacKeys(DeviceType.VitalCharactersDeviceType)
    val beHmacKeys = this.getHmacKeys(DeviceType.VitalBraceletBEDeviceType)
    return mapOf(
        Pair(DeviceType.VitalSeriesDeviceType, CryptographicTransformer(vbdmHmacKeys.hmacKey1, vbdmHmacKeys.hmacKey2, this.aesKey, cipher)),
        Pair(DeviceType.VitalCharactersDeviceType, CryptographicTransformer(vbcHmacKeys.hmacKey1, vbcHmacKeys.hmacKey2, this.aesKey, cipher)),
        Pair(DeviceType.VitalBraceletBEDeviceType, CryptographicTransformer(beHmacKeys.hmacKey1, beHmacKeys.hmacKey2, this.aesKey, beCipher)),
    )
}

fun Secrets.isMissingSecrets(): Boolean {
    return this.aesKey.length != 24 ||
            this.vbCipherList.size != 16 ||
            this.beCipherList.size != 16 ||
            this.vbdmHmacKeys.isMissingKey() ||
            this.vbcHmacKeys.isMissingKey() ||
            this.beHmacKeys.isMissingKey()
}

fun HmacKeys.isMissingKey(): Boolean {
    return this.hmacKey1.length != 24 || this.hmacKey2.length != 24
}