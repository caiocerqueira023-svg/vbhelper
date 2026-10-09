package com.github.nacabaro.vbhelper.screens.offlineBattle

import com.github.nacabaro.vbhelper.rendering.sprite3d.ResidentFrameImage
import com.github.nacabaro.vbhelper.rendering.sprite3d.SpriteExtrusionGlb
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Setup-time silhouette data using the original pose alpha, with the usual masked sprite outline.
 * Pose names/topology are retained. The silhouette has no renderable contact shadow, so a retained
 * transition instance cannot leave a phantom shadow when the renderer hides/scales its pose nodes.
 */
internal fun buildFinisherTransitionGlb(poses: Map<String, ResidentFrameImage>): ByteArray {
    val silhouettes = poses.mapValues { (_, frame) ->
        ResidentFrameImage(
            IntArray(frame.argb.size) { index ->
                val alpha = frame.argb[index] and 0xFF000000.toInt()
                if (alpha == 0) 0 else alpha or 0x00FFFFFF
            },
            frame.width,
            frame.height,
        )
    }
    val model = SpriteExtrusionGlb.build(silhouettes)
    val header = ByteBuffer.wrap(model).order(ByteOrder.LITTLE_ENDIAN)
    val jsonLength = header.getInt(12)
    val document = JSONObject(String(model, 20, jsonLength, Charsets.UTF_8))
    val materials = document.getJSONArray("materials")
    for (index in 0 until materials.length()) {
        materials.getJSONObject(index).put("alphaMode", "BLEND").remove("alphaCutoff")
    }
    val nodes = document.getJSONArray("nodes")
    for (index in 0 until nodes.length()) {
        val node = nodes.getJSONObject(index)
        if (node.optString("name") == "contact_shadow") node.remove("mesh")
    }
    // Keep the generated binary chunk and its buffer offsets intact; replace only the JSON chunk.
    val json = SpriteExtrusionGlb.normalizeJson(document.toString()).toByteArray(Charsets.UTF_8)
    val paddedJsonLength = (json.size + 3) / 4 * 4
    val binaryChunkOffset = 20 + jsonLength
    val totalLength = 20 + paddedJsonLength + model.size - binaryChunkOffset
    return ByteBuffer.allocate(totalLength).order(ByteOrder.LITTLE_ENDIAN).apply {
        putInt(0x46546C67)
        putInt(2)
        putInt(totalLength)
        putInt(paddedJsonLength)
        putInt(0x4E4F534A)
        put(json)
        repeat(paddedJsonLength - json.size) { put(0x20.toByte()) }
        put(model, binaryChunkOffset, model.size - binaryChunkOffset)
    }.array()
}
