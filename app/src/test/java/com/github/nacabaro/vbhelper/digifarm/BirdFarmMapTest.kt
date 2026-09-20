package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.digifarm.map.BirdFarmMap
import com.github.nacabaro.vbhelper.digifarm.map.IsoTile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BirdFarmMapTest {
    @Test
    fun projectionRoundTripsAtTileCenters() {
        listOf(IsoTile(8, 8), IsoTile(20, 12), IsoTile(38, 35)).forEach { tile ->
            assertEquals(tile, BirdFarmMap.unproject(BirdFarmMap.project(tile)))
        }
    }

    @Test
    fun everySafeSpawnResolvesToWalkableGround() {
        assertEquals(12, BirdFarmMap.safeSpawns.distinct().size)
        BirdFarmMap.safeSpawns.forEach { spawn ->
            val closest = BirdFarmMap.closestWalkable(spawn)
            assertTrue(BirdFarmMap.isWalkable(closest))
        }
    }

    @Test
    fun routesNeverLeaveWalkableTiles() {
        BirdFarmMap.safeSpawns.zipWithNext().forEach { (start, end) ->
            val path = BirdFarmMap.findPath(start, end)
            assertTrue("No route between $start and $end", path.isNotEmpty())
            path.forEach { point ->
                assertTrue("Path left walkable ground at $point", BirdFarmMap.isWalkable(point))
            }
        }
    }

    @Test
    fun bothBridgesRouteInBothDirections() {
        assertEquals(2, BirdFarmMap.bridgePortals.size)
        BirdFarmMap.bridgePortals.forEach { portal ->
            assertTrue("${portal.id} north head walkable", BirdFarmMap.isWalkable(portal.north))
            assertTrue("${portal.id} south head walkable", BirdFarmMap.isWalkable(portal.south))
            val northToSouth = BirdFarmMap.findPath(portal.north, portal.south)
            val southToNorth = BirdFarmMap.findPath(portal.south, portal.north)
            assertTrue("${portal.id} has no north->south route", northToSouth.isNotEmpty())
            assertTrue("${portal.id} has no south->north route", southToNorth.isNotEmpty())
            (northToSouth + southToNorth).forEach { point ->
                assertTrue("${portal.id} path left walkable ground at $point", BirdFarmMap.isWalkable(point))
            }
        }
    }

    @Test
    fun activityPointsAreWalkableAndManifestIsVersioned() {
        val manifest = BirdFarmMap.manifest()
        assertEquals("bird_digifarm", manifest.mapId)
        assertEquals(1, manifest.version)
        assertTrue(manifest.walkableTileCount > 100)
        BirdFarmMap.activityPoints.forEach { (activity, points) ->
            assertTrue("$activity has no points", points.isNotEmpty())
            points.forEach { point ->
                val snapped = BirdFarmMap.closestWalkable(point)
                assertTrue("$activity point $point resolves outside walkable ground", BirdFarmMap.isWalkable(snapped))
            }
        }
    }
}
