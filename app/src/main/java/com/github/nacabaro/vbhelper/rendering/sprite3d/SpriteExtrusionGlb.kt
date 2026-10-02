package com.github.nacabaro.vbhelper.rendering.sprite3d

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.math.max
import kotlin.math.pow

/** Turns the alpha outline of each pixel-art pose into a shallow 3D solid. */
internal object SpriteExtrusionGlb {
    private const val GENERATOR_VERSION = "sprite-extrusion-v5"
    private const val MAX_CACHED_MODEL_BYTES = 8 * 1024 * 1024
    private const val HALF_DEPTH = 0.045f
    private const val ALPHA_CUTOFF = 128
    private const val OUTLINE_SUPERSAMPLE = 2
    private const val OUTLINE_COLOR = 0xFF000000.toInt()

    private val modelCache = LinkedHashMap<String, ByteArray>(8, 0.75f, true)
    private var cachedModelBytes = 0

    internal fun normalizeJson(raw: String): String = raw.replace("\\/", "/")

    /** A centered, depth-tested effect plane; preserves source alpha and adds no body outline/shadow. */
    fun buildBillboard(frame: ResidentFrameImage): ByteArray {
        require(frame.width > 0 && frame.height > 0 && frame.argb.size == frame.width * frame.height)
        val glb = GlbData()
        val aspect = frame.width.toFloat() / frame.height
        val geometry = Geometry().apply {
            quad(
                floatArrayOf(-aspect / 2, -0.5f, 0f), floatArrayOf(aspect / 2, -0.5f, 0f),
                floatArrayOf(aspect / 2, 0.5f, 0f), floatArrayOf(-aspect / 2, 0.5f, 0f),
                floatArrayOf(0f, 1f), floatArrayOf(1f, 1f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0f)
            )
        }
        val imageView = glb.view(png(frame))
        val primitive = glb.primitive(geometry, 0)
        return glb.finish(JSONObject()
            .put("asset", JSONObject().put("version", "2.0"))
            .put("extensionsUsed", JSONArray().put("KHR_materials_unlit"))
            .put("scene", 0)
            .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            .put("nodes", JSONArray().put(JSONObject().put("name", "impact").put("mesh", 0)))
            .put("meshes", JSONArray().put(JSONObject().put("primitives", JSONArray().put(primitive))))
            .put("materials", JSONArray().put(JSONObject()
                .put("extensions", JSONObject().put("KHR_materials_unlit", JSONObject()))
                .put("doubleSided", true).put("alphaMode", "BLEND")
                .put("pbrMetallicRoughness", JSONObject()
                    .put("baseColorTexture", JSONObject().put("index", 0))
                    .put("baseColorFactor", JSONArray().put(1).put(1).put(1).put(1))
                    .put("metallicFactor", 0).put("roughnessFactor", 1))))
            .put("images", JSONArray().put(JSONObject().put("bufferView", imageView).put("mimeType", "image/png")))
            .put("textures", JSONArray().put(JSONObject().put("source", 0).put("sampler", 0)))
            .put("samplers", JSONArray().put(JSONObject().put("magFilter", 9728).put("minFilter", 9728)
                .put("wrapS", 33071).put("wrapT", 33071)))
            .put("bufferViews", glb.views).put("accessors", glb.accessors)
            .put("buffers", JSONArray().put(JSONObject().put("byteLength", glb.binarySize()))))
    }

