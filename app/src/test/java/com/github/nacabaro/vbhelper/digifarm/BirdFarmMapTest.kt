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
}
