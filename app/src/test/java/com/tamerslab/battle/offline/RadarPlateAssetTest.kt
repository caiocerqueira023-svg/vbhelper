package com.tamerslab.battle.offline

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The Radar place uses a derived plate (authored radar arena minus the battle boundary, sky dome and
 * floating voxel props, with an enlarged theme floor). This keeps that derivation honest.
 */
class RadarPlateAssetTest {
    @Test
    fun `derived radar plate keeps floor and grid, has no battle props, and its floor never ends`() {
        val assets = File("src/main/assets")
        val manifest = JSONObject(File(assets, "Arena/Radar/plate.json").readText())
        assertEquals("Arena/Radar/radar_plate.glb", manifest.getString("assetPath"))
        assertTrue(manifest.getDouble("cameraCollisionRadius") > manifest.getDouble("playableRadius"))

        val bytes = File(assets, manifest.getString("assetPath")).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buffer.getInt(0))
        assertEquals(2, buffer.getInt(4))
        assertEquals(bytes.size, buffer.getInt(8))
        val json = JSONObject(String(bytes, 20, buffer.getInt(12), Charsets.UTF_8))

        val meshes = json.getJSONArray("meshes")
        val arena = meshes.getJSONObject(0)
        val primitiveNames = (0 until arena.getJSONArray("primitives").length())
            .map { arena.getJSONArray("primitives").getJSONObject(it).getString("name") }
            .toSet()
        assertEquals(setOf("radar_floor", "world_grid"), primitiveNames)

        // The authored battle arena's props must not come back through the derived asset.
        val nodes = json.getJSONArray("nodes")
        val nodeNames = (0 until nodes.length()).map { nodes.getJSONObject(it).optString("name") }
        assertFalse(nodeNames.any { it.startsWith("RadarVoxel") })
        assertFalse("Sphere001" in nodeNames)

        // The floor is a plain themed quad; it is enlarged far beyond the authored 400x400 arena so
        // the ground continues past the grid at the lowest camera angle and widest zoom.
        val floorAccessor = json.getJSONArray("accessors").getJSONObject(
            arena.getJSONArray("primitives").getJSONObject(0)
                .getJSONObject("attributes").getInt("POSITION")
        )
        val floorMax = floorAccessor.getJSONArray("max")
        val floorMin = floorAccessor.getJSONArray("min")
        assertTrue(floorMax.getDouble(0) >= 2_000.0)
        assertTrue(floorMin.getDouble(0) <= -2_000.0)
    }
}
