package com.github.nacabaro.vbhelper.rendering.sprite3d

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class SpriteExtrusionGlbTest {
    @Test fun impactBillboardKeepsOriginalPixelsAndTransparencyWithoutAContactShadow() {
        val bytes = SpriteExtrusionGlb.buildBillboard(ResidentFrameImage(intArrayOf(0, 0x80FF3048.toInt()), 2, 1))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        header.position(12)
        val jsonLength = header.int
        val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8).trim())
        assertEquals(1, document.getJSONArray("meshes").length())
        assertEquals(1, document.getJSONArray("materials").length())
        assertEquals("BLEND", document.getJSONArray("materials").getJSONObject(0).getString("alphaMode"))
        val positionIndex = document.getJSONArray("meshes").getJSONObject(0).getJSONArray("primitives")
            .getJSONObject(0).getJSONObject("attributes").getInt("POSITION")
        val position = document.getJSONArray("accessors").getJSONObject(positionIndex)
        assertEquals(0.0, position.getJSONArray("min").getDouble(2), 0.0)
        assertEquals(0.0, position.getJSONArray("max").getDouble(2), 0.0)
        val imageView = document.getJSONArray("images").getJSONObject(0).getInt("bufferView")
        val view = document.getJSONArray("bufferViews").getJSONObject(imageView)
        val image = ImageIO.read(ByteArrayInputStream(bytes, 20 + jsonLength + 8 + view.getInt("byteOffset"), view.getInt("byteLength")))
        assertEquals(2, image.width)
        assertEquals(1, image.height)
        assertEquals(0x80FF3048.toInt(), image.getRGB(1, 0))
        assertEquals(0, image.getRGB(0, 0))
    }

    @Test
    fun androidEscapedMimeTypeIsNormalizedForGltfio() {
        assertEquals("image/png", SpriteExtrusionGlb.normalizeJson("image\\/png"))
    }

    @Test
    fun opaquePixelHasSupersampledDepthEdgesAndEmbeddedArt() {
        val pixels = IntArray(9)
        pixels[4] = 0xFFFF3048.toInt()
        val bytes = SpriteExtrusionGlb.build(
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
        assertEquals(64, edgeAccessor.getInt("count"))
        assertEquals(-0.045, edgeAccessor.getJSONArray("min").getDouble(2), 0.00001)
        assertEquals(0.045, edgeAccessor.getJSONArray("max").getDouble(2), 0.00001)

        val binaryStart = 20 + jsonLength + 8
        val imageView = document.getJSONArray("images").getJSONObject(0).getInt("bufferView")
        val view = document.getJSONArray("bufferViews").getJSONObject(imageView)
        val offset = binaryStart + view.getInt("byteOffset")
        val image = ImageIO.read(ByteArrayInputStream(bytes, offset, view.getInt("byteLength")))
        assertEquals(8, image.width)
        assertEquals(8, image.height)
        assertEquals(0xFFFF3048.toInt(), image.getRGB(3, 3))
        assertEquals(0xFF000000.toInt(), image.getRGB(2, 2))
        assertTrue((image.getRGB(0, 0) ushr 24) == 0)
    }

    @Test
    fun generatedModelUsesOnlyTheTightContactShadow() {
        val bytes = SpriteExtrusionGlb.build(
            mapOf("idle" to ResidentFrameImage(intArrayOf(0xFFFFFFFF.toInt()), 1, 1))
        )
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        header.position(12)
        val jsonLength = header.int
        header.int
        val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8).trim())
        val nodes = document.getJSONArray("nodes")
        val namedNodes = (0 until nodes.length()).associateBy(
            keySelector = { nodes.getJSONObject(it).getString("name") },
            valueTransform = { nodes.getJSONObject(it) },
        )

        assertTrue(!namedNodes.containsKey("soft_shadow"))
        assertTrue(namedNodes.containsKey("contact_shadow"))
        assertEquals(2, document.getJSONArray("images").length())
        assertEquals(2, document.getJSONArray("textures").length())
    }

    @Test
    fun identicalPoseImagesReuseOneMeshAndTexture() {
        val frame = ResidentFrameImage(
            intArrayOf(0x00000000, 0xFFFFFFFF.toInt(), 0xFF21D4E8.toInt(), 0x00000000),
            2,
            2,
        )
        val bytes = SpriteExtrusionGlb.build(mapOf("idle" to frame, "happy" to frame.copy()))
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        header.position(12)
        val jsonLength = header.int
        header.int
        val document = JSONObject(String(bytes, 20, jsonLength, Charsets.UTF_8).trim())
        val nodes = document.getJSONArray("nodes")

        assertEquals(nodes.getJSONObject(1).getInt("mesh"), nodes.getJSONObject(2).getInt("mesh"))
        assertEquals(2, document.getJSONArray("images").length())
    }

    @Test
    fun identicalSpriteContentReusesTheExtrudedModelButPixelChangesDoNot() {
        val pixels = intArrayOf(0x00000000, 0xFF21D4E8.toInt(), 0x00000000, 0xFF814BFF.toInt())
        val first = SpriteExtrusionGlb.build(mapOf("cache-check" to ResidentFrameImage(pixels, 2, 2)))
        val identical = SpriteExtrusionGlb.build(mapOf("cache-check" to ResidentFrameImage(pixels.copyOf(), 2, 2)))
        val changedPixels = pixels.copyOf().apply { this[1] = 0xFFFF3048.toInt() }
        val changed = SpriteExtrusionGlb.build(mapOf("cache-check" to ResidentFrameImage(changedPixels, 2, 2)))

        assertSame(first, identical)
        assertNotSame(first, changed)
    }
}
