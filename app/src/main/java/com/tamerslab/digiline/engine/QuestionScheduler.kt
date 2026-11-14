package com.tamerslab.digiline.engine

import com.tamerslab.digiline.model.DigilineSeed
import com.tamerslab.digiline.model.QuestionCardDto
import com.tamerslab.digiline.model.QuestionKind
import com.tamerslab.digiline.model.QuestionSelection
import kotlin.random.Random

/**
 * Deterministic question selection for owned Digimon (contract §3.1, plan §6.1).
 *
 * Selection is stable for the same inputs: cards are sorted by id, recently asked cards are
 * excluded (with a safe fallback so an exhausted pool never silences the Digimon) and the index
 * comes from [DigilineSeed], never from `String.hashCode()` or input order.
 */
object QuestionScheduler {
    /**
     * Contract signature: pick one card or `null` for an empty pool. Prefer [schedule] when the
     * shuffled option order is needed as well.
     */
    fun select(
        candidates: List<QuestionCardDto>,
        kind: QuestionKind,
        seed: Long,
        recentIds: Set<String> = emptySet(),
        variant: Int = 0,
    ): QuestionCardDto? = schedule(candidates, kind, seed, recentIds, variant)?.card

    /**
     * Picks a card and freezes its option order.
     *
     * @param candidates cards of one pool (species or generic).
     * @param kind pool kind; filtering falls back to [candidates] if the pool is mixed/untagged.
     * @param seed persisted deterministic seed for this decision.
     * @param recentIds ids to avoid (anti-repeat); if every card is recent the pool is reused.
     * @param variant stable per-individual variant index (species variants, plan R8).
     */
    fun schedule(
        candidates: List<QuestionCardDto>,
        kind: QuestionKind,
        seed: Long,
        recentIds: Set<String> = emptySet(),
        variant: Int = 0,
    ): QuestionSelection? {
        if (candidates.isEmpty()) return null
        val matched = candidates.filter { matchesKind(it, kind) }
        val pool = matched.ifEmpty { candidates }
        if (pool.isEmpty()) return null
        val ordered = pool.sortedBy { it.id }
        val fresh = ordered.filterNot { it.id in recentIds }
        val available = fresh.ifEmpty { ordered }
        val decisionSeed = DigilineSeed.combine(
            seed,
            kind.ordinal.toLong(),
            available.size.toLong(),
            variant.toLong(),
        )
        val index = Random(decisionSeed).nextInt(available.size)
        val card = available[index]
        return arrange(card, decisionSeed)
    }

    /**
     * Freezes a deterministic option order for [card]. The authored order is still preserved as a
     * fallback for ids missing from the selection.
     */
    fun arrange(card: QuestionCardDto, seed: Long): QuestionSelection {
        val optionIds = card.options.map { it.id }.sorted()
        if (optionIds.size <= 1) return QuestionSelection(card, optionIds, seed)
        val shuffled = optionIds.shuffled(Random(DigilineSeed.combine(seed, optionIds.size.toLong())))
        return QuestionSelection(card, shuffled, seed)
    }

    private fun matchesKind(card: QuestionCardDto, kind: QuestionKind): Boolean = when (kind) {
        QuestionKind.SPECIES -> card.isSpeciesQuestion
        QuestionKind.GENERIC -> !card.isSpeciesQuestion || card.tags.contains("generic")
    }
}
