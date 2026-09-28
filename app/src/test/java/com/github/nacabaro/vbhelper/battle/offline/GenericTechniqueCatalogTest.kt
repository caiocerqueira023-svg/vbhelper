package com.github.nacabaro.vbhelper.battle.offline

import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueImpactShape
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueKind
import com.github.nacabaro.vbhelper.battle.offline.core.TechniqueRangeProfile
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueCatalog
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueFamily
import com.github.nacabaro.vbhelper.battle.offline.data.GenericTechniqueLoadout
import com.github.nacabaro.vbhelper.battle.offline.data.TechniqueStatusPresentation
import com.github.nacabaro.vbhelper.battle.offline.data.TrainingBattleFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericTechniqueCatalogTest {
    @Test
    fun catalogKeepsOneNeutralCounterpartForEveryDecodeSkillSlot() {
        val entries = GenericTechniqueCatalog.entries

        assertEquals(56, entries.size)
        assertEquals(56, entries.map { it.definition.techniqueId }.distinct().size)
        assertEquals(GenericTechniqueFamily.entries.toSet(), entries.map { it.family }.toSet())
        GenericTechniqueFamily.entries.forEach { family ->
            assertEquals((1..7).toList(), entries.filter { it.family == family }.map { it.rank }.sorted())
        }
        entries.forEach { entry ->
            assertNull(entry.definition.element)
            assertTrue(entry.definition.techniqueId.startsWith("generic_"))
            assertTrue(entry.sourcePower >= 0)
            assertTrue(entry.sourceEnergyCost >= 0)
        }
    }

    @Test
    fun catalogSeparatesActivationRangeFromImpactGeometry() {
        val definitions = GenericTechniqueCatalog.entries.map { it.definition }

        assertTrue(definitions.any { it.rangeProfile == TechniqueRangeProfile.CLOSE })
        assertTrue(definitions.any { it.rangeProfile == TechniqueRangeProfile.CLOSE_MEDIUM })
        assertTrue(definitions.any { it.rangeProfile == TechniqueRangeProfile.MEDIUM_LONG })
        assertTrue(definitions.any { it.rangeProfile == TechniqueRangeProfile.ALL_FIELD })
        assertTrue(definitions.any { it.rangeProfile == TechniqueRangeProfile.SELF })
        assertTrue(definitions.any { it.impactShape == TechniqueImpactShape.AROUND_USER })
        assertTrue(definitions.any { it.impactShape == TechniqueImpactShape.AROUND_TARGET })
        assertTrue(definitions.any { it.impactShape == TechniqueImpactShape.ALL_OPPONENTS })
        assertTrue(definitions.any { it.hitCount > 1 })
        definitions.filter { it.rangeProfile == TechniqueRangeProfile.ALL_FIELD }.forEach {
            assertEquals(TechniqueImpactShape.ALL_OPPONENTS, it.impactShape)
        }
        definitions.filter { it.impactShape == TechniqueImpactShape.AROUND_USER }.forEach {
            assertTrue("${it.displayName} can start beyond its own impact", it.maxRange <= it.areaRadius)
        }
    }

    @Test
    fun trainingFactoryUsesARangeCompleteNeutralLoadoutInsteadOfPracticePlaceholders() {
        val techniques = TrainingBattleFactory.techniques
        val regular = techniques.filter { it.kind != TechniqueKind.SPECIAL }
        val special = techniques.single { it.kind == TechniqueKind.SPECIAL }

        assertEquals(56, techniques.size)
        assertTrue(techniques.all { it.techniqueId.startsWith("generic_") && it.element == null })
        assertFalse(techniques.any { it.techniqueId.startsWith("practice_") })
        assertTrue(regular.any { it.rangeProfile == TechniqueRangeProfile.CLOSE })
        assertTrue(regular.any { it.rangeProfile == TechniqueRangeProfile.MEDIUM_LONG })
        assertTrue(regular.any { it.impactShape == TechniqueImpactShape.AROUND_USER })
        assertEquals(TechniqueRangeProfile.MEDIUM_LONG, special.rangeProfile)
        assertEquals(TechniqueImpactShape.AROUND_TARGET, special.impactShape)
        assertEquals(0, special.energyCost)
        assertEquals(0, special.commandPointCost)
        assertTrue(special.hitCount > 1)
    }

    @Test
    fun loadoutKeepsThreeUniqueKnownRegularTechniquesAndRepairsInvalidData() {
        val selected = GenericTechniqueLoadout.resolve(
            listOf(
                "generic_control_impact_shot",
                "generic_control_impact_shot",
                GenericTechniqueCatalog.specialTechniqueId,
                "unknown"
            )
        )

        assertEquals(3, selected.size)
        assertEquals(3, selected.distinct().size)
        assertEquals("generic_control_impact_shot", selected.first())
        assertFalse(GenericTechniqueCatalog.specialTechniqueId in selected)
        assertTrue(selected.all { id -> GenericTechniqueCatalog.selectableEntries.any { it.definition.techniqueId == id } })
    }

    @Test
    fun catalogKeepsDecodeStatusCategoriesOnTheirEquivalentSkillSlots() {
        fun statusIds(techniqueId: String) = GenericTechniqueCatalog.definition(techniqueId)
            .statusEffects
            .map { it.id }

        assertEquals(listOf("decode_stun"), statusIds("generic_tempo_interrupting_touch"))
        assertEquals(listOf("decode_paralysis"), statusIds("generic_control_disruptive_drop"))
        assertEquals(listOf("decode_paralysis"), statusIds("generic_endurance_rupture_wave"))
        assertEquals(emptyList<String>(), statusIds("generic_assault_pressure_spin"))
        assertEquals(listOf("decode_paralysis"), statusIds("generic_assault_rupture_charge"))
        assertEquals(listOf("decode_paralysis"), statusIds("generic_tactics_disruptive_contact"))
        assertEquals(listOf("decode_paralysis"), statusIds("generic_precision_contact_orb"))
        assertEquals(listOf("decode_noise"), statusIds("generic_tempo_total_overload"))
        assertEquals(listOf("decode_liquid_crystalization"), statusIds(GenericTechniqueCatalog.specialTechniqueId))
    }

    @Test
    fun statusPresentationUsesDecodeEquivalentNamesWithoutLeakingInternalIds() {
        val labels = GenericTechniqueCatalog.entries
            .flatMap { TechniqueStatusPresentation.forTechnique(it.definition) }

        assertTrue(labels.isNotEmpty())
        assertTrue(labels.none { it.name.startsWith("generic_") || it.name.startsWith("decode_") })
        assertEquals(
            setOf(
                "Veneno",
                "Paralisia",
                "Stun",
                "Slow",
                "Confusão",
                "Liquid Crystalization",
                "Noise",
                "Ataque ↑",
                "Defesa ↑",
                "Velocidade ↑",
                "Todos os atributos ↑"
            ),
            labels.map { it.name }.toSet()
        )
    }
}
