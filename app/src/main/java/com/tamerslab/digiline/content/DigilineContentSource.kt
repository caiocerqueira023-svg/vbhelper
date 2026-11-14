package com.tamerslab.digiline.content

/**
 * The immutable in-memory content pack model is owned by workstream B
 * (`com.tamerslab.digiline.model`, contract §3). This alias keeps loader/validator call sites in
 * the content package readable without declaring a second pack class.
 */
typealias DigilineContentPack = com.tamerslab.digiline.model.DigilineContentPack

/** File keys listed by `manifest.json` (contract §1: asset layout `digiline/v1/<locale>/...`). */
object DigilineContentFiles {
    const val MANIFEST: String = "manifest.json"
    const val SPECIES: String = "species"
    const val GENERIC: String = "generic"
    const val QUIZ: String = "quiz"
    const val NEGOTIATION: String = "negotiation"
    const val PERSUASION: String = "persuasion"
    const val EVENTS: String = "events"
    const val LORE: String = "lore"

    /** Required manifest file keys; the loader refuses a manifest that omits any of them. */
    val REQUIRED_KEYS: List<String> = listOf(SPECIES, GENERIC, QUIZ, NEGOTIATION, PERSUASION, EVENTS, LORE)
}

/** Reads an immutable DigiLine content pack from an asset root (default `digiline/v1`). */
interface DigilineContentSource {
    suspend fun load(assetRoot: String = DEFAULT_ASSET_ROOT): DigilineContentPack

    companion object {
        /** Default assets root produced by workstream A: `app/src/main/assets/digiline/v1`. */
        const val DEFAULT_ASSET_ROOT: String = "digiline/v1"
    }
}
