package com.tamerslab.farm

/**
 * Which artwork the farm diorama is allowed to show (owner request, 2026-10-30).
 *
 * - [MIXED] keeps every resident on its card (VB) sprites, the normal look.
 * - [VB_ONLY] only lets residents appear if VB sprite data exists for them.
 * - [DOT_ONLY] only lets residents appear if their species has Dot artwork, and draws that artwork.
 *
 * This is presentation only: it changes what the farm draws, never an individual's stored appearance
 * preference used by the rest of the app.
 */
enum class FarmSpriteFilter(val key: String) {
    MIXED("mixed"),
    VB_ONLY("vb"),
    DOT_ONLY("dot");

    companion object {
        fun from(key: String?): FarmSpriteFilter =
            entries.firstOrNull { it.key == key } ?: MIXED
    }
}

/** Range the farm roster may be limited to. */
const val FARM_MIN_RESIDENTS = 1
const val FARM_MAX_RESIDENTS = 12