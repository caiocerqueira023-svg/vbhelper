package com.tamerslab.digiline.freechat.content

import android.content.res.AssetManager
import com.tamerslab.BuildConfig
import com.tamerslab.digiline.freechat.model.FreeChatContent
import com.tamerslab.digiline.freechat.model.FreeChatDatabasePackDto
import com.tamerslab.digiline.freechat.model.FreeChatManifestDto
import com.tamerslab.digiline.freechat.model.FreeChatPackDto
import com.tamerslab.digiline.freechat.model.SpeciesVoicePackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Shared lenient JSON configuration for the Free Talk content packs and the persisted
 * `ChatMessageEntity.payloadJson` rows (contract §1/§3).
 *
 * Unknown keys are ignored and JSON syntax is read leniently so generated packs can gain fields
 * without breaking older builds; hard validation lives in [FreeChatContentValidator].
 */
val FreeChatJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Manifest-driven Free Talk content loader (contract §3).
 *
 * The loader itself has no Android dependency: it reads every file through [readAsset], so JVM unit
 * tests can hand it a map-backed lambda. [fromAssets] is the only Android-aware entry point and is
 * kept in the companion so the constructor stays testable.
 *
 * Behaviour:
 * - reads `manifest.json`, then the files listed under its `intents`/`speciesVoice` keys (defaults
 *   `free_chat.json`/`species_voice.json`) with [FreeChatJson];
 * - builds [FreeChatContent] (`speciesNames` = the voices' `speciesName` values, `contentVersion`
 *   from the manifest);
 * - in debug builds runs [FreeChatContentValidator] and throws when the pack has hard errors.
 *
 * Failure behaviour: unreadable assets, unsafe paths, unsupported schema versions and malformed
 * JSON throw [IllegalStateException] whose message starts with the offending file path.
 */
class FreeChatContentLoader(
    private val readAsset: (String) -> String,
) : FreeChatContentSource {

    override suspend fun load(root: String): FreeChatContent = withContext(Dispatchers.IO) {
        val assetRoot = sanitizeRoot(root)
        val manifest = decode("$assetRoot/${FreeChatContentFiles.MANIFEST}", FreeChatManifestDto.serializer())
        checkManifest(manifest)
        val pack = decode(
            assetPath(assetRoot, manifest, FreeChatContentFiles.INTENTS, FreeChatContentFiles.DEFAULT_INTENTS_FILE),
            FreeChatPackDto.serializer(),
        )
        val voicePack = decode(
            assetPath(
                assetRoot,
                manifest,
                FreeChatContentFiles.SPECIES_VOICE,
                FreeChatContentFiles.DEFAULT_SPECIES_VOICE_FILE,
            ),
            SpeciesVoicePackDto.serializer(),
        )
        val content = FreeChatContent(
            manifest = manifest,
            pack = pack,
            speciesVoices = voicePack.voices
                .filter { it.speciesKey.isNotBlank() }
                .associateBy { it.speciesKey },
            speciesNames = voicePack.voices
                .map { it.speciesName }
                .filter { it.isNotBlank() }
                .distinct(),
            contentVersion = manifest.contentVersion.takeIf { it > 0 } ?: pack.contentVersion,
            locale = manifest.locale.takeIf { it.isNotBlank() } ?: pack.locale,
            databaseFallbacks = loadDatabaseFallbacks(assetRoot, manifest),
        )
        validateInDebug(content)
        content
    }

    /**
     * Optional external database fallback layer (owner requirement): if the manifest lists a
     * `databaseFallback` file it is loaded and shipped as the last-resort answer source; if the key
     * is absent the pack simply has no database layer (generated content only).
     */
    private fun loadDatabaseFallbacks(
        assetRoot: String,
        manifest: FreeChatManifestDto,
    ): List<com.tamerslab.digiline.freechat.model.FreeChatDatabasePairDto> {
        val file = manifest.files[FreeChatContentFiles.DATABASE_FALLBACK]?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        if (file.startsWith('/') || file.contains("..") || file.contains('\\')) {
            throw IllegalStateException(
                "${FreeChatContentFiles.MANIFEST}: file " +
                    "'${FreeChatContentFiles.DATABASE_FALLBACK}' has an unsafe path '$file'"
            )
        }
        val path = if (file.startsWith("$assetRoot/")) file else "$assetRoot/$file"
        val databasePack = decode(path, FreeChatDatabasePackDto.serializer())
        return databasePack.pairs.filter { it.user.isNotBlank() && it.response.isNotBlank() }
    }

    private fun sanitizeRoot(assetRoot: String): String {
        val root = assetRoot.trim().trim('/')
        if (root.isBlank()) {
            throw IllegalStateException("Free Talk asset root must not be blank")
        }
        if (root.contains("..") || root.contains('\\') || root.startsWith('/')) {
            throw IllegalStateException("Free Talk asset root '$assetRoot' is not a safe relative path")
        }
        return root
    }

    private fun assetPath(
        root: String,
        manifest: FreeChatManifestDto,
        key: String,
        fallbackFile: String,
    ): String {
        val file = manifest.files[key]?.takeIf { it.isNotBlank() } ?: fallbackFile
        if (file.startsWith('/') || file.contains("..") || file.contains('\\')) {
            throw IllegalStateException(
                "${FreeChatContentFiles.MANIFEST}: file '$key' has an unsafe path '$file'"
            )
        }
        // The shipped manifest lists file names relative to the locale directory; a pack that lists
        // them relative to the assets root is also accepted.
        return if (file.startsWith("$root/")) file else "$root/$file"
    }

    private fun checkManifest(manifest: FreeChatManifestDto) {
        if (manifest.schemaVersion !in 1..SUPPORTED_SCHEMA_VERSION) {
            throw IllegalStateException(
                "${FreeChatContentFiles.MANIFEST}: schemaVersion ${manifest.schemaVersion} is " +
                    "unsupported (this build supports 1..$SUPPORTED_SCHEMA_VERSION)"
            )
        }
    }

    private fun validateInDebug(content: FreeChatContent) {
        if (!BuildConfig.DEBUG) return
        val errors = FreeChatContentValidator().validate(content)
        if (errors.isEmpty()) return
        val preview = errors.take(MAX_REPORTED_ERRORS).joinToString("; ")
        throw IllegalStateException(
            "${FreeChatContentFiles.MANIFEST}: Free Talk content validation failed " +
                "(${errors.size} error(s)): $preview"
        )
    }

    private fun <T> decode(path: String, deserializer: DeserializationStrategy<T>): T {
        val text = try {
            readAsset(path)
        } catch (failure: Exception) {
            throw IllegalStateException("$path: unable to read asset (${failure.message})", failure)
        }
        return try {
            FreeChatJson.decodeFromString(deserializer, text)
        } catch (failure: SerializationException) {
            throw IllegalStateException("$path: malformed JSON (${failure.message})", failure)
        } catch (failure: IllegalArgumentException) {
            throw IllegalStateException("$path: malformed JSON (${failure.message})", failure)
        }
    }

    companion object {
        /** Highest `schemaVersion` this loader understands; newer packs are rejected loudly. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        private const val MAX_REPORTED_ERRORS = 12

        /**
         * Android factory: the loader opens the pack from the packaged assets
         * (`load()` uses [FreeChatContentSource.DEFAULT_ASSET_ROOT]). Only this companion function
         * touches [AssetManager], so the loader stays JVM-testable.
         */
        fun fromAssets(assets: AssetManager): FreeChatContentLoader =
            FreeChatContentLoader(readAsset = { path ->
                assets.open(path).bufferedReader().use { it.readText() }
            })
    }
}
