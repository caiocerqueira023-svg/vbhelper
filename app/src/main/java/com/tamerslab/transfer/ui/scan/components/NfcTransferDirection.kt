package com.tamerslab.transfer.ui.scan.components

/**
 * Direction of one NFC transfer. The watch is the passive tag: the phone is always the reader, so
 * only the data flow changes between receiving a Digimon and sending it back. The direction selects
 * the hand-off animation played by [DigimonTransferAura].
 */
enum class NfcTransferDirection {
    /** Watch -> app: a character is being read into the app (Digimon arriving). */
    WATCH_TO_APP,

    /** App -> watch: a character is being written back to the bracelet (Digimon departing). */
    APP_TO_WATCH,
}

fun NfcTransferDirection.toTransferMode(): DigimonTransferMode = when (this) {
    NfcTransferDirection.WATCH_TO_APP -> DigimonTransferMode.ARRIVING
    NfcTransferDirection.APP_TO_WATCH -> DigimonTransferMode.DEPARTING
}
