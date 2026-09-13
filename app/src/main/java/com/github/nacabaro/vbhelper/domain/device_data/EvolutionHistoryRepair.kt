package com.github.nacabaro.vbhelper.domain.device_data

/** Reconstructs a lineage using the imported card's actual evolution graph. */
object EvolutionHistoryRepair {
    data class Species(val id: Long, val cardId: Long, val charaIndex: Int, val stage: Int)
    data class Route(val fromId: Long, val toId: Long)
    data class Entry(val speciesId: Long, val date: Long)
    data class Result(val entries: List<Entry>, val complete: Boolean)

    fun repair(
        current: Species,
        species: List<Species>,
        routes: List<Route>,
        history: List<Entry>,
        now: Long,
    ): Result {
        val nodes = species.filter { it.cardId == current.cardId }.associateBy { it.id } +
            (current.id to current)
        val recorded = history.associateBy { it.speciesId }
        val parents = routes.distinct().mapNotNull { route ->
            val from = nodes[route.fromId] ?: return@mapNotNull null
            val to = nodes[route.toId] ?: return@mapNotNull null
            // No sideways evolutions, skipped stages, higher forms or foreign DIMs.
            if (from.stage < 0 || from.stage != to.stage - 1) null else to.id to from
        }.groupBy({ it.first }, { it.second })
        val memo = mutableMapOf<Long, List<Species>>()

        fun bestPath(node: Species): List<Species> = memo.getOrPut(node.id) {
            val candidates = parents[node.id].orEmpty()
                .sortedWith(compareBy<Species> { it.charaIndex }.thenBy { it.id })
                .map { bestPath(it) + node }
            // Search all branches: a recorded predecessor may be a dead end.
            candidates.maxWithOrNull(
                compareBy<List<Species>> { it.first().stage == 0 }
                    .thenBy { it.size }
                    .thenBy { path -> path.count { it.id in recorded } }
            ) ?: listOf(node)
        }

        val path = bestPath(current)
        // Device dates can go backwards. Order by lineage, preserving known dates.
        // Missing forms inherit the next known date; no invented time spent training.
        var nextDate = recorded[current.id]?.date ?: history.lastOrNull()?.date ?: now
        val entries = path.asReversed().map { node ->
            val entry = recorded[node.id] ?: Entry(node.id, nextDate)
            nextDate = entry.date
            entry
        }.asReversed()
        return Result(entries, complete = path.first().stage == 0)
    }
}
