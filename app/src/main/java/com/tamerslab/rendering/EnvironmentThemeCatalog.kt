package com.tamerslab.rendering

import com.tamerslab.ui.theme.AppTheme
import org.json.JSONArray
import org.json.JSONObject

internal data class EnvironmentSampler(val wrapS: Int, val wrapT: Int, val minFilter: Int, val magFilter: Int)
internal data class EnvironmentTexture(val asset: String, val sampler: EnvironmentSampler)
internal data class EnvironmentMaterialVariant(
    val baseColor: List<Float>, val emission: List<Float>, val textures: Map<String, EnvironmentTexture>,
)
internal data class EnvironmentMaterialTheme(val name: String, val variants: Map<String, EnvironmentMaterialVariant>) {
    fun variant(theme: AppTheme) = variants.getValue(theme.preferenceValue)
}
internal data class EnvironmentSceneTheme(val asset: String, val materials: List<EnvironmentMaterialTheme>)

/** Generated material allowlists and original factors; never infer a role from a sprite's color. */
internal class EnvironmentThemeCatalog(val scenes: Map<String, EnvironmentSceneTheme>) {
    fun sceneForAsset(asset: String) =
        scenes.values.firstOrNull { it.asset == asset }
            ?: scenes.values.firstOrNull { it.asset == assetAliases[asset] }

    companion object {
        const val ASSET = "environment_themes/manifest.json"

        /**
         * Derived scene assets reuse the authored scene's theme materials. The Radar place renders
         * `radar_plate.glb` (the radar arena without battle props/boundary); it has to keep the exact
         * floor and grid theme the authored radar grid carries, so it maps onto that scene instead of
         * shipping a duplicate material definition.
         */
        private val assetAliases = mapOf(
            "Arena/Radar/radar_plate.glb" to "Arena/Radar/radar_grid.glb",
        )

        fun parse(text: String): EnvironmentThemeCatalog {
            val root = JSONObject(text)
            require(root.getInt("schema") == 1)
            val scenes = root.getJSONObject("scenes")
            return EnvironmentThemeCatalog(scenes.keys().asSequence().associateWith { key ->
                val scene = scenes.getJSONObject(key)
                EnvironmentSceneTheme(scene.getString("asset"), scene.getJSONArray("materials").objects().map { material ->
                    val variants = material.getJSONObject("variants")
                    EnvironmentMaterialTheme(material.getString("name"), variants.keys().asSequence().associateWith { theme ->
                        val variant = variants.getJSONObject(theme)
                        val textures = variant.getJSONObject("textures")
                        EnvironmentMaterialVariant(variant.getJSONArray("baseColorFactor").floats(4),
                            variant.getJSONArray("emissiveFactor").floats(3),
                            textures.keys().asSequence().associateWith { parameter ->
                                require(parameter in setOf("baseColorMap", "emissiveMap"))
                                val texture = textures.getJSONObject(parameter)
                                val path = texture.getString("asset")
                                require(path.startsWith("environment_themes/textures/") && !path.contains(".."))
                                EnvironmentTexture(path, EnvironmentSampler(texture.getInt("wrapS"), texture.getInt("wrapT"),
                                    texture.getInt("minFilter"), texture.getInt("magFilter")))
                            })
                    }.also { require(it.keys == AppTheme.entries.map { theme -> theme.preferenceValue }.toSet()) })
                })
            })
        }

        private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
        private fun JSONArray.floats(size: Int): List<Float> {
            require(length() == size)
            return (0 until size).map { getDouble(it).toFloat().also { value -> require(value.isFinite() && value >= 0) } }
        }
    }
}
