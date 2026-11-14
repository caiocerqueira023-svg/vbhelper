package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.EventLineDto
import com.tamerslab.digiline.model.ReactionPick
import kotlin.random.Random

/**
 * Deterministic event-reaction line picker (plan §8.2, contract §3.1).
 *
 * Replaces the old LLM reactions with authored `event_lines.json` lines. Personality-specific
 * lines are preferred; shared lines (`types` empty) are the fallback. Selection is stable for the
 * same `(event, type, seed)` and never throws on empty content (returns `null`).
 */
class ReactionLineProvider(private val lines: List<EventLineDto> = emptyList()) {
    /** Candidate lines for an event/personality: personality-specific when authored, else shared. */
    fun linesFor(event: String, type: Int): List<EventLineDto> {
        val key = event.trim()
        if (key.isEmpty()) return emptyList()
        val ordered = lines.filter { it.event == key }.sortedBy { it.id }
        if (ordered.isEmpty()) return emptyList()
        val typed = ordered.filter { it.types.isNotEmpty() && it.types.contains(type) }
        return typed.ifEmpty { ordered.filter { it.types.isEmpty() } }
    }

    /**
     * Picks one line. [excludedIds] are recently delivered ids; when the whole pool is excluded it
     * is reused (repeat-safe) instead of leaving the Digimon silent.
     */
    fun pick(
        event: String,
        type: Int,
        seed: Long,
        excludedIds: Set<String> = emptySet(),
    ): ReactionPick? {
        val candidates = linesFor(event, type)
        if (candidates.isEmpty()) return null
        val fresh = candidates.filterNot { it.id in excludedIds }
        val pool = fresh.ifEmpty { candidates }
        val decisionSeed = DigilineSeed.combine(
            seed,
            DigilineSeed.stableHash(event.trim()),
            type.toLong(),
            pool.size.toLong(),
        )
        val index = Random(decisionSeed).nextInt(pool.size)
        return ReactionPick(pool[index])
    }

    /** Picked mood delta (0 when there is no line at all). */
    fun moodDelta(event: String, type: Int, seed: Long): Int = pick(event, type, seed)?.moodDelta ?: 0

    /** Returns a provider over new lines (repository swapped a pack version). */
    fun withLines(lines: List<EventLineDto>): ReactionLineProvider = ReactionLineProvider(lines)

    val lineCount: Int get() = lines.size
}
