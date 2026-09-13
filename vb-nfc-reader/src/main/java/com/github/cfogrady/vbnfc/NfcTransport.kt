package com.github.cfogrady.vbnfc

/** Small transport boundary for exercising real NFC protocol code with fault injection. */
interface NfcTransport {
    val tagId: ByteArray
    fun transceive(command: ByteArray): ByteArray
}

object NfcTransferSafety {
    // NXP Type 2 Tag WRITE acknowledgement, datasheet NTAG213_215_216, table 23.
    fun requireWriteAck(response: ByteArray) {
        check(response.size == 1 && (response[0].toInt() and 0x0f) == 0x0a) {
            "The watch did not acknowledge the NFC write. The transfer is incomplete."
        }
    }

    fun requireReadBack(expected: ByteArray, actual: ByteArray) {
        check(expected.contentEquals(actual)) { "Watch read-back differs from the sent data. Transfer was not committed." }
    }
}
