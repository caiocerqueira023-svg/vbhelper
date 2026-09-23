package com.github.nacabaro.vbhelper.screens.digifarmScreen

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class ResidentExtrusionGlbTest {
    @Test
    fun androidEscapedMimeTypeIsNormalizedForGltfio() {
        assertEquals("image/png", ResidentExtrusionGlb.normalizeJson("image\\/png"))
    }

    @Test
    fun opaquePixelHasFourDepthEdgesAndEmbeddedArt() {
        val pixels = IntArray(9)
        pixels[4] = 0xFFFF3048.toInt()
        val bytes = ResidentExtrusionGlb.build(
            mapOf("walk" to ResidentFrameImage(pixels, 3, 3))
        )
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, header.int)
        assertEquals(2, header.int)
        assertEquals(bytes.size, header.int)
        val jsonLength = header.int
        assertEquals(0x4E4F534A, header.int)
        val rawJson = String(bytes, 20, jsonLength, Charsets.UTF_8).trim()
        assertTrue(rawJson.contains("image/png"))
        assertTrue(!rawJson.contains("image\\/png"))
        val document = JSONObject(rawJson)
        val nodes = document.getJSONArray("nodes")
        assertEquals("pose_walk", nodes.getJSONObject(1).getString("name"))
        val mesh = document.getJSONArray("meshes").getJSONObject(0)
        val primitives = mesh.getJSONArray("primitives")
        assertEquals(2, primitives.length())
        val edgePosition = primitives.getJSONObject(1).getJSONObject("attributes").getInt("POSITION")
        val edgeAccessor = document.getJSONArray("accessors").getJSONObject(edgePosition)
        assertEquals(48, edgeAccessor.getInt("count"))
        assertEquals(-0.045, edgeAccessor.getJSONArray("min").getDouble(2), 0.00001)
        assertEquals(0.045, edgeAccessor.getJSONArray("max").getDouble(2), 0.00001)

        val binaryStart = 20 + jsonLength + 8
        val imageView = document.getJSONArray("images").getJSONObject(0).getInt("bufferView")
        val view = document.getJSONArray("bufferViews").getJSONObject(imageView)
        val offset = binaryStart + view.getInt("byteOffset")
        val image = ImageIO.read(ByteArrayInputStream(bytes, offset, view.getInt("byteLength")))
        assertEquals(5, image.width)
        assertEquals(5, image.height)
        assertEquals(0xFFFF3048.toInt(), image.getRGB(2, 2))
        assertEquals(0xFF4C3A82.toInt(), image.getRGB(1, 1))
        assertTrue((image.getRGB(0, 0) ushr 24) == 0)
    }
}
