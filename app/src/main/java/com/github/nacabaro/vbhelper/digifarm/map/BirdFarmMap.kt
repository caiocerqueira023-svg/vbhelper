package com.github.nacabaro.vbhelper.digifarm.map

import kotlin.math.abs
import kotlin.math.hypot

data class MapPoint(val x: Float, val y: Float)
data class IsoTile(val u: Int, val v: Int)

/** Logical isometric navigation data laid over the supplied Bird Digi-Farm art. */
object BirdFarmMap {
    const val width = 512f
    const val height = 736f
    const val tileWidth = 32f
    const val tileHeight = 16f
    private const val originX = 256f
    private const val originY = 8f

    private data class Polygon(val points: List<MapPoint>) {
        fun contains(point: MapPoint): Boolean {
            var inside = false
            var j = points.lastIndex
            for (i in points.indices) {
                val a = points[i]
                val b = points[j]
                if ((a.y > point.y) != (b.y > point.y) &&
                    point.x < (b.x - a.x) * (point.y - a.y) / (b.y - a.y) + a.x
                ) inside = !inside
                j = i
            }
            return inside
        }
    }

    // Conservative feet-only surfaces. Decorative edges, water, buildings and cliffs stay blocked.
    private val walkableAreas = listOf(
        Polygon(listOf(MapPoint(21f, 121f), MapPoint(125f, 31f), MapPoint(290f, 6f), MapPoint(378f, 39f), MapPoint(353f, 157f), MapPoint(301f, 199f), MapPoint(288f, 243f), MapPoint(212f, 260f), MapPoint(42f, 232f))),
        Polygon(listOf(MapPoint(281f, 217f), MapPoint(355f, 218f), MapPoint(392f, 253f), MapPoint(322f, 279f))),
        Polygon(listOf(MapPoint(284f, 269f), MapPoint(383f, 217f), MapPoint(506f, 239f), MapPoint(507f, 445f), MapPoint(389f, 493f), MapPoint(253f, 452f), MapPoint(211f, 337f))),
        Polygon(listOf(MapPoint(325f, 482f), MapPoint(409f, 472f), MapPoint(425f, 523f), MapPoint(355f, 548f))),
        Polygon(listOf(MapPoint(6f, 551f), MapPoint(119f, 493f), MapPoint(275f, 527f), MapPoint(375f, 553f), MapPoint(494f, 626f), MapPoint(461f, 701f), MapPoint(341f, 733f), MapPoint(77f, 729f), MapPoint(2f, 692f)))
    )

    private val blockedAreas = listOf(
        Polygon(listOf(MapPoint(0f, 579f), MapPoint(132f, 563f), MapPoint(144f, 668f), MapPoint(0f, 692f))),
        Polygon(listOf(MapPoint(278f, 629f), MapPoint(319f, 607f), MapPoint(356f, 621f), MapPoint(356f, 657f), MapPoint(318f, 678f), MapPoint(278f, 658f)))
    )

    val walkableTiles: Set<IsoTile> by lazy {
        buildSet {
            for (u in -8..88) for (v in -8..88) {
                val point = project(IsoTile(u, v))
                if (isWalkable(point)) add(IsoTile(u, v))
            }
        }
    }

    val safeSpawns: List<MapPoint> = listOf(
        MapPoint(205f, 153f), MapPoint(112f, 191f), MapPoint(267f, 112f), MapPoint(155f, 130f),
        MapPoint(348f, 322f), MapPoint(430f, 389f), MapPoint(288f, 367f), MapPoint(420f, 300f),
        MapPoint(186f, 614f), MapPoint(249f, 692f), MapPoint(399f, 640f), MapPoint(90f, 705f)
    )

    fun project(tile: IsoTile): MapPoint = MapPoint(
        originX + (tile.u - tile.v) * tileWidth / 2f,
        originY + (tile.u + tile.v) * tileHeight / 2f
    )

    fun unproject(point: MapPoint): IsoTile {
        val x = (point.x - originX) / (tileWidth / 2f)
        val y = (point.y - originY) / (tileHeight / 2f)
        return IsoTile(((x + y) / 2f).toInt(), ((y - x) / 2f).toInt())
    }

    fun isWalkable(point: MapPoint): Boolean =
        walkableAreas.any { it.contains(point) } && blockedAreas.none { it.contains(point) }

    fun closestWalkable(point: MapPoint): MapPoint = walkableTiles
        .asSequence()
        .map(::project)
        .minByOrNull { hypot((it.x - point.x).toDouble(), (it.y - point.y).toDouble()) }
        ?: safeSpawns.first()

    fun findPath(start: MapPoint, goal: MapPoint): List<MapPoint> {
        val startTile = closestTile(start)
        val goalTile = closestTile(goal)
        if (startTile == goalTile) return listOf(project(goalTile))

        val frontier = java.util.PriorityQueue(compareBy<Pair<IsoTile, Int>> { it.second })
        val cameFrom = mutableMapOf<IsoTile, IsoTile?>()
        val cost = mutableMapOf(startTile to 0)
        frontier += startTile to 0
        cameFrom[startTile] = null
        while (frontier.isNotEmpty()) {
            val current = frontier.remove().first
            if (current == goalTile) break
            neighbors(current).forEach { next ->
                val newCost = cost.getValue(current) + 1
                if (newCost < (cost[next] ?: Int.MAX_VALUE)) {
                    cost[next] = newCost
                    val priority = newCost + abs(next.u - goalTile.u) + abs(next.v - goalTile.v)
                    frontier += next to priority
                    cameFrom[next] = current
                }
            }
        }
        if (goalTile !in cameFrom) return emptyList()
        val path = mutableListOf<IsoTile>()
        var current: IsoTile? = goalTile
        while (current != null) {
            path += current
            current = cameFrom[current]
        }
        return path.asReversed().drop(1).map(::project)
    }

    private fun closestTile(point: MapPoint): IsoTile = walkableTiles.minByOrNull {
        val projected = project(it)
        hypot((projected.x - point.x).toDouble(), (projected.y - point.y).toDouble())
    } ?: unproject(safeSpawns.first())

    private fun neighbors(tile: IsoTile): List<IsoTile> = listOf(
        IsoTile(tile.u + 1, tile.v), IsoTile(tile.u - 1, tile.v),
        IsoTile(tile.u, tile.v + 1), IsoTile(tile.u, tile.v - 1)
    ).filter(walkableTiles::contains)
}
