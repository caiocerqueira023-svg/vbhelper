package com.github.nacabaro.vbhelper.battle.offline

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.min

class OfflineArenaGeometryTest {
    @Test fun cameraAndPlayableDiscFitInsideThePackagedArena() {
        val assets = File("src/main/assets")
        val manifest = JSONObject(File(assets, "Arena/Colosseum/arena.json").readText())
        val bytes = File(assets, manifest.getString("assetPath")).readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, buffer.getInt(0))
        assertEquals(bytes.size, buffer.getInt(8))
        val json = JSONObject(String(bytes, 20, buffer.getInt(12), Charsets.UTF_8))
        val nodes = json.getJSONArray("nodes")
        val sphereNode = (0 until nodes.length()).map(nodes::getJSONObject).first { it.getString("name") == "Sphere001" }
        val primitive = json.getJSONArray("meshes").getJSONObject(sphereNode.getInt("mesh"))
            .getJSONArray("primitives").getJSONObject(0)
        val bounds = json.getJSONArray("accessors").getJSONObject(primitive.getJSONObject("attributes").getInt("POSITION"))
        val scale = manifest.getDouble("visualScale")
        val radius = (0..2).minOf { min(abs(bounds.getJSONArray("min").getDouble(it)), abs(bounds.getJSONArray("max").getDouble(it))) } * scale
        val camera = manifest.getJSONObject("camera")
        assertTrue("Camera must remain INSIDE the textured dome at every zoom", camera.getDouble("maxDistance") + abs(camera.getDouble("targetY")) < radius)
        assertTrue(camera.getDouble("distance") in camera.getDouble("minDistance")..camera.getDouble("maxDistance"))
        assertEquals("Allow a slightly closer maximum zoom", 8.5, camera.getDouble("minDistance"), 0.01)
        assertEquals("Simulation, projection and fighter motion share world units", 1.0, manifest.getDouble("positionScale"), 0.0)
        // Measured central disc: first raised trim starts at radius 11.8936 in the source DAE.
        assertTrue(manifest.getDouble("playableRadius") * manifest.getDouble("positionScale") < 11.8936 * scale)
        // The first raised seating vertices begin near 23.06 source units; keep the camera inside them.
        assertTrue(manifest.getDouble("cameraCollisionRadius") < 23.06 * scale)
        assertTrue(manifest.getDouble("cameraCollisionRadius") > manifest.getDouble("playableRadius"))
        assertTrue(manifest.getDouble("lineupRowSpacing") > manifest.getDouble("fighterScale"))
    }
}
