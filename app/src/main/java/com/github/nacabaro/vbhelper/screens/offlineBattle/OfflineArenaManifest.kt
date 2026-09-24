package com.github.nacabaro.vbhelper.screens.offlineBattle

import android.content.Context
import org.json.JSONObject

/** Shared arena scale, combat bounds, spawn spacing, and camera values generated with the GLB. */
data class OfflineArenaManifest(
    val assetPath: String,
    val visualScale: Float,
    val positionScale: Float,
    val fighterScale: Float,
    val playableRadius: Float,
    val cameraCollisionRadius: Double,
    val lineupRowSpacing: Float,
    val lineupDepthRatio: Float,
    val minimumLineupDepth: Float,
    val cameraDistance: Double,
    val cameraPitchRadians: Double,
    val cameraTargetY: Double,
    val cameraMinDistance: Double,
    val cameraMaxDistance: Double,
    val cameraMinPitchRadians: Double,
    val cameraMaxPitchRadians: Double
) {
    init {
        require(assetPath.isNotBlank())
        require(listOf(visualScale, positionScale, fighterScale, playableRadius,
            lineupRowSpacing, lineupDepthRatio, minimumLineupDepth).all(Float::isFinite))
        require(visualScale > 0f && positionScale > 0f && fighterScale > 0f && playableRadius > 0f)
        require(lineupRowSpacing > 0f && lineupDepthRatio in 0f..1f && minimumLineupDepth >= 0f)
        require(listOf(cameraDistance, cameraPitchRadians, cameraTargetY, cameraMinDistance,
            cameraMaxDistance, cameraMinPitchRadians, cameraMaxPitchRadians, cameraCollisionRadius).all(Double::isFinite))
        require(cameraMinDistance > 0.0 && cameraMaxDistance >= cameraMinDistance)
        require(cameraCollisionRadius > playableRadius && cameraCollisionRadius > 0.0)
        require(cameraMinPitchRadians < cameraMaxPitchRadians)
        require(cameraDistance in cameraMinDistance..cameraMaxDistance)
        require(cameraPitchRadians in cameraMinPitchRadians..cameraMaxPitchRadians)
    }

    companion object {
        private const val MANIFEST_PATH = "Arena/Colosseum/arena.json"

        fun read(context: Context): OfflineArenaManifest {
            val json = context.assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            require(root.optInt("schemaVersion") == 1) { "Versão não suportada do manifesto da arena." }
            require(root.optString("upAxis") == "Y" && root.optString("playPlane") == "XZ") {
                "A arena precisa usar Y para cima e combate no plano XZ."
            }
            val camera = root.getJSONObject("camera")
            return OfflineArenaManifest(
                assetPath = root.getString("assetPath"),
                visualScale = root.getDouble("visualScale").toFloat(),
                positionScale = root.getDouble("positionScale").toFloat(),
                fighterScale = root.getDouble("fighterScale").toFloat(),
                playableRadius = root.getDouble("playableRadius").toFloat(),
                cameraCollisionRadius = root.getDouble("cameraCollisionRadius"),
                lineupRowSpacing = root.getDouble("lineupRowSpacing").toFloat(),
                lineupDepthRatio = root.getDouble("lineupDepthRatio").toFloat(),
                minimumLineupDepth = root.getDouble("minimumLineupDepth").toFloat(),
                cameraDistance = camera.getDouble("distance"),
                cameraPitchRadians = camera.getDouble("pitchRadians"),
                cameraTargetY = camera.getDouble("targetY"),
                cameraMinDistance = camera.getDouble("minDistance"),
                cameraMaxDistance = camera.getDouble("maxDistance"),
                cameraMinPitchRadians = camera.getDouble("minPitchRadians"),
                cameraMaxPitchRadians = camera.getDouble("maxPitchRadians")
            )
        }
    }
}
