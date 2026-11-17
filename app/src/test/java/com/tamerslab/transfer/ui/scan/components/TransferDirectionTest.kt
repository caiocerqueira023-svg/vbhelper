package com.tamerslab.transfer.ui.scan.components

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the direction contract that selects the Digimon hand-off animation. */
class TransferDirectionTest {

    @Test
    fun `watch to app animates an arrival`() {
        assertEquals(
            DigimonTransferMode.ARRIVING,
            NfcTransferDirection.WATCH_TO_APP.toTransferMode(),
        )
    }

    @Test
    fun `app to watch animates a departure`() {
        assertEquals(
            DigimonTransferMode.DEPARTING,
            NfcTransferDirection.APP_TO_WATCH.toTransferMode(),
        )
    }
}
