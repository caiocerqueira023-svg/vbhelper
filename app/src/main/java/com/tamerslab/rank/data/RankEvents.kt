package com.tamerslab.rank.data

import com.tamerslab.rank.domain.RankEventSink
import com.tamerslab.rank.domain.TxpAward
import com.tamerslab.rank.domain.TxpSource

/**
 * Process-wide sink for Tamer XP events. Existing feature code (NFC scan/transfer, battles, quests,
 * dex, items, storage, world) calls [award] from its success path; the rank repository installs
 * itself as the sink at startup. Keeping this tiny indirection avoids touching every call site's
 * constructor graph while still making the sources explicit and testable.
 */
object RankEvents {
    @Volatile
    private var sink: RankEventSink? = null

    fun install(sink: RankEventSink) {
        this.sink = sink
    }

    fun award(
        source: TxpSource,
        idempotencyKey: String,
        amount: Int = source.defaultAmount,
        digimonRef: String? = null,
        origin: String? = null,
        metadata: String? = null,
    ) {
        sink?.submit(
            TxpAward(
                source = source,
                idempotencyKey = idempotencyKey,
                amount = amount,
                digimonRef = digimonRef,
                origin = origin,
                metadata = metadata,
            ),
        )
    }
}