    fun build(poses: Map<String, ResidentFrameImage>): ByteArray {
        val cacheKey = contentKey(poses)
        synchronized(modelCache) {
            modelCache[cacheKey]?.let { return it }
        }
        val firstUsable = poses.values.firstOrNull(::hasVisiblePixels)
            ?: ResidentFrameImage(intArrayOf(0xFFA78BFA.toInt()), 1, 1)
        val frames = poses.ifEmpty { mapOf("walk" to firstUsable) }
            .mapValues { (_, frame) -> outline(if (hasVisiblePixels(frame)) frame else firstUsable) }
        val glb = GlbData()
        val images = JSONArray()
        val textures = JSONArray()
        val materials = JSONArray()
        val meshes = JSONArray()
        val nodes = JSONArray()
        val children = JSONArray()
        nodes.put(JSONObject().put("name", "resident").put("children", children))

        fun addTexture(png: ByteArray): Int {
            val image = images.length()
            images.put(JSONObject().put("bufferView", glb.view(png)).put("mimeType", "image/png"))
            textures.put(JSONObject().put("source", image).put("sampler", 0))
            return textures.length() - 1
        }

        fun addMaterial(
            name: String,
            texture: Int,
            shade: Float,
            blend: Boolean = false,
            tint: FloatArray? = null,
        ): Int {
            val factor = tint?.let {
                JSONArray().put(it[0]).put(it[1]).put(it[2]).put(it[3])
            } ?: JSONArray().put(shade).put(shade).put(shade).put(1)
            val pbr = JSONObject()
                .put("baseColorTexture", JSONObject().put("index", texture))
                .put("baseColorFactor", factor)
                .put("metallicFactor", 0)
                .put("roughnessFactor", 1)
            materials.put(JSONObject()
                .put("name", name)
                .put("extensions", JSONObject().put("KHR_materials_unlit", JSONObject()))
                .put("doubleSided", true)
                .put("alphaMode", if (blend) "BLEND" else "MASK")
                .put("alphaCutoff", 0.5)
                .put("pbrMetallicRoughness", pbr))
            return materials.length() - 1
        }

        val meshesByFrame = LinkedHashMap<String, Int>()
        for ((pose, frame) in frames) {
            val meshIndex = meshesByFrame.getOrPut(frameContentKey(frame)) {
                val texture = addTexture(png(frame))
                val faceMaterial = addMaterial("${pose}_face", texture, 1f)
                val edgeMaterial = addMaterial("${pose}_edge", texture, 0.62f)
                val face = Geometry()
                val edge = Geometry()
                buildPoseGeometry(frame, face, edge)
                val primitives = JSONArray().put(glb.primitive(face, faceMaterial))
                if (edge.indices.isNotEmpty()) primitives.put(glb.primitive(edge, edgeMaterial))
                meshes.length().also { index ->
                    meshes.put(JSONObject().put("name", "${pose}_solid").put("primitives", primitives))
                }
            }
            children.put(nodes.length())
            nodes.put(JSONObject().put("name", "pose_$pose").put("mesh", meshIndex))
        }

        // Keep only a tight physical contact shadow. Broad colored ground halos
        // compete with the arena openings and make the sprites appear detached.
        val groundTexture = addTexture(shadowPng(maxAlpha = 220, edgePower = 1.15f))

        fun addShadow(
            name: String,
            halfWidth: Float,
            halfDepth: Float,
            height: Float,
            tint: FloatArray,
        ) {
            val material = addMaterial(name, groundTexture, 1f, blend = true, tint = tint)
            val shadow = Geometry().apply {
                quad(
                    floatArrayOf(-halfWidth, height, halfDepth),
                    floatArrayOf(halfWidth, height, halfDepth),
                    floatArrayOf(halfWidth, height, -halfDepth),
                    floatArrayOf(-halfWidth, height, -halfDepth),
                    floatArrayOf(0f, 1f), floatArrayOf(1f, 1f),
                    floatArrayOf(1f, 0f), floatArrayOf(0f, 0f),
                )
            }
            val mesh = meshes.length()
            meshes.put(JSONObject().put("name", name).put(
                "primitives", JSONArray().put(glb.primitive(shadow, material))
            ))
            children.put(nodes.length())
            nodes.put(JSONObject().put("name", name).put("mesh", mesh))
        }

        addShadow(
            name = "contact_shadow",
            halfWidth = 0.34f,
            halfDepth = 0.13f,
            height = 0.014f,
            tint = floatArrayOf(0f, 0f, 0f, 0.72f),
        )

        val document = JSONObject()
            .put("asset", JSONObject().put("version", "2.0"))
            .put("extensionsUsed", JSONArray().put("KHR_materials_unlit"))
            .put("scene", 0)
            .put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            .put("nodes", nodes)
            .put("meshes", meshes)
            .put("materials", materials)
            .put("images", images)
            .put("textures", textures)
            .put("samplers", JSONArray().put(JSONObject()
                .put("magFilter", 9728).put("minFilter", 9728)
                .put("wrapS", 33071).put("wrapT", 33071)))
            .put("bufferViews", glb.views)
            .put("accessors", glb.accessors)
            .put("buffers", JSONArray().put(JSONObject().put("byteLength", glb.binarySize())))
        val result = glb.finish(document)
        if (result.size <= MAX_CACHED_MODEL_BYTES) {
            synchronized(modelCache) {
                val previous = modelCache.put(cacheKey, result)
                cachedModelBytes += result.size - (previous?.size ?: 0)
                while (cachedModelBytes > MAX_CACHED_MODEL_BYTES && modelCache.isNotEmpty()) {
                    val eldestKey = modelCache.entries.first().key
                    cachedModelBytes -= modelCache.remove(eldestKey)?.size ?: 0
                }
            }
        }
        return result
    }

