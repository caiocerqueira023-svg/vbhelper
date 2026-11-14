package com.tamerslab.digiline.content

import android.content.res.AssetManager
import com.tamerslab.digiline.model.ContentManifest
import com.tamerslab.digiline.model.EventLinePackDto
import com.tamerslab.digiline.model.LorePackDto
import com.tamerslab.digiline.model.NegotiationPackDto
import com.tamerslab.digiline.model.PersuasionPackDto
import com.tamerslab.digiline.model.QuestionPackDto
import com.tamerslab.digiline.model.QuizPackDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Shared lenient JSON configuration for the DigiLine packs and the payload strings persisted on
 * [com.tamerslab.domain.chat.ChatMessageEntity] (`payloadJson`) and the DigiLine tables
 * (`shiftJson`, `demandsJson`, `topicsDoneJson`, `effectsJson`).
 */
val DigilineJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Manifest-driven DigiLine content loader (contract §1/§4).
 *
 * The loader itself has no Android dependency: it reads every file through [reader], so JVM unit
 * tests can hand it a map-backed lambda. [fromAssets] is the only Android-aware entry point and
 * is kept in the companion so the constructor stays testable.
 *
 * Failure behaviour: malformed JSON, unsafe paths, unsupported manifest versions and missing
 * required pack files throw [IllegalStateException] whose message names the offending file.
 */
class DigilineContentLoader(
    private val reader: (String) -> String,
    private val json: Json = DigilineJson,
) : DigilineContentSource {

    override suspend fun load(assetRoot: String): DigilineContentPack = withContext(Dispatchers.IO) {
        val root = sanitizeRoot(assetRoot)
        val manifest = decode<ContentManifest>("$root/${DigilineContentFiles.MANIFEST}")
        checkManifest(manifest)
        val directory = "$root/${manifest.locale}"
        DigilineContentPack(
            manifest,
            decode<QuestionPackDto>(assetPath(directory, manifest, DigilineContentFiles.SPECIES)).questions,
            decode<QuestionPackDto>(assetPath(directory, manifest, DigilineContentFiles.GENERIC)).questions,
            decode<QuizPackDto>(assetPath(directory, manifest, DigilineContentFiles.QUIZ)).questions,
            decode<NegotiationPackDto>(assetPath(directory, manifest, DigilineContentFiles.NEGOTIATION)),
            decode<PersuasionPackDto>(assetPath(directory, manifest, DigilineContentFiles.PERSUASION)),
            decode<EventLinePackDto>(assetPath(directory, manifest, DigilineContentFiles.EVENTS)).lines,
            decode<LorePackDto>(assetPath(directory, manifest, DigilineContentFiles.LORE)).facts,
        )
    }

    /** Zero-argument overload for callers holding the concrete loader (the interface supplies a default). */
    suspend fun load(): DigilineContentPack = load(DigilineContentSource.DEFAULT_ASSET_ROOT)

    private fun sanitizeRoot(assetRoot: String): String {
        val root = assetRoot.trim().trim('/')
        if (root.isBlank()) {
            throw IllegalStateException("DigiLine asset root must not be blank")
        }
        if (root.contains("..") || root.contains('\\') || root.startsWith('/')) {
            throw IllegalStateException("DigiLine asset root '$assetRoot' is not a safe relative path")
        }
        return root
    }

    private fun assetPath(directory: String, manifest: ContentManifest, key: String): String {
        val file = manifest.files[key]
            ?: throw IllegalStateException(
                "manifest.json does not list the required file key '$key'"
            )
        if (file.isBlank() || file.startsWith('/') || file.contains("..") || file.contains('\\')) {
            throw IllegalStateException("manifest.json file '$key' has an unsafe path '$file'")
        }
        return "$directory/$file"
    }

    private fun checkManifest(manifest: ContentManifest) {
        if (manifest.schemaVersion !in 1..SUPPORTED_SCHEMA_VERSION) {
            throw IllegalStateException(
                "manifest.json schemaVersion ${manifest.schemaVersion} is unsupported " +
                    "(this build supports 1..$SUPPORTED_SCHEMA_VERSION)"
            )
        }
        val locale = manifest.locale
        if (locale.isBlank() || locale.startsWith('/') || locale.contains("..") || locale.contains('\\')) {
            throw IllegalStateException("manifest.json locale '$locale' is not a safe relative directory name")
        }
        if (manifest.contentVersion < 0) {
            throw IllegalStateException(
                "manifest.json contentVersion ${manifest.contentVersion} must be >= 0"
            )
        }
        val missing = DigilineContentFiles.REQUIRED_KEYS.filterNot { manifest.files.containsKey(it) }
        if (missing.isNotEmpty()) {
            throw IllegalStateException(
                "manifest.json is missing required file keys: ${missing.joinToString()}"
            )
        }
    }

    private inline fun <reified T> decode(path: String): T {
        val text = try {
            reader(path)
        } catch (e: Exception) {
            throw IllegalStateException("Unable to read DigiLine content file '$path'", e)
        }
        return try {
            json.decodeFromString<T>(text)
        } catch (e: SerializationException) {
            throw IllegalStateException("Malformed JSON in DigiLine content file '$path': ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Malformed JSON in DigiLine content file '$path': ${e.message}", e)
        }
    }

    companion object {
        /** Highest `schemaVersion` this loader understands; newer packs are rejected loudly. */
        const val SUPPORTED_SCHEMA_VERSION: Int = 1

        /**
         * Android factory: the loader opens the manifests/packs from the packaged assets
         * (`load()` uses [DigilineContentSource.DEFAULT_ASSET_ROOT]). Only this companion
         * function touches [AssetManager], so the loader stays JVM-testable.
         */
        fun fromAssets(assets: AssetManager): DigilineContentLoader =
            DigilineContentLoader(reader = { path ->
                assets.open(path).bufferedReader().use { it.readText() }
            })
    }
}
