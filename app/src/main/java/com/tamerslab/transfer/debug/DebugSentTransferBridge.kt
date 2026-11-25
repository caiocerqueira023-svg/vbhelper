package com.tamerslab.transfer.debug

import com.tamerslab.hardware.nfc.data.NfcCharacter

/**
 * Debug-only hand-off between the simulated send and the simulated receive.
 *
 * The real watch keeps the character payload after the app deletes its storage row. We cannot
 * store the payload on a watch, so the debug write retains the exact marshalled character here and
 * the next debug read restores it with the original transfer token/device key. This keeps the
 * identity and lineage intact instead of creating an unrelated duplicate.
 */
object DebugSentTransferBridge {
    data class SentCharacter(
        val character: NfcCharacter,
        val deviceKey: String,
        val individualId: String,
    )

    @Volatile
    private var sent: SentCharacter? = null

    fun store(character: NfcCharacter, deviceKey: String, individualId: String) {
        sent = SentCharacter(character, deviceKey, individualId)
    }

    fun peek(): SentCharacter? = sent

    fun clear() {
        sent = null
    }
}
