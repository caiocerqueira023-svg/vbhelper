package com.github.nacabaro.vbhelper.digifarm.map

import kotlin.math.roundToInt

/** A point in the Filament scene. Movement is on x/z; y is the ground height. */
data class FarmWorldPoint(val x: Float, val y: Float = 0f, val z: Float)

data class FarmWorldBounds(
    val min: List<Float> = listOf(-1f, -1f),
    val max: List<Float> = listOf(1f, 1f),
) {
    val minX: Float get() = min.getOrElse(0) { -1f }
    val minZ: Float get() = min.getOrElse(1) { -1f }
    val maxX: Float get() = max.getOrElse(0) { 1f }
    val maxZ: Float get() = max.getOrElse(1) { 1f }
}

data class Digifarm3dManifest(
    val schema: Int = 1,
    val mapId: String = Digifarm3dMap.mapId,
    val mapVersion: Int = 1,
    val asset: String = Digifarm3dMap.runtimeAsset,
    val coordinateSystem: String = "",
    val bounds: Any? = null,
    val playableBounds: FarmWorldBounds = FarmWorldBounds(),
    val safeSpawns: List<List<Float>> = emptyList(),
    val staticNodes: List<String> = emptyList(),
    val collisionSource: List<String> = emptyList(),
)

/**
 * Coordinate conversion shared by simulation, hit testing and the Compose
 * compatibility sprite layer. The old logical positions are only an import
 * format for existing residents; new pathfinding uses FarmWorldPoint.
 */
object Digifarm3dMap {
    const val mapId = "digi_farm_3d"
    const val mapVersion = 1
    const val runtimeAsset = "digifarm/3d/digi_farm_3d.glb"
    /** Tron variant: same geometry/bounds, black + cyan edges over purple sea. */
    const val tronRuntimeAsset = "digifarm/3d/digi_farm_3d_tron.glb"
    const val legacyWidth = 512f
    const val legacyHeight = 736f

    /** The versioned bounds from manifest.json, used before native camera projection is wired. */
    val defaultPlayableBounds = FarmWorldBounds(
        min = listOf(-0.68f, -0.43f),
        max = listOf(0.68f, 0.43f)
    )

    fun legacyToViewport(point: MapPoint): Pair<Float, Float> {
        val world = legacyToWorld(point, Digifarm3dManifest(playableBounds = defaultPlayableBounds))
        val bounds = defaultPlayableBounds
        val normalizedX = ((world.x - bounds.minX) / (bounds.maxX - bounds.minX)).coerceIn(0f, 1f)
        val normalizedZ = ((world.z - bounds.minZ) / (bounds.maxZ - bounds.minZ)).coerceIn(0f, 1f)
        return normalizedX to normalizedZ
    }

    fun legacyToWorld(point: MapPoint, manifest: Digifarm3dManifest): FarmWorldPoint {
        val u = (point.x / legacyWidth).coerceIn(0f, 1f)
        val v = (point.y / legacyHeight).coerceIn(0f, 1f)
        val bounds = manifest.playableBounds
        return FarmWorldPoint(
            x = lerp(bounds.minX, bounds.maxX, u),
            z = lerp(bounds.minZ, bounds.maxZ, v)
        )
    }

    fun worldToLegacy(point: FarmWorldPoint, manifest: Digifarm3dManifest): MapPoint {
        val bounds = manifest.playableBounds
        val u = inverseLerp(bounds.minX, bounds.maxX, point.x)
        val v = inverseLerp(bounds.minZ, bounds.maxZ, point.z)
        return MapPoint(
            x = (u * legacyWidth).roundToInt().toFloat(),
            y = (v * legacyHeight).roundToInt().toFloat()
        )
    }

    fun clamp(point: FarmWorldPoint, manifest: Digifarm3dManifest): FarmWorldPoint {
        val bounds = manifest.playableBounds
        return point.copy(
            x = point.x.coerceIn(bounds.minX, bounds.maxX),
            z = point.z.coerceIn(bounds.minZ, bounds.maxZ)
        )
    }

    fun safeSpawns(manifest: Digifarm3dManifest): List<FarmWorldPoint> =
        manifest.safeSpawns.mapNotNull { values ->
            if (values.size < 2) null else FarmWorldPoint(values[0], z = values[1])
        }.ifEmpty {
            listOf(FarmWorldPoint(0f, z = 0f))
        }

    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount

    private fun inverseLerp(start: Float, end: Float, value: Float): Float =
        if (end == start) 0.5f else ((value - start) / (end - start)).coerceIn(0f, 1f)
}
