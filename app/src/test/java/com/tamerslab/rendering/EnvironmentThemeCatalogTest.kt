package com.tamerslab.rendering

import com.tamerslab.ui.theme.AppTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EnvironmentThemeCatalogTest {
    private val manifestText = File("src/main/assets/environment_themes/manifest.json").readText()
    private val catalog = EnvironmentThemeCatalog.parse(manifestText)
    private val palettes = JSONObject(manifestText).getJSONObject("palettes")

    @Test fun everyEnvironmentHasAllFourPalettesAndOnlyEnvironmentBindings() {
        assertEquals(3, catalog.scenes.size)
        for (scene in catalog.scenes.values) {
            assertTrue(scene.materials.isNotEmpty())
            assertEquals(scene.materials.size, scene.materials.map { it.name }.toSet().size)
            for (material in scene.materials) {
                assertFalse(material.name.contains("_face"))
                assertFalse(material.name.contains("_edge"))
                assertFalse(material.name.contains("contact_shadow"))
                assertEquals(AppTheme.entries.map { it.preferenceValue }.toSet(), material.variants.keys)
                for (variant in material.variants.values) {
                    assertEquals(4, variant.baseColor.size)
                    assertEquals(3, variant.emission.size)
                    assertTrue(variant.baseColor.all { it in 0f..1f })
                    variant.textures.values.forEach {
                        assertTrue(File("src/main/assets/${it.asset}").isFile())
                    }
                }
            }
        }
        assertNull(catalog.sceneForAsset("custom/unmapped_scene.glb"))
    }

    @Test fun alphaAndUvSamplingAreSharedAcrossAllThemes() {
        for (scene in catalog.scenes.values) for (material in scene.materials) {
            val baseline = material.variant(AppTheme.VB_HELPER)
            for (theme in AppTheme.entries) {
                val variant = material.variant(theme)
                assertEquals(baseline.baseColor.last(), variant.baseColor.last())
                assertEquals(baseline.textures.keys, variant.textures.keys)
                for (channel in baseline.textures.keys) {
                    assertEquals(baseline.textures.getValue(channel).sampler, variant.textures.getValue(channel).sampler)
                }
            }
        }
    }

    @Test fun radarLightThemesKeepALightFloorWithDarkerGridAndReadableBoundary() {
        val scene = catalog.scenes.getValue("radar")
        val floor = scene.materials.first { it.name == "radar_floor" }
        val grid = scene.materials.first { it.name == "world_grid" }
        val boundary = scene.materials.first { it.name == "battle_boundary" }
        assertTrue(floor.variant(AppTheme.VB_HELPER).baseColor.take(3).all { it < .01f })
        assertTrue(floor.variant(AppTheme.VB_LAB).baseColor.take(3).all { it < .02f })
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            val floorLuminance = relativeLuminance(floor.variant(theme).baseColor)
            // A near-white floor on a near-white backdrop is what flattened the light radar.
            assertTrue("$theme radar floor stopped reading as a surface", floorLuminance > .4f)
            assertTrue("$theme radar floor must stay lighter than its grid",
                relativeLuminance(grid.variant(theme).baseColor) < floorLuminance * .5f)
            assertTrue("$theme battle boundary disappears into the floor",
                contrastRatio(relativeLuminance(boundary.variant(theme).baseColor), floorLuminance) > 2.5f)
        }
        assertEquals(4, AppTheme.entries.map { boundary.variant(it).baseColor }.toSet().size)
    }

    /**
     * The light themes must not repaint the whole map in the theme hue: geometry keeps a
     * neutral ink ramp that reaches true black, and chroma is reserved for accents. Tinting
     * the mid-tones is what produced the washed-out, oddly green/blue light arenas.
     */
    @Test fun lightThemesUseNeutralInkAndReserveChromaForAccents() {
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            val palette = palettes.getJSONObject(theme.preferenceValue)
            for (key in listOf("structureDark", "structureLight", "grid")) {
                assertTrue("$theme $key is tinted toward the theme hue", chroma(srgb(palette, key)) < .10f)
            }
            assertTrue("$theme structure ramp no longer reaches black",
                luminance(srgb(palette, "structureDark")) < .12f)
            assertTrue("$theme panel blocks are no longer near black",
                luminance(srgb(palette, "panel")) < .08f)
            assertTrue("$theme energy lost its accent chroma", chroma(srgb(palette, "energy")) > .25f)
        }
    }

    private fun srgb(palette: JSONObject, key: String): List<Float> {
        val hex = palette.getString(key)
        require(hex.length == 7 && hex[0] == '#') { "Unexpected ${palette}.$key color $hex" }
        return (1..5 step 2).map { hex.substring(it, it + 2).toInt(16) / 255f }
    }

    private fun luminance(srgb: List<Float>) =
        .2126f * srgb[0] + .7152f * srgb[1] + .0722f * srgb[2]

    private fun chroma(srgb: List<Float>) = srgb.max() - srgb.min()

    /** Manifest factors are already linear, unlike the sRGB palette swatches. */
    private fun relativeLuminance(linear: List<Float>) =
        .2126f * linear[0] + .7152f * linear[1] + .0722f * linear[2]

    private fun contrastRatio(a: Float, b: Float) =
        (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)

    @Test fun defaultDomeFactorsRemainAuthoredAndLightEmissionIsBounded() {
        val dome = catalog.scenes.getValue("colosseum").materials.first { it.name == "Sphere001" }
        assertEquals(listOf(.5f, .16f, 1f), dome.variant(AppTheme.VB_HELPER).emission)
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            assertTrue(dome.variant(theme).emission.all { it <= .75f })
        }
    }

    @Test fun farmGlowKeepsDarkThemePeaksInLightThemes() {
        // The farm's floor and cubes are theme-tinted, so their pulse is allowed past the white-surface
        // ceiling: it must stay smooth, but it has to reach HDR levels that bloom.
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            val values = (0..30).map { sceneEmissionStrength(theme, 1f + it / 10f, peakCeiling = 1.62f) }
            assertTrue(values.all { it >= 1f && it <= 1.62f })
            assertTrue(values.zipWithNext().all { (a, b) -> b > a })
            assertTrue("farm pulse never reaches bloom: ${values.last()}", values.last() > 1.3f)
        }
        // The default ceiling is unchanged for every other environment.
        assertEquals(1f, sceneEmissionStrength(AppTheme.VB_ARENA, 1f), 0f)
        assertTrue(sceneEmissionStrength(AppTheme.VB_ARENA, 3f) < 1.15f)
    }

    @Test fun lightThemePulsesStaySmoothWithoutWashingOutWhiteSurfaces() {
        for (theme in listOf(AppTheme.VB_HELPER, AppTheme.VB_LAB)) {
            assertEquals(2.13f, sceneEmissionStrength(theme, 2.13f), 0f)
        }
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            assertEquals(.68f, sceneEmissionStrength(theme, .68f), 0f)
            val values = (0..30).map { sceneEmissionStrength(theme, 1f + it / 10f) }
            assertTrue(values.all { it >= 1f && it < 1.15f })
            assertTrue(values.zipWithNext().all { (a, b) -> b > a })
        }
    }
}
