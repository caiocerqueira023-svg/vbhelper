package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDatabaseDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NativeSpeciesResolutionTest {
    private val hint = NativeSpeciesHint("source", "document", "world-ds", "usa-raw", 1,
        "native:save-local:7", 20, "canonical:test")

    @Test fun resolvesAnExistingReviewedIndividualWithoutAskingForMappingAgain() {
        assertEquals("canonical:test", resolve(listOf(hint)))
    }

    @Test fun neverUsesANicknameOrAnotherSourcesLocalIdAsASpeciesMapping() {
        assertNull(resolve(emptyList()))
        assertNull(resolve(listOf(hint.copy(sourceId = "other", documentKey = "different"))))
        assertNull(resolve(listOf(hint.copy(nativeIdentity = "native:save-local:8"))))
    }

    @Test fun rejectsChangedSpeciesProfilesVersionsAndAmbiguousMappings() {
        assertNull(resolve(listOf(hint.copy(nativeSpeciesId = 21))))
        assertNull(resolve(listOf(hint.copy(formatProfile = "other-region"))))
        assertNull(resolve(listOf(hint.copy(codecVersion = 2))))
        assertNull(resolve(listOf(hint, hint.copy(canonicalSpeciesId = "canonical:other"))))
    }

    @Test fun theShippedGamesTableIdentifiesSpeciesBeforeAnyReview() {
        val catalog = catalog()
        assertEquals(catalog.resolve("Tanemon")!!.id, tableIdentity(4, catalog))
        assertEquals(catalog.resolve("Poyomon")!!.id, tableIdentity(2, catalog))
        assertEquals(catalog.resolve("Koromon")!!.id, tableIdentity(0, catalog))
    }

    @Test fun otherGamesProfilesVersionsUnusedSlotsAndUnnamedSpeciesStayUnidentified() {
        val catalog = catalog()
        assertNull(NativeSpeciesResolution.tableIdentity("digimon_world_ds_3ds", "world-ds-usa-drastic-raw-v1",
            1, 4, table(), catalog))
        assertNull(NativeSpeciesResolution.tableIdentity("digimon_world_ds_nds", "world-ds-eur", 1, 4, table(), catalog))
        assertNull(NativeSpeciesResolution.tableIdentity("digimon_world_ds_nds", "world-ds-usa-drastic-raw-v1",
            2, 4, table(), catalog))
        assertNull(tableIdentity(101, catalog))
        assertNull(tableIdentity(426, catalog))
        assertNull(tableIdentity(420, catalog))
        assertNull(NativeSpeciesResolution.tableIdentity("digimon_world_ds_nds", "world-ds-usa-drastic-raw-v1",
            1, 4, null, catalog))
    }

    @Test fun mostOfTheGamesOwnNamesResolveThroughTheShippedCatalog() {
        val catalog = catalog()
        val identified = (0 until 426).count { tableIdentity(it.toLong(), catalog) != null }
        assertTrue("only $identified of 426 species resolved", identified >= 300)
    }

    private fun tableIdentity(speciesId: Long, catalog: CanonicalSpeciesCatalog) =
        NativeSpeciesResolution.tableIdentity("digimon_world_ds_nds", "world-ds-usa-drastic-raw-v1", 1, speciesId,
            table(), catalog)

    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun table(): WorldDsSpeciesTable =
        WorldDsSpeciesTable.parse(asset("world_ds_species.json").reader().use { it.readText() })

    private fun catalog(): CanonicalSpeciesCatalog =
        CanonicalSpeciesCatalog(Gson().fromJson(asset("species_catalog.json").reader(), CanonicalSpeciesDatabaseDto::class.java))

    private fun resolve(hints: List<NativeSpeciesHint>) = NativeSpeciesResolution.reviewedIdentity(
        "source", "document", "world-ds", "usa-raw", 1, "save-local:7", 20, hints)
}
