package com.github.nacabaro.vbhelper.species

import org.junit.Assert.*
import org.junit.Test
import com.google.gson.Gson
import java.io.File
import java.security.MessageDigest

class CanonicalSpeciesCatalogTest {
    private fun set(id: String = "agumon-original", spriteId: Int = -100) = DotSpriteSetDto(
        id = id, spriteId = spriteId, name = "Agumon", author = "Bandai", source = "Digimon Color",
        width = 48, height = 48, frames = mapOf("idle_1" to "frames/${"a".repeat(64)}.bin",
            "idle_2" to "frames/${"b".repeat(64)}.bin", "atk_2" to "frames/${"c".repeat(64)}.bin"))

    private fun species(id: String = "digimon.agumon", name: String = "Agumon", sets: List<String> = listOf("agumon-original")) =
        CanonicalSpeciesDto(id = id, name = name, aliases = listOf("Agumon"), level = "Child",
            dotSprites = DotSpriteAssociationDto(sets.firstOrNull(), sets))

    private fun database(species: List<CanonicalSpeciesDto> = listOf(species()), sets: List<DotSpriteSetDto> = listOf(set())) =
        CanonicalSpeciesDatabaseDto(schemaVersion = 1, species = species, spriteSets = sets)

    @Test fun immutableIdentityResolvesNamedAliasesWithoutCollapsingForms() {
        val dukemon = species("digimon.dukemon", "Dukemon").copy(aliases = listOf("Gallantmon"))
        val catalog = CanonicalSpeciesCatalog(database(listOf(dukemon)))
        assertEquals("digimon.dukemon", catalog.resolve("Gallantmon")!!.id)
        assertNull(catalog.resolve("Dukemon Crimson Mode"))
        assertNull(catalog.resolve("Dukemon X"))
    }

    @Test fun readinessRequiresDigimonClassificationAndBundledArtwork() {
        val ready = species()
        val missing = species("digimon.new", "Newmon", emptyList())
        val collaboration = species("reference.medal", "Medal").copy(kind = "OBJECT")
        val catalog = CanonicalSpeciesCatalog(database(listOf(ready, missing, collaboration)))
        assertTrue(catalog.isExpandedReady(ready.id))
        assertFalse(catalog.isExpandedReady(missing.id))
        assertFalse(catalog.isExpandedReady(collaboration.id))
    }

    @Test fun ambiguousAliasDoesNotSilentlyChooseADifferentSpecies() {
        val one = species().copy(aliases = listOf("Shared name"))
        val two = species("digimon.other", "Othermon").copy(aliases = listOf("Shared name"))
        assertNull(CanonicalSpeciesCatalog(database(listOf(one, two))).resolve("Shared name"))
    }

    @Test fun canonicalIdentityAndAliasesRemainSearchableWhenSourceArtworkUsesAnotherName() {
        val dukemon = species("digimon.dukemon", "Dukemon").copy(aliases = listOf("Gallantmon"))
        val artwork = set().copy(name = "Gallantmon")
        val catalog = CanonicalSpeciesCatalog(database(listOf(dukemon), listOf(artwork)))
        assertEquals(listOf(artwork), catalog.searchSets("digimon.dukemon", "Dukemon", true))
        assertEquals(listOf(artwork), catalog.searchSets("digimon.dukemon", "Gallantmon", true))
        assertEquals(listOf(artwork), catalog.searchSets("My Knightmon", "Dukemon", false))
        assertTrue(catalog.searchSets("digimon.dukemon", "Another artist", true).isEmpty())
        assertEquals(listOf(artwork), catalog.setsForSpecies("digimon.dukemon"))
    }

    @Test fun pencAttackAndMissingMotionUseNamedRolesRatherThanArrayOffsets() {
        val set = set()
        assertEquals(set.frames["atk_2"], DotFrameRoles.path(set, 11))
        assertEquals(set.frames["idle_1"], DotFrameRoles.path(set, 3))
        assertEquals(set.frames["idle_2"], DotFrameRoles.path(set, 4))
        assertEquals(set.frames["idle_1"], DotFrameRoles.path(set, 9))
    }

    @Test fun corruptAssociationsAndUnsafeFramePathsFailBeforeArtworkIsLoaded() {
        assertThrows(IllegalArgumentException::class.java) { CanonicalSpeciesCatalog(database(sets = emptyList())) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalSpeciesCatalog(database(sets = listOf(set().copy(spriteId = 100)))) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalSpeciesCatalog(database(sets = listOf(set().copy(frames = mapOf("idle_1" to "../other.bin"))))) }
        assertThrows(IllegalArgumentException::class.java) { CanonicalSpeciesCatalog(database(sets = listOf(set(), set("another", -100)))) }
    }

    @Test fun shippedCatalogIsReadableAndEveryReadyIdentityReferencesTheShippedPack() {
        val assets = File("src/main/assets").takeIf { it.isDirectory } ?: File("app/src/main/assets")
        val data = File(assets, "species_catalog.json").reader().use {
            Gson().fromJson(it, CanonicalSpeciesDatabaseDto::class.java)
        }
        val catalog = CanonicalSpeciesCatalog(data)
        assertTrue(catalog.data.species.size > 1_000)
        assertTrue(catalog.data.spriteSets.size > 1_000)
        catalog.data.species.forEach { assertEquals(it.id, catalog.resolve(it.name)?.id) }
        val agumon = catalog.resolve("Agumon")!!
        assertTrue(catalog.isExpandedReady(agumon.id))
        assertEquals("Bandai", catalog.setsById[agumon.dotSprites.defaultSetId]!!.author)
        assertFalse(catalog.isExpandedReady(catalog.resolve("Medal")!!.id))
        assertEquals(catalog.resolve("Omegamon")!!.id, catalog.resolve("Omnimon")!!.id)
        for ((canonical, alias) in listOf("Diablomon" to "Diaboromon", "Armagemon" to "Armageddemon",
            "Anubimon" to "Anubismon", "Chrysalimon" to "Kurisarimon", "Holy Angemon" to "MagnaAngemon",
            "Zeke Greymon" to "Zeig Greymon", "Koemon" to "Monmon", "Red Vegimon" to "Red Vegiemon")) {
            val species = requireNotNull(catalog.resolve(canonical)) { canonical }
            assertEquals(canonical, species.id, catalog.resolve(alias)?.id)
            assertTrue(canonical, catalog.isExpandedReady(species.id))
        }
        assertNotEquals(catalog.resolve("Diablomon")!!.id, catalog.resolve("Diaboromon X")!!.id)
        assertTrue(catalog.isExpandedReady(catalog.resolve("Allomon")!!.id))
        assertEquals(catalog.resolve("Red Vegimon")!!.id, catalog.resolve("Vegimon (Red)")!!.id)
        assertNotEquals(catalog.resolve("Red Vegimon")!!.id, catalog.resolve("Vegimon")!!.id)
        assertTrue(catalog.searchSets("Koemon", "Monmon", true).isNotEmpty())
        assertTrue(catalog.searchSets("Red Vegimon", "Red Vegiemon", true).isNotEmpty())
        assertEquals(setOf("dmc-sprites", "digitama-hatchery", "wikimon"), data.spriteSources.map { it.id }.toSet())
        assertTrue(data.spriteSets.any { it.provider == "wikimon" })
        assertTrue(catalog.isExpandedReady(catalog.resolve("Azhdarmon")!!.id))
        val pack = File(assets, "dot_sprites.zip")
        assertEquals(data.packBytes, pack.length())
        val digest = MessageDigest.getInstance("SHA-256").digest(pack.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(data.packSha256, digest)
    }
}
