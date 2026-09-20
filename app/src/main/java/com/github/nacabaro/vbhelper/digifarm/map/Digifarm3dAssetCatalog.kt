package com.github.nacabaro.vbhelper.digifarm.map

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.InputStreamReader

/** Validates the versioned runtime scene before a renderer is started. */
object Digifarm3dAssetCatalog {
    private const val MANIFEST_ASSET = "digifarm/3d/manifest.json"
    private val gson = Gson()

    fun read(context: Context): Result<Digifarm3dManifest> = runCatching {
        val manifest = context.assets.open(MANIFEST_ASSET).use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                gson.fromJson(reader, Digifarm3dManifest::class.java)
            }
        }
        require(manifest.mapId == Digifarm3dMap.mapId) { "Unexpected Digifarm map id: ${manifest.mapId}" }
        require(manifest.mapVersion == Digifarm3dMap.mapVersion) { "Unsupported Digifarm map version: ${manifest.mapVersion}" }
        require(manifest.asset == Digifarm3dMap.runtimeAsset) { "Manifest asset does not match the runtime loader" }
        require(manifest.playableBounds.maxX > manifest.playableBounds.minX)
        require(manifest.playableBounds.maxZ > manifest.playableBounds.minZ)
        require(Digifarm3dMap.safeSpawns(manifest).isNotEmpty())
        manifest
    }.onFailure { failure ->
        Log.e("Digifarm3d", "Digifarm manifest validation failed", failure)
    }
}
