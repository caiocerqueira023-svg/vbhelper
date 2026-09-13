package com.github.nacabaro.vbhelper.source

import com.github.nacabaro.vbhelper.database.AppDatabase
import com.github.nacabaro.vbhelper.domain.identity.TransferFingerprint
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer

class WatchTransferRepository(private val db: AppDatabase) {
    /** Durable before any NFC write. A failed/uncertain send keeps both source and receipt. */
    fun prepare(transfer: WatchTransfer) = db.runInTransaction {
        validateSource(transfer)
        val previous = db.watchTransferDao().getByIndividualId(transfer.individualId)
        check(previous == null || previous == transfer) { "An unfinished transfer already exists for this individual." }
        check(db.watchTransferDao().getPendingForWatch(transfer.deviceKey).none {
            it.individualId != transfer.individualId
        }) {
            "Já existe um Digimon enviado a este relógio que ainda não retornou. " +
                "Receba-o no app antes de enviar outro, para preservar nome e conversa."
        }
        db.watchTransferDao().record(transfer)
    }

    /** Called only after the transport confirms success; independent of Compose/lifecycle. */
    fun complete(transfer: WatchTransfer) = db.runInTransaction {
        check(db.watchTransferDao().get(transfer.token) == transfer) { "Transfer receipt is missing or changed." }
        validateSource(transfer)
        db.userCharacterDao().deleteCharacterById(requireNotNull(transfer.sourceCharacterId))
    }

    private fun validateSource(transfer: WatchTransfer) {
        val source = db.userCharacterDao().getCharacterSync(requireNotNull(transfer.sourceCharacterId))
        check(source != null && source.individualId == transfer.individualId &&
            TransferFingerprint.of(source) == transfer.sourceFingerprint) {
            "The Digimon changed after transfer preparation. Its storage copy has been preserved."
        }
    }
}
