package com.tamerslab.species

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.room.withTransaction
import com.tamerslab.data.database.AppDatabase
import com.tamerslab.domain.characters.Sprite
import com.tamerslab.utils.BitmapData
import com.tamerslab.utils.createARGBIntArray
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Offline source library. Browsing decodes only visible portraits; selection installs one set. */
class BundledDotSpriteLibrary(private val context: Context, private val db: AppDatabase) {
    /**
     * VB battle frames shipped in the APK, reachable for un-imported species through the canonical
     * card-slot binding. Shared by every artwork consumer so the asset index is read once per process.
     */
    val bundledVbSprites: BundledVbSprites by lazy {
        BundledVbSprites(context.applicationContext) { catalogBlocking() }
    }
    private val catalogLock = Any()
    private val packMutex = Mutex()
    private val portraitZipMutex = Mutex()
    private var portraitZipPack: File? = null
    private var portraitZip: ZipFile? = null
    private var cachedCatalog: CanonicalSpeciesCatalog? = null
    private var cachedPack: File? = null
    private val portraits = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val installedSprites = object : LruCache<String, Sprite>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Sprite) = (1..12).sumOf { value.displayFrame(it)?.size ?: 0 }
    }

    suspend fun catalog(): CanonicalSpeciesCatalog = withContext(Dispatchers.IO) {
        catalogBlocking()
    }

    /** Local-only read for synchronous Room producers such as NFC import and quest evidence. Call on IO. */
    fun catalogBlocking(): CanonicalSpeciesCatalog = synchronized(catalogLock) {
            cachedCatalog ?: context.assets.open("species_catalog.json").bufferedReader().use {
                CanonicalSpeciesCatalog(Gson().fromJson(it, CanonicalSpeciesDatabaseDto::class.java))
            }.also { cachedCatalog = it }
    }

    suspend fun portrait(setId: String): Bitmap = withContext(Dispatchers.IO) {
        portraits[setId]?.let { return@withContext it }
        val catalog = catalog()
        val set = requireNotNull(catalog.setsById[setId])
        // A fresh ZipFile parses the pack's ~27k-entry central directory; keep one open for portraits
        // (they load one at a time in pickers/lists), guarded because ZipFile streams are not shared.
        val bytes = portraitZipMutex.withLock {
            val pack = pack(catalog)
            val zip = portraitZip?.takeIf { portraitZipPack == pack } ?: run {
                portraitZip?.close()
                ZipFile(pack).also { portraitZip = it; portraitZipPack = pack }
            }
            readFrame(zip, set, DotFrameRoles.path(set, 1))
        }
        Bitmap.createBitmap(BitmapData(bytes, set.width, set.height).createARGBIntArray(),
            set.width, set.height, Bitmap.Config.ARGB_8888).also { portraits.put(setId, it) }
    }

    /** The negative, content-addressed ID is stable across restarts and never replaces imported art. */
    suspend fun install(setId: String): Int {
        installAll(listOf(setId))
        return requireNotNull(catalog().setsById[setId]).spriteId
    }

    /** Immutable Dot frames are shared across animated portraits instead of rereading twelve BLOBs per tick. */
    suspend fun sprite(setId: String): Sprite = withContext(Dispatchers.IO) {
        installedSprites[setId]?.let { return@withContext it }
        val set = requireNotNull(catalog().setsById[setId])
        val existing = db.spriteDao().getById(set.spriteId)
        if (existing != null) existing.also { installedSprites.put(setId, it) }
        else {
            installAll(listOf(setId))
            requireNotNull(installedSprites[setId])
        }
    }

    /** One archive open and bounded batches, rather than one archive/Room transaction per species. */
    suspend fun installAll(setIds: Collection<String>) = withContext(Dispatchers.IO) {
        if (setIds.isEmpty()) return@withContext
        val catalog = catalog()
        val pack = pack(catalog)
        val buffers = object : LruCache<String, ByteArray>(4 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
        ZipFile(pack).use { zip ->
            setIds.distinct().map { requireNotNull(catalog.setsById[it]) }.chunked(32).forEach { batch ->
                val sprites = batch.map { set ->
                    fun frame(pose: Int): ByteArray {
                        val path = DotFrameRoles.path(set, pose)
                        return buffers[path]?.also { require(it.size == set.width * set.height * 2) }
                            ?: readFrame(zip, set, path).also { buffers.put(path, it) }
                    }
                    Sprite(set.spriteId, frame(1), frame(2), frame(3), frame(4), frame(5), frame(6),
                        frame(7), frame(8), frame(9), frame(10), frame(11), frame(12), set.width, set.height)
                }
                db.withTransaction {
                    val previous = db.spriteDao().getByIds(sprites.map { it.id }).associateBy { it.id }
                    sprites.forEach { require(previous[it.id] == null || previous[it.id] == it) { "Artwork identity collision" } }
                    db.spriteDao().insertBundledSprites(sprites.filter { it.id !in previous })
                }
                batch.zip(sprites).forEach { (set, sprite) -> installedSprites.put(set.id, sprite) }
            }
        }
    }

    private suspend fun pack(catalog: CanonicalSpeciesCatalog): File = packMutex.withLock {
        val data = catalog.data
        require(data.packBytes in 1..(64L * 1024 * 1024) && SHA256.matches(data.packSha256))
        cachedPack?.takeIf { it.isFile && it.length() == data.packBytes }?.let { return@withLock it }
        // The cache file name already carries the checksum, so a matching length is enough on the hot
        // path: re-hashing 12 MB on every process start only delays the first portrait.
        val target = File(context.cacheDir, "bundled-dot-${data.packSha256}.zip")
        if (!target.isFile || target.length() != data.packBytes) {
            val temporary = File.createTempFile("bundled-dot-", ".part", context.cacheDir)
            try {
                context.assets.open("dot_sprites.zip").use { input -> temporary.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var count = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        count += read
                        require(count <= data.packBytes) { "Artwork pack exceeds its manifest size" }
                        output.write(buffer, 0, read)
                    }
                } }
                require(temporary.length() == data.packBytes && hash(temporary) == data.packSha256) { "Artwork pack checksum mismatch" }
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally { temporary.delete() }
        }
        target.also { cachedPack = it }
    }

    private fun readFrame(zip: ZipFile, set: DotSpriteSetDto, path: String): ByteArray {
        require(CanonicalSpeciesCatalog.FRAME_PATH.matches(path))
        val expected = set.width * set.height * 2
        val entry = requireNotNull(zip.getEntry(path)) { "Bundled frame missing" }
        require(entry.size == expected.toLong()) { "Bundled frame dimensions disagree" }
        val bytes = ByteArray(expected)
        zip.getInputStream(entry).use { input ->
            var offset = 0
            while (offset < expected) {
                val count = input.read(bytes, offset, expected - offset)
                require(count > 0) { "Bundled frame truncated" }
                offset += count
            }
            require(input.read() == -1)
        }
        require("frames/${digest(bytes)}.bin" == path) { "Bundled frame checksum mismatch" }
        return bytes
    }

    private fun hash(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hex(hash.digest())
    }

    private fun digest(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        return String(CharArray(bytes.size * 2) { index ->
            val value = bytes[index / 2].toInt() and 255
            digits[if (index % 2 == 0) value ushr 4 else value and 15]
        })
    }
    private companion object { val SHA256 = Regex("[0-9a-f]{64}") }
}
