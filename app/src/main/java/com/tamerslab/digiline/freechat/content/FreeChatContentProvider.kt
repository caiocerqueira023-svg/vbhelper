package com.tamerslab.digiline.freechat.content

import com.tamerslab.digiline.freechat.model.FreeChatContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Process-wide cache for the parsed Free Talk content (contract §3).
 *
 * The pack is parsed once and reused by every gateway call. Unlike the DigiLine provider (which
 * serves an empty pack), Free Talk is optional UI: any load failure yields `null` and the feature
 * stays hidden (fail-open), so a missing/malformed pack never breaks the existing chat.
 *
 * A failure is memoized: the assets are fixed at install time, so retrying a broken pack on every
 * message would only add latency. [isPotentiallyAvailable] is the non-suspending hint the gateway
 * uses for its composer gate (true until a load has actually failed).
 */
class FreeChatContentProvider(
    private val source: FreeChatContentSource,
) {
    private val loadMutex = Mutex()

    @Volatile
    private var cached: FreeChatContent? = null

    @Volatile
    private var failed: Boolean = false

    /** Parsed pack, or `null` when the shipped pack is missing/malformed. */
    suspend fun get(): FreeChatContent? {
        cached?.let { return it }
        if (failed) return null
        return loadMutex.withLock {
            cached?.let { return@withLock it }
            if (failed) return@withLock null
            val loaded = try {
                source.load()
            } catch (failure: Exception) {
                failed = true
                Timber.e(failure, "Free Talk content pack unavailable; hiding free chat")
                null
            }
            cached = loaded
            loaded
        }
    }

    /**
     * False only after a load actually failed; true while the pack is cached or not yet attempted.
     * Used by `FreeChatRepository.isAvailable()` because that gate is synchronous.
     */
    fun isPotentiallyAvailable(): Boolean = cached != null || !failed
}
