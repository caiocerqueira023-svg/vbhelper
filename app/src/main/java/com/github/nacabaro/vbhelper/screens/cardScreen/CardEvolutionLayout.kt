package com.github.nacabaro.vbhelper.screens.cardScreen

import com.github.nacabaro.vbhelper.dtos.CardEvolutionLink

/** Logical dp coordinates shared by connector drawing and Compose node placement. */
data class CardChartPoint(val x: Float, val y: Float)
data class CardChartSize(val width: Float, val height: Float)
data class CardEvolutionLayoutNode(val id: Long, val stage: Int, val charaIndex: Int)
data class CardEvolutionPlacement(val node: CardEvolutionLayoutNode, val x: Float, val y: Float, val size: Float,
                                  val displayStage: Int = node.stage) {
    val centerX get() = x + size / 2
    val bottom get() = y + size
}
data class CardEvolutionRow(val stage: Int, val top: Float, val nodeIds: List<Long>)
data class CardEvolutionRoute(
    val fromId: Long,
    val toId: Long,
    val isFusion: Boolean,
    val fusionAttributes: Set<String>,
    val points: List<CardChartPoint>,
)
data class CardEvolutionSegment(val start: CardChartPoint, val end: CardChartPoint, val isJogress: Boolean)

data class CardEvolutionLayout(
    val width: Float,
    val height: Float,
    val nodes: Map<Long, CardEvolutionPlacement>,
    val rows: List<CardEvolutionRow>,
    val routes: List<CardEvolutionRoute>,
) {
    val size get() = CardChartSize(width, height)

    /** Draw overlapping buses once; repeated antialiasing must not thicken shared lines. */
    fun connectorSegments(includeJogress: Boolean): List<CardEvolutionSegment> {
        val grouped = linkedMapOf<Triple<Boolean, Boolean, Float>, MutableList<Pair<Float, Float>>>()
        routes.filter { !it.isFusion || includeJogress }.forEach { route ->
            route.points.zipWithNext().forEach { (start, end) ->
                if (start != end) {
                    val horizontal = start.y == end.y
                    val key = Triple(route.isFusion, horizontal, if (horizontal) start.y else start.x)
                    val a = if (horizontal) start.x else start.y
                    val b = if (horizontal) end.x else end.y
                    grouped.getOrPut(key) { mutableListOf() }.add(minOf(a, b) to maxOf(a, b))
                }
            }
        }
        return buildList {
            grouped.toSortedMap(compareBy({ it.first }, { it.second }, { it.third })).forEach { (key, spans) ->
                val sorted = spans.sortedBy { it.first }
                var start = sorted.first().first
                var end = sorted.first().second
                fun append() {
                    add(CardEvolutionSegment(
                        if (key.second) CardChartPoint(start, key.third) else CardChartPoint(key.third, start),
                        if (key.second) CardChartPoint(end, key.third) else CardChartPoint(key.third, end), key.first))
                }
                sorted.drop(1).forEach { span ->
                    if (span.first <= end) end = maxOf(end, span.second)
                    else { append(); start = span.first; end = span.second }
                }
                append()
            }
        }
    }

    companion object {
        const val NODE_SIZE = 72f
        private const val COLUMN_GAP = 20f
        private const val PADDING = 24f

        fun create(characters: List<CardEvolutionLayoutNode>, links: List<CardEvolutionLink>): CardEvolutionLayout {
            val ordered = characters.distinctBy { it.id }
                .sortedWith(compareBy({ it.stage }, { it.charaIndex }, { it.id }))
            if (ordered.isEmpty()) return CardEvolutionLayout(48f, 48f, emptyMap(), emptyList(), emptyList())
            val byId = ordered.associateBy { it.id }
            val validLinks = links.filter { it.fromId in byId && it.toId in byId }
            val displayStages = presentationStages(ordered, validLinks)
            val groups = validLinks.groupBy { Triple(it.fromId, it.toId, it.isJogress) }
                .toSortedMap(compareBy({ it.first }, { it.second }, { it.third }))
            val groupedRows = ordered.groupBy { displayStages.getValue(it.id) }.toSortedMap()
            val rowStages = groupedRows.keys.toList()
            val rowNodes = groupedRows.values.map { it.toMutableList() }
            val stageRows = rowStages.mapIndexed { index, stage -> stage to index }.toMap()
            val normalLinks = validLinks.filter { !it.isJogress &&
                displayStages.getValue(it.fromId) < displayStages.getValue(it.toId) }

            // Barycentric sweeps reduce crossings, with card index/ID as deterministic tie-breakers.
            repeat(2) {
                reorderRows(rowNodes, normalLinks, downwards = true)
                reorderRows(rowNodes, normalLinks, downwards = false)
            }
            val topMargin = PADDING
            val rowGap = 64f
            val graphWidth = PADDING * 2 + rowNodes.maxOf { it.size } * NODE_SIZE +
                (rowNodes.maxOf { it.size } - 1) * COLUMN_GAP
            val placements = linkedMapOf<Long, CardEvolutionPlacement>()
            val rows = rowNodes.mapIndexed { rowIndex, row ->
                val rowWidth = row.size * NODE_SIZE + (row.size - 1) * COLUMN_GAP
                val top = topMargin + rowIndex * (NODE_SIZE + rowGap)
                row.forEachIndexed { column, node ->
                    placements[node.id] = CardEvolutionPlacement(node,
                        (graphWidth - rowWidth) / 2 + column * (NODE_SIZE + COLUMN_GAP), top, NODE_SIZE, rowStages[rowIndex])
                }
                CardEvolutionRow(rowStages[rowIndex], top, row.map { it.id })
            }
            var usesSideLane = false
            val routes = groups.map { (key, conditions) ->
                val from = placements.getValue(key.first)
                val to = placements.getValue(key.second)
                val fromRow = stageRows.getValue(from.displayStage)
                val toRow = stageRows.getValue(to.displayStage)
                val points = when (toRow - fromRow) {
                    1 -> {
                        val middle = (from.bottom + to.y) / 2
                        listOf(CardChartPoint(from.centerX, from.bottom), CardChartPoint(from.centerX, middle),
                            CardChartPoint(to.centerX, middle), CardChartPoint(to.centerX, to.y))
                    }
                    0 -> {
                        val above = if (fromRow > 0) rows[fromRow - 1].top + NODE_SIZE + rowGap / 2 else from.y - 16f
                        val selfOffset = if (from.node.id == to.node.id) 12f else 0f
                        listOf(CardChartPoint(from.centerX - selfOffset, from.y),
                            CardChartPoint(from.centerX - selfOffset, above),
                            CardChartPoint(to.centerX + selfOffset, above), CardChartPoint(to.centerX + selfOffset, to.y))
                    }
                    else -> {
                        // Stage-skipping and backward routes travel outside the node area.
                        usesSideLane = true
                        val side = graphWidth + 12f
                        val departure = from.bottom + rowGap / 2
                        val arrival = if (toRow > 0) rows[toRow - 1].top + NODE_SIZE + rowGap / 2 else to.y - 16f
                        listOf(CardChartPoint(from.centerX, from.bottom), CardChartPoint(from.centerX, departure),
                            CardChartPoint(side, departure), CardChartPoint(side, arrival),
                            CardChartPoint(to.centerX, arrival), CardChartPoint(to.centerX, to.y))
                    }
                }
                CardEvolutionRoute(key.first, key.second, key.third,
                    conditions.mapNotNull { it.fusionAttribute }.toSortedSet(), points)
            }
            return CardEvolutionLayout(graphWidth + if (usesSideLane) 24f else 0f,
                rows.last().top + NODE_SIZE + 88f, placements, rows, routes)
        }

        private fun presentationStages(nodes: List<CardEvolutionLayoutNode>, links: List<CardEvolutionLink>): Map<Long, Int> {
            val byId = nodes.associateBy { it.id }
            val incoming = links.filter { it.fromId != it.toId }.groupBy { it.toId }
            val stages = nodes.associate { it.id to it.stage }.toMutableMap()
            // DiM stats often call terminal Jogress results stage VI too. Place results
            // reached exclusively from VI+ below their predecessors; explicit BEM VII stays VII.
            val waiting = nodes.filter { node -> node.stage == 5 && incoming[node.id]?.let { sources ->
                sources.isNotEmpty() && sources.all { byId.getValue(it.fromId).stage >= 5 }
            } == true }.mapTo(linkedSetOf()) { it.id }
            while (waiting.isNotEmpty()) {
                val ready = waiting.filter { id -> incoming.getValue(id).none { it.fromId in waiting } }
                if (ready.isEmpty()) {
                    // Stabilize only actual cycle members; descendants can still be promoted.
                    val cyclic = waiting.filter { origin ->
                        val queue = ArrayDeque<Long>()
                        incoming.getValue(origin).filter { it.fromId in waiting }.forEach { queue.add(it.fromId) }
                        val visited = hashSetOf<Long>()
                        var reachesSelf = false
                        while (queue.isNotEmpty() && !reachesSelf) {
                            val current = queue.removeFirst()
                            if (current == origin) reachesSelf = true
                            else if (visited.add(current)) incoming[current].orEmpty()
                                .filter { it.fromId in waiting }.forEach { queue.add(it.fromId) }
                        }
                        reachesSelf
                    }
                    if (cyclic.isEmpty()) break
                    waiting.removeAll(cyclic.toSet())
                    continue
                }
                ready.forEach { id ->
                    stages[id] = maxOf(6, incoming.getValue(id).maxOf { stages.getValue(it.fromId) + 1 })
                    waiting.remove(id)
                }
            }
            return stages
        }

        private fun reorderRows(rows: List<MutableList<CardEvolutionLayoutNode>>, links: List<CardEvolutionLink>, downwards: Boolean) {
            val ranks = mutableMapOf<Long, Float>()
            val traversal = if (downwards) rows else rows.reversed()
            for (row in traversal) {
                val currentRanks = row.mapIndexed { index, node -> node.id to (index + .5f) / row.size }.toMap()
                val scores = row.associate { node ->
                    val neighbors = links.asSequence().filter {
                        if (downwards) it.toId == node.id else it.fromId == node.id
                    }.mapNotNull { ranks[if (downwards) it.fromId else it.toId] }.toList()
                    node.id to if (neighbors.isEmpty()) currentRanks.getValue(node.id) else neighbors.average().toFloat()
                }
                row.sortWith(compareBy({ scores.getValue(it.id) }, { it.charaIndex }, { it.id }))
                row.forEachIndexed { index, node -> ranks[node.id] = (index + .5f) / row.size }
            }
        }
    }
}
