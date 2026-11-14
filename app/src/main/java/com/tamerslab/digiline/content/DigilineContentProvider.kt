package com.tamerslab.digiline.content

import com.tamerslab.digiline.model.DigilineContentPack
import timber.log.Timber

/**
 * Process-wide cache for the parsed DigiLine content pack.
 *
 * The pack is parsed once (on the IO dispatcher inside the loader) and reused by the owned and wild
 * repositories. A missing/malformed pack must never crash normal gameplay: the first failure is
 * logged loudly and an empty pack is served instead, so all lookups return safe `null`/empty
 * results and the DigiLine shows no authored cards rather than breaking the app.
 */
class DigilineContentProvider(
    private val loader: DigilineContentLoader,
) {
    private var cached: DigilineContentPack? = null
    private var failedOnce: Boolean = false

    suspend fun get(): DigilineContentPack {
        cached?.let { return it }
        val pack = try {
            loader.load()
        } catch (failure: Exception) {
            if (!failedOnce) {
                failedOnce = true
                Timber.e(failure, "DigiLine content pack unavailable; serving an empty pack")
            }
            DigilineContentPack.empty()
        }
        cached = pack
        return pack
    }

    /** True when the loaded pack contains usable authored content. */
    suspend fun hasContent(): Boolean = get().hasContent
}