    private fun contentKey(poses: Map<String, ResidentFrameImage>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(GENERATOR_VERSION.toByteArray(Charsets.UTF_8))
        updateInt(digest, poses.size)
        poses.forEach { (name, frame) ->
            val nameBytes = name.toByteArray(Charsets.UTF_8)
            updateInt(digest, nameBytes.size)
            digest.update(nameBytes)
            updateInt(digest, frame.width)
            updateInt(digest, frame.height)
            val count = (frame.width.toLong() * frame.height).coerceIn(0L, frame.argb.size.toLong()).toInt()
            updateInt(digest, count)
            for (index in 0 until count) updateInt(digest, frame.argb[index])
        }
        return digest.digest().joinToString(separator = "") { it.toUByte().toString(16).padStart(2, '0') }
    }

    private fun updateInt(digest: MessageDigest, value: Int) {
        digest.update((value ushr 24).toByte())
        digest.update((value ushr 16).toByte())
        digest.update((value ushr 8).toByte())
        digest.update(value.toByte())
    }

    private fun frameContentKey(frame: ResidentFrameImage): String {
        val digest = MessageDigest.getInstance("SHA-256")
        updateInt(digest, frame.width)
        updateInt(digest, frame.height)
        frame.argb.forEach { updateInt(digest, it) }
        return digest.digest().joinToString(separator = "") {
            it.toUByte().toString(16).padStart(2, '0')
        }
    }

    private fun hasVisiblePixels(frame: ResidentFrameImage): Boolean =
        frame.width > 0 && frame.height > 0 &&
            frame.argb.size >= frame.width * frame.height &&
            frame.argb.take(frame.width * frame.height).any { (it ushr 24) >= ALPHA_CUTOFF }

    /** Adds a half-source-pixel black border by rasterizing the silhouette at 2x. */
    private fun outline(frame: ResidentFrameImage): ResidentFrameImage {
        val sourceWidth = frame.width
        val sourceHeight = frame.height
        val width = sourceWidth * OUTLINE_SUPERSAMPLE + 2
        val height = sourceHeight * OUTLINE_SUPERSAMPLE + 2
        val outlined = IntArray(width * height)
        for (y in 0 until sourceHeight) for (x in 0 until sourceWidth) {
            val color = frame.argb[y * sourceWidth + x]
            if ((color ushr 24) < ALPHA_CUTOFF) continue
            val outX = x * OUTLINE_SUPERSAMPLE + 1
            val outY = y * OUTLINE_SUPERSAMPLE + 1
            for (dy in -1..OUTLINE_SUPERSAMPLE) for (dx in -1..OUTLINE_SUPERSAMPLE) {
                val edgeIndex = (outY + dy) * width + outX + dx
                if (outlined[edgeIndex] == 0) outlined[edgeIndex] = OUTLINE_COLOR
            }
            for (dy in 0 until OUTLINE_SUPERSAMPLE) {
                for (dx in 0 until OUTLINE_SUPERSAMPLE) {
                    outlined[(outY + dy) * width + outX + dx] = color
                }
            }
        }
        return ResidentFrameImage(outlined, width, height)
    }

