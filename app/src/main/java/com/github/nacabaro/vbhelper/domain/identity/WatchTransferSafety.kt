package com.github.nacabaro.vbhelper.domain.identity

import com.github.cfogrady.vbnfc.data.NfcCharacter

/** No assumption that custom app bytes belong independently to each backup slot. */
object WatchTransferSafety {
    fun requireSingleExport(transfer: WatchTransfer, pending: List<WatchTransfer>) {
        check(pending.none { it.individualId != transfer.individualId }) {
            "Já existe um Digimon enviado a este relógio que ainda não retornou. " +
                "Receba-o no app antes de enviar outro, para preservar nome e conversa."
        }
    }

    fun checkReturn(character: NfcCharacter, sourceDevice: String,
                    transfer: WatchTransfer?, pending: List<WatchTransfer>): WatchTransfer? {
        if (transfer == null) {
            check(pending.isEmpty()) {
                "O relógio devolveu um identificador ausente ou desconhecido, mas há uma transferência pendente. " +
                    "O Digimon foi preservado no relógio; nenhuma identidade nova foi criada."
            }
        } else {
            check(transfer.deviceKey.isEmpty() || transfer.deviceKey == sourceDevice) {
                "This transfer belongs to another physical watch."
            }
            check(IndividualIdentity.decode(character.appReserved1) == transfer.token && transfer.matches(character)) {
                "O identificador devolvido pelo relógio não corresponde a este Digimon. " +
                    "Isso pode ocorrer ao alternar ativo e backup. O Digimon foi preservado no relógio."
            }
            check(pending.none { it.token != transfer.token && it.matches(character) }) {
                "Há mais de uma transferência compatível com os dados deste relógio. " +
                    "O recebimento foi interrompido para não associar o nome e a conversa de outro indivíduo."
            }
        }
        return transfer
    }
}
