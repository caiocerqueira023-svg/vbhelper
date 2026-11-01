package com.tamerslab.transfer.ui.scan

import com.tamerslab.hardware.nfc.data.NfcCharacter
import com.tamerslab.ActivityLifecycleListener
import com.tamerslab.domain.card.Card
import com.tamerslab.security.proto.Secrets
import kotlinx.coroutines.flow.Flow

interface ScanScreenController {
    val secretsFlow: Flow<Secrets>
    val transferStatus: Flow<String?> get() = kotlinx.coroutines.flow.flowOf(null)
    fun onClickRead(secrets: Secrets, onComplete: ()->Unit, onMultipleCards: (List<Card>) -> Unit)
    fun onClickCheckCard(secrets: Secrets, nfcCharacter: NfcCharacter, onComplete: () -> Unit)
    fun onClickWrite(secrets: Secrets, nfcCharacter: NfcCharacter, onComplete: () -> Unit)

    fun cancelRead()

    fun registerActivityLifecycleListener(key: String, activityLifecycleListener: ActivityLifecycleListener)
    fun unregisterActivityLifecycleListener(key: String)

    fun flushCharacter(cardId: Long)

    fun characterFromNfc(
        nfcCharacter: NfcCharacter,
        onMultipleCards: (List<Card>, NfcCharacter) -> Unit
    ): String
    suspend fun characterToNfc(characterId: Long): NfcCharacter?
}
