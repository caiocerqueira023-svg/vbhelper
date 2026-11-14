package com.tamerslab.digiline.native

import com.tamerslab.data.database.AppDatabase
import com.tamerslab.digiline.data.NativePersonalitySyncEntity
import com.tamerslab.domain.personality.DigimonPersonalityType

/**
 * Auto-sync wiring point for the app → game personality shift (D4/R2/R3).
 *
 * The app currently ships no Time Stranger save *writer* (its save capabilities are
 * read/provisional-copy only), so no component in normal play can hand exact save bytes to this
 * coordinator. This class exists so that a future guarded writer (SAF + backup + journal +
 * readback) needs only one call site: it prepares a verified candidate with the existing
 * [TimeStrangerPersonalitySync], owns the queue state transitions, and never writes files itself.
 *
 * Expected caller contract:
 * 1. `pending(individualId)` returns a queued shift.
 * 2. Writer loads the exact linked save bytes and resolves the native record key.
 * 3. `prepare(...)` returns `Ready(candidateBytes)`; the writer performs the guarded write.
 * 4. Only after the write + readback succeed, call [markWritten]; on refusal call [markUnavailable].
 */
class NativePersonalitySyncCoordinator(
    private val database: AppDatabase,
) {
    private val dao get() = database.digilineDao()

    enum class Status { NONE, PENDING, WRITTEN, UNAVAILABLE }

    suspend fun pending(individualId: String): NativePersonalitySyncEntity? =
        dao.nativeSync(individualId)?.takeIf { it.state == STATE_PENDING }

    suspend fun pendingAll(): List<NativePersonalitySyncEntity> = dao.nativeSyncsInState(STATE_PENDING)

    /**
     * Prepares the verified candidate for one queued shift. Returns `null` when there is nothing
     * queued for that individual. The returned candidate is safe to write only through the
     * transfer layer's guarded save-edit flow.
     */
    suspend fun prepare(
        individualId: String,
        sourceBytes: ByteArray,
        recordKey: String,
    ): NativePersonalitySyncOutcome? {
        val sync = dao.nativeSync(individualId) ?: return null
        if (sync.state == STATE_WRITTEN) return null
        val target = DigimonPersonalityType.entries.firstOrNull { it.number == sync.targetType }
            ?: return NativePersonalitySyncOutcome.Refused(
                NativePersonalitySyncOutcome.Refusal.MAPPING_UNVERIFIED
            )
        return TimeStrangerPersonalitySync.prepare(sourceBytes, recordKey, target)
    }

    /** Called by the writer after a verified write + readback. */
    suspend fun markWritten(individualId: String, now: Long = System.currentTimeMillis()) {
        val sync = dao.nativeSync(individualId) ?: return
        dao.upsertNativeSync(sync.copy(state = STATE_WRITTEN, updatedAt = now))
    }

    /** Records why the queued shift is not in the game yet (FARM/unverified/no write path). */
    suspend fun markUnavailable(
        individualId: String,
        state: String = STATE_UNAVAILABLE,
        now: Long = System.currentTimeMillis(),
    ) {
        val sync = dao.nativeSync(individualId) ?: return
        dao.upsertNativeSync(sync.copy(state = state, updatedAt = now))
    }

    suspend fun status(individualId: String): Status = when (dao.nativeSync(individualId)?.state) {
        STATE_PENDING -> Status.PENDING
        STATE_WRITTEN -> Status.WRITTEN
        null -> Status.NONE
        else -> Status.UNAVAILABLE
    }

    companion object {
        const val STATE_PENDING = "PENDING"
        const val STATE_WRITTEN = "WRITTEN"
        const val STATE_UNAVAILABLE = "UNAVAILABLE_WRITE_PATH"
    }
}
