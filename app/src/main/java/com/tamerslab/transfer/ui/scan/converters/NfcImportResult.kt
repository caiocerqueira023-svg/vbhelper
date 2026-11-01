package com.tamerslab.transfer.ui.scan.converters

/** Radio acknowledgment depends on a committed record, never on translated feedback text. */
sealed interface NfcImportResult {
    data class Stored(val characterId: Long) : NfcImportResult
    data object MissingCard : NfcImportResult
    data object AmbiguousCard : NfcImportResult

    val saved: Boolean get() = this is Stored
}
