package com.github.nacabaro.vbhelper.domain.identity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Kept across restarts so a lost NFC acknowledgement cannot create a second individual. */
@Entity
data class WatchImportReceipt(
    @PrimaryKey val fingerprint: String,
    val characterId: Long,
    val individualId: String,
)