    private fun buildPoseGeometry(frame: ResidentFrameImage, face: Geometry, edge: Geometry) {
        val w = frame.width
        val h = frame.height
        val aspect = (w.toFloat() / h).coerceIn(0.5f, 1.8f)
        val left = -aspect / 2f
        val right = aspect / 2f
        face.quad(
            floatArrayOf(left, 0f, HALF_DEPTH), floatArrayOf(right, 0f, HALF_DEPTH),
            floatArrayOf(right, 1f, HALF_DEPTH), floatArrayOf(left, 1f, HALF_DEPTH),
            floatArrayOf(0f, 1f), floatArrayOf(1f, 1f),
            floatArrayOf(1f, 0f), floatArrayOf(0f, 0f),
        )
        face.quad(
            floatArrayOf(right, 0f, -HALF_DEPTH), floatArrayOf(left, 0f, -HALF_DEPTH),
            floatArrayOf(left, 1f, -HALF_DEPTH), floatArrayOf(right, 1f, -HALF_DEPTH),
            floatArrayOf(1f, 1f), floatArrayOf(0f, 1f),
            floatArrayOf(0f, 0f), floatArrayOf(1f, 0f),
        )
        fun solid(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h &&
            (frame.argb[y * w + x] ushr 24) >= ALPHA_CUTOFF

        for (y in 0 until h) for (x in 0 until w) {
            if (!solid(x, y)) continue
            val x0 = left + aspect * x / w
            val x1 = left + aspect * (x + 1) / w
            val y0 = 1f - (y + 1f) / h
            val y1 = 1f - y.toFloat() / h
            val uv = floatArrayOf((x + 0.5f) / w, (y + 0.5f) / h)
            if (!solid(x - 1, y)) edge.quad(
                floatArrayOf(x0, y0, HALF_DEPTH), floatArrayOf(x0, y1, HALF_DEPTH),
                floatArrayOf(x0, y1, -HALF_DEPTH), floatArrayOf(x0, y0, -HALF_DEPTH),
                uv, uv, uv, uv,
            )
            if (!solid(x + 1, y)) edge.quad(
                floatArrayOf(x1, y0, -HALF_DEPTH), floatArrayOf(x1, y1, -HALF_DEPTH),
                floatArrayOf(x1, y1, HALF_DEPTH), floatArrayOf(x1, y0, HALF_DEPTH),
                uv, uv, uv, uv,
            )
            if (!solid(x, y - 1)) edge.quad(
                floatArrayOf(x0, y1, -HALF_DEPTH), floatArrayOf(x1, y1, -HALF_DEPTH),
                floatArrayOf(x1, y1, HALF_DEPTH), floatArrayOf(x0, y1, HALF_DEPTH),
                uv, uv, uv, uv,
            )
            if (!solid(x, y + 1)) edge.quad(
                floatArrayOf(x0, y0, HALF_DEPTH), floatArrayOf(x1, y0, HALF_DEPTH),
                floatArrayOf(x1, y0, -HALF_DEPTH), floatArrayOf(x0, y0, -HALF_DEPTH),
                uv, uv, uv, uv,
            )
        }
    }

    private class Geometry {
        val positions = ArrayList<Float>()
        val uvs = ArrayList<Float>()
        val indices = ArrayList<Int>()

        fun quad(a: FloatArray, b: FloatArray, c: FloatArray, d: FloatArray,
                 ua: FloatArray, ub: FloatArray, uc: FloatArray, ud: FloatArray) {
            val base = positions.size / 3
            for (point in arrayOf(a, b, c, d)) for (value in point) positions.add(value)
            for (uv in arrayOf(ua, ub, uc, ud)) for (value in uv) uvs.add(value)
            indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
        }
    }

    private class GlbData {
        private val binary = ByteArrayOutputStream()
        val views = JSONArray()
        val accessors = JSONArray()

        fun binarySize(): Int = binary.size()

        fun view(bytes: ByteArray, target: Int? = null): Int {
            while (binary.size() % 4 != 0) binary.write(0)
            val result = views.length()
            val item = JSONObject().put("buffer", 0)
                .put("byteOffset", binary.size()).put("byteLength", bytes.size)
            if (target != null) item.put("target", target)
            views.put(item)
            binary.write(bytes)
            return result
        }

        private fun floats(values: List<Float>, type: String, size: Int, position: Boolean): Int {
            val bytes = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            values.forEach(bytes::putFloat)
            val accessor = JSONObject()
                .put("bufferView", view(bytes.array(), 34962))
                .put("componentType", 5126)
                .put("count", values.size / size)
                .put("type", type)
            if (position) {
                val mins = JSONArray()
                val maxs = JSONArray()
                for (axis in 0 until size) {
                    val coordinates = values.indices.asSequence()
                        .filter { it % size == axis }.map(values::get).toList()
                    mins.put(coordinates.minOrNull() ?: 0f)
                    maxs.put(coordinates.maxOrNull() ?: 0f)
                }
                accessor.put("min", mins).put("max", maxs)
            }
            accessors.put(accessor)
            return accessors.length() - 1
        }

        fun primitive(geometry: Geometry, material: Int): JSONObject {
            val positions = floats(geometry.positions, "VEC3", 3, position = true)
            val uvs = floats(geometry.uvs, "VEC2", 2, position = false)
            val bytes = ByteBuffer.allocate(geometry.indices.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            geometry.indices.forEach(bytes::putInt)
            val indices = accessors.length()
            accessors.put(JSONObject()
                .put("bufferView", view(bytes.array(), 34963))
                .put("componentType", 5125)
                .put("count", geometry.indices.size)
                .put("type", "SCALAR"))
            return JSONObject()
                .put("attributes", JSONObject().put("POSITION", positions).put("TEXCOORD_0", uvs))
                .put("indices", indices)
                .put("material", material)
        }

        fun finish(document: JSONObject): ByteArray {
            // Android's JSONObject escapes MIME slashes as "image\/png".
            // gltfio compares the raw MIME string and then fails to find its
            // PNG texture provider, leaving every alpha-masked pose invisible.
            val json = normalizeJson(document.toString()).toByteArray(Charsets.UTF_8)
            val jsonPadding = (4 - json.size % 4) % 4
            val payload = binary.toByteArray()
            val binaryPadding = (4 - payload.size % 4) % 4
            val total = 12 + 8 + json.size + jsonPadding + 8 + payload.size + binaryPadding
            val result = ByteArrayOutputStream(total)
            writeLittleInt(result, 0x46546C67)
            writeLittleInt(result, 2)
            writeLittleInt(result, total)
            writeLittleInt(result, json.size + jsonPadding)
            writeLittleInt(result, 0x4E4F534A)
            result.write(json)
            repeat(jsonPadding) { result.write(0x20) }
            writeLittleInt(result, payload.size + binaryPadding)
            writeLittleInt(result, 0x004E4942)
            result.write(payload)
            repeat(binaryPadding) { result.write(0) }
            return result.toByteArray()
        }
    }

    private fun png(frame: ResidentFrameImage): ByteArray {
        val pixels = ByteArrayOutputStream(frame.width * frame.height * 4 + frame.height)
        for (y in 0 until frame.height) {
            pixels.write(0)
            for (x in 0 until frame.width) {
                val argb = frame.argb[y * frame.width + x]
                pixels.write((argb ushr 16) and 255)
                pixels.write((argb ushr 8) and 255)
                pixels.write(argb and 255)
                pixels.write((argb ushr 24) and 255)
            }
        }
        return pngBytes(frame.width, frame.height, pixels.toByteArray())
    }

    private fun shadowPng(maxAlpha: Int, edgePower: Float): ByteArray {
        val pixels = ByteArrayOutputStream(32 * 32 * 4 + 32)
        for (y in 0 until 32) {
            pixels.write(0)
            for (x in 0 until 32) {
                val dx = (x - 15.5f) / 15.5f
                val dy = (y - 15.5f) / 15.5f
                val falloff = max(0f, 1f - dx * dx - dy * dy)
                val alpha = (maxAlpha * falloff.pow(edgePower)).toInt()
                pixels.write(255)
                pixels.write(255)
                pixels.write(255)
                pixels.write(alpha)
            }
        }
        return pngBytes(32, 32, pixels.toByteArray())
    }

    private fun pngBytes(width: Int, height: Int, pixels: ByteArray): ByteArray {
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed).use { it.write(pixels) }
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
        val header = ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(width)
            it.writeInt(height)
            it.writeByte(8)
            it.writeByte(6)
            it.writeByte(0)
            it.writeByte(0)
            it.writeByte(0)
        }
        pngChunk(png, "IHDR", header.toByteArray())
        pngChunk(png, "IDAT", compressed.toByteArray())
        pngChunk(png, "IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun pngChunk(output: ByteArrayOutputStream, name: String, bytes: ByteArray) {
        val type = name.toByteArray(Charsets.US_ASCII)
        DataOutputStream(output).writeInt(bytes.size)
        output.write(type)
        output.write(bytes)
        val crc = CRC32()
        crc.update(type)
        crc.update(bytes)
        DataOutputStream(output).writeInt(crc.value.toInt())
    }

    private fun writeLittleInt(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 255)
        output.write((value ushr 8) and 255)
        output.write((value ushr 16) and 255)
        output.write((value ushr 24) and 255)
    }
}
