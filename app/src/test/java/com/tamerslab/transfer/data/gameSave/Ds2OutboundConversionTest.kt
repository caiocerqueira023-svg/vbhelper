package com.tamerslab.transfer.data.gameSave

import com.google.gson.Gson
import com.tamerslab.gamesave.Ds2RecordCodec
import com.tamerslab.gamesave.WorldDsStatConversion
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDatabaseDto
import java.io.File
import org.junit.Assert.*
import org.junit.Test

/**
 * Dawn/Dusk send projection: app partners without an exact retained record are synthesized with the
 * game's own species defaults and the same bands its creation path uses.
 */
class Ds2OutboundConversionTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun table(): Ds2SpeciesTable = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())

    private fun catalog(): CanonicalSpeciesCatalog =
        CanonicalSpeciesCatalog(Gson().fromJson(asset("species_catalog.json").reader(), CanonicalSpeciesDatabaseDto::class.java))

    private fun speciesId(name: String): Long {
        val canonical = requireNotNull(catalog().resolve(name)) { "$name is not in the bundled catalog" }
        return requireNotNull(WorldDsSpeciesCrosswalk.ds2IdsByCanonical(table(), catalog())[canonical.id]) {
            "$name is not in the Dawn/Dusk species table"
        }
    }

    private fun state(name: String, nickname: String?, stats: WorldDsStatConversion.AppStats): Ds2OutboundState {
        val species = speciesId(name)
        val table = table()
        return Ds2OutboundState(
            dsSpeciesId = species,
            canonicalSpeciesId = requireNotNull(catalog().resolve(name)).id,
            displayName = Ds2OutboundConversion.recordName(nickname, requireNotNull(table.name(species))),
            fallbackName = requireNotNull(table.name(species)),
            appStats = stats,
            defaults = requireNotNull(Ds2OutboundConversion.defaultsOf(table, species)),
            lineage = table.lineage(species),
        )
    }

    private fun appStats(vitalPoints: Int) = WorldDsStatConversion.AppStats(stage = 1, baseHp = 100,
        baseBp = 80, baseAp = 90, trainingHp = 40, trainingBp = 30, trainingAp = 20, vitalPoints = vitalPoints,
        mood = 50, trophies = 3, wins = 5, losses = 2, activityLevel = 7)

    @Test fun crosswalkMapsSpeciesThatOnlyHaveAFullRomName() {
        // Regression for "Metal Mamemon is listed as not being in Dawn/Dusk": the display table
        // truncates it to "MtlMamemon", the full ROM name table does not.
        assertEquals(0x124L, speciesId("Metal Mamemon"))
        assertEquals(0x133L, speciesId("Were Garurumon (Black)"))
        assertEquals(0x134L, speciesId("Were Garurumon"))
        assertEquals(0x1B7L, speciesId("Prince Mamemon"))
        assertEquals(0x120L, speciesId("Volcamon"))
        assertEquals(0x85L, speciesId("Agumon (Black)"))
    }

    @Test fun aCardCharacterVariantRefinesTheIndividualsBaseSpecies() {
        val catalog = catalog()
        val agumon = catalog.resolve("Agumon")
        val agumonBlack = catalog.resolve("Agumon (Black)")
        val greymon = catalog.resolve("Greymon")
        assertNotNull(agumon)
        assertNotNull(agumonBlack)
        assertNotNull(greymon)
        assertEquals("digimon.agumonblack",
            WorldDsSpeciesCrosswalk.preferRefined(catalog, agumon, agumonBlack)?.id)
        // A different Digimon never replaces the individual's own species.
        assertEquals("digimon.greymon",
            WorldDsSpeciesCrosswalk.preferRefined(catalog, greymon, agumonBlack)?.id)
        assertEquals("digimon.agumon",
            WorldDsSpeciesCrosswalk.preferRefined(catalog, agumon, catalog.resolve("Agumon"))?.id)
    }

    @Test fun crosswalkMapsBundledSpeciesNamesToDawnDuskIds() {
        assertEquals(156L, speciesId("Coronamon"))
        assertEquals(157L, speciesId("Lunamon"))
        assertEquals(226L, speciesId("Reppamon"))
    }

    @Test fun generatedRecordUsesTheGamesDefaultsAndTheAppProjection() {
        val state = state("Coronamon", "MyMon", appStats(WorldDsStatConversion.vitalPointsForLevel(12)))
        val outbound = Ds2OutboundConversion.generate(state, seedBase64 = null)
        assertNull(outbound.nativeIdentity)
        assertEquals("generated", outbound.recordKey)
        assertEquals("MyMon", outbound.storedName)
        assertEquals(12, outbound.level)
        val record = Ds2RecordCodec.decodeBase64(requireNotNull(outbound.nativeRecordBase64))
        assertEquals(Ds2RecordCodec.RECORD_BYTES, record.size)
        fun u16(at: Int) = (record[at].toInt() and 255) or ((record[at + 1].toInt() and 255) shl 8)
        fun u32(at: Int) = (0 until 4).fold(0L) { v, i -> v or ((record[at + i].toLong() and 255) shl (8 * i)) }
        assertEquals(0L, u32(0))                 // identity is assigned by the destination
        assertEquals(156L, u32(4))               // Coronamon
        assertEquals(12, u16(0x76))
        assertEquals(32, u16(0x52))              // species max level
        assertEquals(listOf(1, 36, 0xFFFF, 0xFFFF), (0 until 4).map { u16(0x22 + it * 2) })
        assertEquals(listOf(32, 52, 111, 59), (0 until 4).map { u16(0x54 + it * 2) })
        assertEquals(0x21, u16(0x70))
        assertEquals(0x18L, u32(0xB0))           // stage 1 flags
        assertEquals(listOf(86L, 156L, -1L, -1L, -1L),
            (0 until 5).map { u32(0xB4 + it * 4).toInt().toLong() })
        assertEquals(0x0040, u16(0xD2))          // Sunmon marked in the history bitmap
        assertEquals(0x1000, u16(0xDA))          // Coronamon marked in the history bitmap
        assertEquals(1, record[0x107].toInt())
        assertEquals(listOf(1000, 200, 900, 700, 700, 500, 400, 500), (0 until 8).map { u16(0x108 + it * 2) })
        assertEquals(0, u16(0x120))
        assertTrue((0 until Ds2StatConversion.EXP_CHANNELS).all { u32(0x78 + it * 4) == 0L })
        // HP/ATK/DEF reflect the app projection, not the bare species base values.
        assertTrue(requireNotNull(outbound.nativeParameters).maxHp > 128)
        assertEquals(Ds2StatConversion.EXP_CHANNELS, requireNotNull(outbound.nativeParameters).expCounters.size)
    }

    @Test fun seededGenerationKeepsTheNativeIdentitySpeciesAndUnknownBytes() {
        val coronamon = state("Coronamon", "Seed", appStats(WorldDsStatConversion.vitalPointsForLevel(12)))
        val seed = Ds2RecordCodec.generate(63, 0x9CL, "Coronamon", "Coronamon",
            Ds2RecordCodec.Ds2GeneratedStats(12, 300, 300, 200, 200, 100, 90, 80, 70, 32),
            defaults = coronamon.defaults, lineage = coronamon.lineage).bytes
        seed[0x140] = 0x5A // an unknown tail byte the engine never rewrites
        val grown = state("Coronamon", "Seed", appStats(WorldDsStatConversion.vitalPointsForLevel(40)))
        val outbound = Ds2OutboundConversion.generate(grown, Ds2RecordCodec.base64(seed))
        assertEquals("save-local:63", outbound.nativeIdentity)
        assertEquals("retained", outbound.recordKey)
        assertEquals(0x9CL, outbound.nativeSpeciesId)
        val record = Ds2RecordCodec.decodeBase64(requireNotNull(outbound.nativeRecordBase64))
        assertEquals(63L, Ds2RecordCodec.localId(record))
        // The species max level caps the projected level, exactly like the game itself.
        assertEquals(32, outbound.level)
        assertEquals(0x5A, record[0x140].toInt() and 255)
        assertEquals(32, (record[0x52].toInt() and 255) or ((record[0x53].toInt() and 255) shl 8))
    }

    @Test fun baselineAndLevelCoverageDecideBetweenRetainedAndGenerated() {
        val state = state("Coronamon", "Seed", appStats(WorldDsStatConversion.vitalPointsForLevel(12)))
        val retained = Ds2OutboundConversion.generate(state, null).copy(nativeIdentity = "save-local:63")
        val projection = com.tamerslab.domain.transfer.NativeAppProjection("individual", 1,
            Ds2StatConversion.VERSION, "APP_SENT_VALUES", state.appStats.stage, state.appStats.baseHp,
            state.appStats.baseBp, state.appStats.baseAp, state.appStats.trainingHp, state.appStats.trainingAp,
            state.appStats.trainingBp, state.appStats.vitalPoints, state.appStats.wins, state.appStats.losses, 0, 0, 10000)
        assertTrue(Ds2OutboundConversion.matchesBaseline(state, projection))
        assertTrue(Ds2OutboundConversion.retainedCoversAppLevel(state, retained))
        val grown = state.copy(appStats = appStats(WorldDsStatConversion.vitalPointsForLevel(40)))
        assertFalse(Ds2OutboundConversion.matchesBaseline(grown, projection))
        assertFalse(Ds2OutboundConversion.retainedCoversAppLevel(grown, retained))
    }
}
