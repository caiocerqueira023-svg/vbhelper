package com.tamerslab.digiline.freechat.content

import com.tamerslab.digiline.freechat.model.FreeChatContent

/**
 * File keys listed by `manifest.json` (contract §1: asset layout
 * `app/src/main/assets/freechat/v1/en/`).
 *
 * Workstream A owns the assets; the defaults below are used only when a manifest omits a key, so a
 * pack that always ships `free_chat.json`/`species_voice.json` keeps loading even if the manifest
 * is minimal.
 */
object FreeChatContentFiles {
    const val MANIFEST: String = "manifest.json"
    const val INTENTS: String = "intents"
    const val SPECIES_VOICE: String = "speciesVoice"
    const val DATABASE_FALLBACK: String = "databaseFallback"
    const val DEFAULT_INTENTS_FILE: String = "free_chat.json"
    const val DEFAULT_SPECIES_VOICE_FILE: String = "species_voice.json"
    const val DEFAULT_DATABASE_FALLBACK_FILE: String = "database_fallback.json"
}

/**
 * Reads the immutable Free Talk content pack (contract §3).
 *
 * The default root is the shipped pack directory produced by workstream A. Implementations may
 * throw [IllegalStateException] whose message names the offending file (`"<file>: <reason>"`);
 * the process-wide provider turns every failure into `null` (fail-open: the feature is hidden
 * instead of breaking chat).
 */
interface FreeChatContentSource {
    suspend fun load(root: String = DEFAULT_ASSET_ROOT): FreeChatContent

    companion object {
        /** Default assets root produced by workstream A: `app/src/main/assets/freechat/v1/en`. */
        const val DEFAULT_ASSET_ROOT: String = "freechat/v1/en"
    }
}
