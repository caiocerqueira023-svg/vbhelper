package com.github.nacabaro.vbhelper.rendering

import com.github.nacabaro.vbhelper.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EnvironmentThemeCatalogTest {
    private val catalog = EnvironmentThemeCatalog.parse(File("src/main/assets/environment_themes/manifest.json").readText())

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

    @Test fun radarLightThemesHaveLightFloorsAndDistinctEnergyColors() {
        val scene = catalog.scenes.getValue("radar")
        val floor = scene.materials.first { it.name == "radar_floor" }
        val boundary = scene.materials.first { it.name == "battle_boundary" }
        assertTrue(floor.variant(AppTheme.VB_HELPER).baseColor.take(3).all { it < .01f })
        assertTrue(floor.variant(AppTheme.VB_LAB).baseColor.take(3).all { it < .02f })
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            assertTrue(floor.variant(theme).baseColor.take(3).all { it > .8f })
        }
        assertEquals(4, AppTheme.entries.map { boundary.variant(it).baseColor }.toSet().size)
    }

    @Test fun defaultDomeFactorsRemainAuthoredAndLightEmissionIsBounded() {
        val dome = catalog.scenes.getValue("colosseum").materials.first { it.name == "Sphere001" }
        assertEquals(listOf(.5f, .16f, 1f), dome.variant(AppTheme.VB_HELPER).emission)
        for (theme in listOf(AppTheme.VB_ARENA, AppTheme.DIGIMON_NET)) {
            assertTrue(dome.variant(theme).emission.all { it <= .75f })
        }
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
