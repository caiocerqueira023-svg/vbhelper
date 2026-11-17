package com.tamerslab.transfer.ui.scan.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The NFC guide and the Digimon hand-off animation are driven by two pure mappings. Pinning them
 * here keeps the direction semantics (watch -> app is an arrival, app -> watch is a departure) and
 * the phase precedence stable without a device or Compose runtime.
 */
class NfcTransferGuideLogicTest {

    @Test
    fun `watch to app is an arrival and app to watch is a departure`() {
        assertEquals(
            DigimonTransferMode.ARRIVING,
            NfcTransferGuideLogic.modeFor(NfcTransferDirection.WATCH_TO_APP),
        )
        assertEquals(
            DigimonTransferMode.DEPARTING,
            NfcTransferGuideLogic.modeFor(NfcTransferDirection.APP_TO_WATCH),
        )
    }

    @Test
    fun `error wins over every other phase`() {
        assertEquals(
            NfcGuidePhase.ERROR,
            NfcTransferGuideLogic.phaseFor(error = true, success = true, transferring = true, linked = true),
        )
    }

    @Test
    fun `success wins over transferring but not over error`() {
        assertEquals(
            NfcGuidePhase.SUCCESS,
            NfcTransferGuideLogic.phaseFor(success = true, transferring = true, linked = true),
        )
    }

    @Test
    fun `transferring wins over linked`() {
        assertEquals(
            NfcGuidePhase.TRANSFERRING,
            NfcTransferGuideLogic.phaseFor(transferring = true, linked = true),
        )
    }

    @Test
    fun `linked reports the touch prompt and idle reports waiting`() {
        assertEquals(NfcGuidePhase.LINKED, NfcTransferGuideLogic.phaseFor(linked = true))
        assertEquals(NfcGuidePhase.WAITING, NfcTransferGuideLogic.phaseFor())
    }

    @Test
    fun `every phase maps to its own status label`() {
        val labels = NfcGuidePhase.entries.map(NfcTransferGuideLogic::statusLabelFor)
        assertEquals("status labels must be unique per phase", labels.size, labels.toSet().size)
    }

    @Test
    fun `directions use distinct labels`() {
        assertNotEquals(
            NfcTransferGuideLogic.directionLabelFor(NfcTransferDirection.WATCH_TO_APP),
            NfcTransferGuideLogic.directionLabelFor(NfcTransferDirection.APP_TO_WATCH),
        )
    }
}
