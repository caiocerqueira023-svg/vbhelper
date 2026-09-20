package com.github.nacabaro.vbhelper.digifarm

import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dMap
import com.github.nacabaro.vbhelper.digifarm.map.Digifarm3dManifest
import com.github.nacabaro.vbhelper.digifarm.map.FarmWorldBounds
import com.github.nacabaro.vbhelper.digifarm.map.MapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Digifarm3dMapTest {
    private val manifest = Digifarm3dManifest(
        playableBounds = FarmWorldBounds(min = listOf(-1f, -0.75f), max = listOf(1f, 0.75f)),
        safeSpawns = listOf(listOf(-.5f, -.25f), listOf(.5f, .25f))
    )

    @Test
    fun legacyProjectionRoundTripsWithinOnePixel() {
        listOf(MapPoint(0f, 0f), MapPoint(256f, 368f), MapPoint(512f, 736f)).forEach { source ->
            val projected = Digifarm3dMap.legacyToWorld(source, manifest)
            val restored = Digifarm3dMap.worldToLegacy(projected, manifest)
            assertTrue(kotlin.math.abs(source.x - restored.x) <= 1f)
            assertTrue(kotlin.math.abs(source.y - restored.y) <= 1f)
        }
    }

    @Test
    fun worldPointsClampToPlayableBounds() {
        val clamped = Digifarm3dMap.clamp(
            com.github.nacabaro.vbhelper.digifarm.map.FarmWorldPoint(-8f, z = -8f),
            manifest
        )
        assertEquals(-1f, clamped.x)
        assertEquals(-.75f, clamped.z)
    }

    @Test
    fun safeSpawnManifestIsConvertedToWorldPoints() {
        val points = Digifarm3dMap.safeSpawns(manifest)
        assertEquals(2, points.size)
        assertEquals(-.5f, points.first().x)
        assertEquals(.25f, points.last().z)
    }

    @Test
    fun legacyViewportProjectionUsesRuntimeBounds() {
        val first = Digifarm3dMap.legacyToViewport(MapPoint(0f, 0f))
        val last = Digifarm3dMap.legacyToViewport(MapPoint(512f, 736f))
        assertEquals(0f, first.first)
        assertEquals(0f, first.second)
        assertEquals(1f, last.first)
        assertEquals(1f, last.second)
    }
}
