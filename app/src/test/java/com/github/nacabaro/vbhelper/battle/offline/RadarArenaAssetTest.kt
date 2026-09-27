package com.github.nacabaro.vbhelper.battle.offline

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class RadarArenaAssetTest {
    @Test
    fun `packaged radar arena contains grid floor and circular battle boundary`() {
        val assets = File("src/main/assets")
        val manifest = JSONObject(File(assets, "Arena/Radar/arena.json").readText())
        val bytes = File(assets, manifest.getString("assetPath")).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(0x46546C67, buffer.getInt(0))
        assertEquals(2, buffer.getInt(4))
        assertEquals(bytes.size, buffer.getInt(8))
        val json = JSONObject(String(bytes, 20, buffer.getInt(12), Charsets.UTF_8))
        val materials = json.getJSONArray("materials")
        val materialNames = (0 until materials.length())
            .map { materials.getJSONObject(it).getString("name") }

        assertTrue("world_grid" in materialNames)
        assertTrue("battle_boundary" in materialNames)
        assertEquals(8.0, manifest.getDouble("playableRadius"), 0.0)
        assertTrue(manifest.getDouble("cameraCollisionRadius") > manifest.getDouble("playableRadius"))
        val floorPrimitive = json.getJSONArray("meshes").getJSONObject(0)
            .getJSONArray("primitives").getJSONObject(0)
        val floorBounds = json.getJSONArray("accessors").getJSONObject(
            floorPrimitive.getJSONObject("attributes").getInt("POSITION")
        )
        assertTrue(
            floorBounds.getJSONArray("max").getDouble(0) > manifest.getDouble("cameraCollisionRadius")
        )
    }
}
