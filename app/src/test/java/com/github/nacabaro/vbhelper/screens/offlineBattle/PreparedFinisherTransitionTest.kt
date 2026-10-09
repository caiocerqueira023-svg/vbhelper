package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

class PreparedFinisherTransitionTest {
    @Test fun transitionMaterialsSupportSmoothFadeInsteadOfAlphaCutoffPopping() {
        val model = buildFinisherTransitionGlb(mapOf("idle" to ResidentFrameImage(intArrayOf(0xFFFFFFFF.toInt()), 1, 1)))
        val materials = document(model).getJSONArray("materials")
        for (index in 0 until materials.length()) assertEquals("BLEND", materials.getJSONObject(index).getString("alphaMode"))
    }

    @Test
    fun silhouetteRetainsPoseNamesButHasNoRenderableGroundShadow() {
        val poses = mapOf(
            "idle" to ResidentFrameImage(intArrayOf(0, 0xFFFF0000.toInt(), 0, 0), 2, 2),
            "attack" to ResidentFrameImage(intArrayOf(0xFF0000FF.toInt(), 0, 0, 0), 2, 2),
        )
        val model = buildFinisherTransitionGlb(poses)
        val header = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, header.getInt(0))
        assertEquals(2, header.getInt(4))
        assertEquals(model.size, header.getInt(8))
        assertEquals(0x4E4F534A, header.getInt(16))
        val nodes = document(model).getJSONArray("nodes")
        val names = (0 until nodes.length()).map { nodes.getJSONObject(it).getString("name") }
        assertTrue(names.containsAll(listOf("pose_idle", "pose_attack")))
        for (index in 0 until nodes.length()) {
            val node = nodes.getJSONObject(index)
            if (node.optString("name") == "contact_shadow") assertFalse(node.has("mesh"))
        }
        assertEquals(0x004E4942, header.getInt(20 + header.getInt(12) + 4))
    }

    @Test
    fun silhouetteWhitensSourceColorsWithoutChangingAlphaOrMutatingSourcePixels() {
        val pixels = intArrayOf(0, 0xFFFF0000.toInt(), 0xC0405060.toInt(), 0)
        val before = pixels.copyOf()
        val poses = mapOf("idle" to ResidentFrameImage(pixels, 2, 2))
        val source = firstTexture(SpriteExtrusionGlb.build(poses))
        val transition = firstTexture(buildFinisherTransitionGlb(poses))
        assertEquals(source.width, transition.width)
        assertEquals(source.height, transition.height)
        var whitened = 0
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val original = source.getRGB(x, y)
            val silhouette = transition.getRGB(x, y)
            assertEquals(original ushr 24, silhouette ushr 24)
            if ((original ushr 24) != 0 && (original and 0x00FFFFFF) != 0) {
                assertEquals(0x00FFFFFF, silhouette and 0x00FFFFFF)
                whitened++
            }
        }
        assertTrue(whitened > 0)
        assertArrayEquals(before, pixels)
    }

    private fun document(model: ByteArray): JSONObject {
        val jsonLength = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        return JSONObject(String(model, 20, jsonLength, Charsets.UTF_8))
    }

    private fun firstTexture(model: ByteArray): java.awt.image.BufferedImage {
        val document = document(model)
        val viewIndex = document.getJSONArray("images").getJSONObject(0).getInt("bufferView")
        val view = document.getJSONArray("bufferViews").getJSONObject(viewIndex)
        val binaryOffset = 28 + ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN).getInt(12)
        return ImageIO.read(ByteArrayInputStream(model, binaryOffset + view.getInt("byteOffset"), view.getInt("byteLength")))
    }
}
