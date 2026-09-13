package com.github.nacabaro.vbhelper.source

import com.github.cfogrady.vitalwear.protos.Character
import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class VitalWearCharacterImporterTest {
    @Test
    fun normalizeTransformationCountdownMinutes_keepsOneMinuteMinimumWhenCharacterCanStillEvolve() {
        assertEquals(1, normalizeTransformationCountdownMinutes(0, hasPossibleTransformations = true))
    }

    @Test
    fun normalizeTransformationCountdownMinutes_preservesPositiveCountdownWhenCharacterCanStillEvolve() {
        assertEquals(12, normalizeTransformationCountdownMinutes(12, hasPossibleTransformations = true))
    }

    @Test
    fun normalizeTransformationCountdownMinutes_allowsZeroWhenNoTransformationsRemain() {
        assertEquals(0, normalizeTransformationCountdownMinutes(0, hasPossibleTransformations = false))
    }

    @Test
    fun resolveDeviceType_prefersExplicitTransferTypeOverFallbackHeuristic() {
        assertEquals(
            DeviceType.VBDevice,
            resolveDeviceType(
                transferDeviceType = Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_VB,
                fallbackIsBeCharacter = true,
            )
        )
    }

    @Test
    fun resolveDeviceType_fallsBackToBeWhenTransferTypeIsUnspecified() {
        assertEquals(
            DeviceType.BEDevice,
            resolveDeviceType(
                transferDeviceType = Character.CharacterStats.TransferDeviceType.TRANSFER_DEVICE_TYPE_UNSPECIFIED,
                fallbackIsBeCharacter = true,
            )
        )
    }

    @Test
    fun resolveDeviceType_usesFallbackWhenTransferTypeIsUnrecognized() {
        assertEquals(
            DeviceType.VBDevice,
            resolveDeviceType(
                transferDeviceType = Character.CharacterStats.TransferDeviceType.UNRECOGNIZED,
                fallbackIsBeCharacter = false,
            )
        )
    }

}
