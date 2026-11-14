package com.tamerslab.digiline.data

import com.tamerslab.digiline.model.DigilineContentPack

/**
 * Stage/tier resolution shared by owned questions, quizzes and wild negotiation (plan §7.3, D12).
 *
 * Order: game/card stage -> species profile level -> tier 2 fallback. The source value is only a
 * difficulty hint for authored content; it never changes stats or outcomes.
 */
object DigilineStages {
    const val DEFAULT_TIER = 2
    const val MIN_TIER = 1
    const val MAX_TIER = 4

    /** Card stages are 0..5 (Baby..Mega in the card model); map the broad growth bands. */
    fun tierForCardStage(stage: Int?): Int? = when (stage) {
        null -> null
        in 0..1 -> 1
        in 2..3 -> 2
        in 4..4 -> 3
        in 5..5 -> 4
        else -> null
    }

    /** Species profile level strings used by the catalog ("Child", "Adult", "Perfect", "Ultimate", ...). */
    fun tierForLevel(level: String?): Int? {
        val normalized = level?.trim()?.lowercase() ?: return null
        if (normalized.isEmpty()) return null
        return when {
            normalized.contains("baby") -> 1
            normalized.contains("child") || normalized.contains("rookie") -> 1
            normalized.contains("adult") || normalized.contains("champion") -> 2
            normalized.contains("armor") || normalized.contains("hybrid") -> 2
            normalized.contains("perfect") -> 3
            normalized.contains("ultimate") -> 4
            normalized.contains("mega") -> 4
            else -> null
        }
    }

    fun tier(cardStage: Int?, level: String?): Int =
        (tierForCardStage(cardStage) ?: tierForLevel(level) ?: DEFAULT_TIER).coerceIn(MIN_TIER, MAX_TIER)

    /** Normalized species key matching the content-pack lookup rules. */
    fun speciesKey(name: String?): String? =
        name?.takeIf { it.isNotBlank() }
            ?.let { DigilineContentPack.normalizeSpeciesKey(it) }
            ?.takeIf { it.isNotEmpty() }
}
