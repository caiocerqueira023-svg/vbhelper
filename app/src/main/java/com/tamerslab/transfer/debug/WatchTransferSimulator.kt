package com.tamerslab.transfer.debug

import androidx.activity.ComponentActivity
import com.tamerslab.di.TamersLabApplication
import com.tamerslab.domain.identity.IndividualIdentity
import com.tamerslab.domain.identity.TransferFingerprint
import com.tamerslab.domain.identity.WatchTransfer
import com.tamerslab.quests.QuestProgress
import com.tamerslab.rank.data.RankEvents
import com.tamerslab.rank.domain.TxpSource
import com.tamerslab.transfer.data.WatchTransferRepository
import com.tamerslab.transfer.ui.scan.converters.FromNfcConverter
import com.tamerslab.transfer.ui.scan.converters.NfcImportResult
import com.tamerslab.transfer.ui.scan.converters.ToNfcConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Debug-only replacement for the physical watch transfer. It exercises the same conversion,
 * identity, quest-baseline and storage paths as the real NFC flow, but never touches NFC.
 *
 * - [receive] rebuilds one stored Digimon as an NFC payload and imports it with a fresh identity,
 *   exactly as if the watch had just returned a character.
 * - [send] prepares a real one-way transfer receipt, captures the quest baseline, completes it
 *   (which removes the source from storage) and awards the NFC write TXP.
 */
class WatchTransferSimulator(private val activity: ComponentActivity) {
    private val application = activity.applicationContext as TamersLabApplication
    private val database = application.container.db

    suspend fun receive(sourceCharacterId: Long): Long = withContext(Dispatchers.IO) {
        val nfc = ToNfcConverter(activity).characterToNfc(sourceCharacterId)
        // A fresh token guarantees a new individual instead of resolving to the source identity.
        nfc.appReserved1 = IndividualIdentity.encode(IndividualIdentity.generate())
        val result = FromNfcConverter(activity, sourceDevice = DEBUG_READ_DEVICE)
            .addCharacter(nfc) { _, _ -> }
        when (result) {
            is NfcImportResult.Stored -> result.characterId
            NfcImportResult.MissingCard ->
                error("The simulated read references a DIM/BEM that is not imported.")
            NfcImportResult.AmbiguousCard ->
                error("The simulated read matches more than one imported DIM/BEM.")
        }
    }

    suspend fun send(sourceCharacterId: Long) = withContext(Dispatchers.IO) {
        val nfc = ToNfcConverter(activity).characterToNfc(sourceCharacterId)
        val token = IndividualIdentity.generate()
        nfc.appReserved1 = IndividualIdentity.encode(token)
        val source = database.userCharacterDao().getCharacter(sourceCharacterId)
        val transfer = WatchTransfer.capture(token, source.individualId, nfc).copy(
            sourceCharacterId = sourceCharacterId,
            cardId = database.cardDao().getCardByCharacterIdSync(sourceCharacterId)?.id,
            sourceFingerprint = TransferFingerprint.of(source),
            // A unique key keeps the simulated transfer away from real watch receipts.
            deviceKey = "debug-simulator-${UUID.randomUUID()}",
        )
        val transfers = WatchTransferRepository(database)
        transfers.prepare(transfer)
        database.runInTransaction {
            QuestProgress(database).captureExportLocked(
                token,
                source.individualId,
                nfc,
                System.currentTimeMillis(),
            )
        }
        transfers.complete(transfer)
        database.watchTransferDao().consume(token)
        RankEvents.award(
            TxpSource.NFC_WRITE,
            "debug:nfc:write:$token",
            digimonRef = source.individualId,
            origin = "DEBUG",
        )
    }

    private companion object {
        const val DEBUG_READ_DEVICE = "debug-simulator-read"
    }
}
