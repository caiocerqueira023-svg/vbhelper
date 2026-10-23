package com.github.nacabaro.vbhelper.source.gameSave

import com.github.nacabaro.vbhelper.domain.transfer.NativeAppProjection
import com.github.nacabaro.vbhelper.gamesave.WorldDsRecordCodec
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import com.github.nacabaro.vbhelper.gamesave.WorldDsStatConversion
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesCatalog
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDatabaseDto
import com.github.nacabaro.vbhelper.species.CanonicalSpeciesDto
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WorldDsOutboundConversionTest {
    private val catalog = CanonicalSpeciesCatalog(CanonicalSpeciesDatabaseDto(species = listOf(
        CanonicalSpeciesDto(id = "digimon.agumon", name = "Agumon"),
        CanonicalSpeciesDto(id = "digimon.greymon", name = "Greymon"))))

    private fun table(vararg entries: Pair<Int, String>): WorldDsSpeciesTable {
        val names = MutableList(WorldDsSaveReader.SPECIES_COUNT) { "" }
        entries.forEach { names[it.first] = it.second }
        return WorldDsSpeciesTable.parse(Gson().toJson(mapOf(
            "gameId" to WorldDsSaveReader.GAME_ID,
            "formatProfile" to WorldDsSaveReader.PROFILE,
            "names" to names)))
    }

    private fun u16(bytes: ByteArray, at: Int) =
        (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun s32(bytes: ByteArray, at: Int) =
        (0 until 4).fold(0) { value, index -> value or ((bytes[at + index].toInt() and 255) shl (8 * index)) }

    private fun shippedTable(): WorldDsSpeciesTable {
        val file = File("src/main/assets/world_ds_species.json").takeIf { it.isFile }
            ?: File("app/src/main/assets/world_ds_species.json")
        return WorldDsSpeciesTable.parse(file.reader().use { it.readText() })
    }

    private fun state(wins: Int = 0): WorldDsOutboundState {
        val stats = WorldDsStatConversion.AppStats(stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
            trainingHp = 12, trainingBp = 8, trainingAp = 5, vitalPoints = 4200, mood = 60,
            trophies = 2, wins = wins, losses = 1, ageInDays = 30, activityLevel = 7, generation = 2, rank = 1)
        return WorldDsOutboundState(dsSpeciesId = 2, canonicalSpeciesId = "digimon.greymon",
            displayName = "CAIO", fallbackName = "GREYMON", stage = 3, baseHp = 40, baseBp = 30, baseAp = 20, appStats = stats)
    }

    @Test fun duplicateSpeciesNamesResolveToTheLowestGameId() {
        val map = WorldDsSpeciesCrosswalk.dsIdsByCanonical(
            table(0 to "Agumon", 2 to "Greymon", 5 to "Agumon"), catalog)
        assertEquals(0L, map["digimon.agumon"])
        assertEquals(2L, map["digimon.greymon"])
    }

    @Test fun unknownSpeciesNamesAreAbsentInsteadOfSubstituted() {
        val map = WorldDsSpeciesCrosswalk.dsIdsByCanonical(table(0 to "Agumon", 1 to "Plotmon", 2 to "Greymon"), catalog)
        assertFalse(map.containsKey("digimon.plotmon"))
    }

    @Test fun baselineMatchesOnlyWhenEveryTrackedFieldIsEqual() {
        val projection = NativeAppProjection("individual", 1, WorldDsStatConversion.VERSION, "APP_STARTING_VALUES",
            stage = 3, baseHp = 40, baseBp = 30, baseAp = 20, trainingHp = 12, trainingAp = 5, trainingBp = 8,
            vitalPoints = 4200, wins = 0, losses = 1, baselineDeltaId = 0, recordedAt = 0)
        assertTrue(WorldDsOutboundConversion.matchesBaseline(state(), projection))
        assertFalse(WorldDsOutboundConversion.matchesBaseline(state(wins = 1), projection))
        assertFalse(WorldDsOutboundConversion.matchesBaseline(state(), projection.copy(trainingHp = 13)))
    }

    @Test fun retainedSeedKeepsNativeIdentityAndSpecies() {
        val seed = ByteArray(0x180)
        seed[0x00] = 0x07
        seed[0x04] = 0x14
        seed[0x1A] = 0x03
        seed[0x52] = 0x39
        val record = WorldDsOutboundConversion.generate(state(), WorldDsRecordCodec.base64(seed))
        assertEquals("save-local:7", record.nativeIdentity)
        assertEquals(0x14L, record.nativeSpeciesId)
        val bytes = WorldDsRecordCodec.decodeBase64(requireNotNull(record.nativeRecordBase64))
        assertEquals(7L, WorldDsRecordCodec.localId(bytes))
        assertEquals(0x14L, WorldDsRecordCodec.speciesId(bytes))
        assertEquals(0x39, bytes[0x52].toInt() and 255)
    }

    @Test fun legacyRetainedSeedIsRepairedWithSpeciesDefaults() {
        val seed = ByteArray(0x180)
        seed[0x00] = 0x58; seed[0x01] = 0xBA.toByte(); seed[0x02] = 0xA7.toByte(); seed[0x03] = 0xAC.toByte()
        seed[0x04] = 35
        seed[0x52] = 31
        val renamon = state().copy(dsSpeciesId = 35, canonicalSpeciesId = "digimon.renamon",
            displayName = "RENAMON", fallbackName = "RENAMON", stage = 1)
        val table = shippedTable()
        val record = WorldDsOutboundConversion.generate(renamon, WorldDsRecordCodec.base64(seed),
            table.defaults(35), table.lineage(35))
        assertEquals("save-local:2896673368", record.nativeIdentity)
        assertEquals(35L, record.nativeSpeciesId)
        val bytes = WorldDsRecordCodec.decodeBase64(requireNotNull(record.nativeRecordBase64))
        assertEquals(31, u16(bytes, 0x52))
        assertEquals(7, bytes[0x1A].toInt() and 255)
        assertEquals(2, u16(bytes, 0x50))
        assertEquals(16, u16(bytes, 0x22))
        assertEquals(0x18, s32(bytes, 0xA0))
        assertEquals(4, s32(bytes, 0xA4))
        assertEquals(35, s32(bytes, 0xA8))
        assertEquals(listOf(3, 145, 331, 20), (0 until 4).map { u16(bytes, 0x54 + it * 2) })
        assertEquals(33, u16(bytes, 0x70))
        assertEquals(0x40, u16(bytes, 0x108))
    }

    @Test fun newRecordUsesShippedSpeciesDefaultsInsteadOfZeroedFields() {
        val table = shippedTable()
        val renamon = state().copy(dsSpeciesId = 35, canonicalSpeciesId = "digimon.renamon",
            displayName = "RENAMON", fallbackName = "RENAMON", stage = 1)
        val record = WorldDsOutboundConversion.generate(renamon, null, table.defaults(35), table.lineage(35))
        val bytes = WorldDsRecordCodec.decodeBase64(requireNotNull(record.nativeRecordBase64))
        assertEquals(7, bytes[0x1A].toInt() and 255)
        assertEquals(2, (bytes[0x50].toInt() and 255) or ((bytes[0x51].toInt() and 255) shl 8))
        assertEquals(1, bytes[0x1E].toInt() and 255)
        assertEquals(16, (bytes[0x22].toInt() and 255) or ((bytes[0x23].toInt() and 255) shl 8))
        assertEquals(0x18, s32(bytes, 0xA0))
        assertEquals(4, s32(bytes, 0xA4))
        assertEquals(35, s32(bytes, 0xA8))
        assertEquals(33, (bytes[0x70].toInt() and 255) or ((bytes[0x71].toInt() and 255) shl 8))
        assertEquals(0x40, (bytes[0x108].toInt() and 255) or ((bytes[0x109].toInt() and 255) shl 8))
        assertNull(record.nativeIdentity)
        assertEquals(35L, record.nativeSpeciesId)
    }

    @Test fun generatedRecordCarriesConvertedStatsAndSeedUnknownBytes() {
        val seed = ByteArray(0x180)
        seed[0x100] = 0x7F
        val record = WorldDsOutboundConversion.generate(state(), WorldDsRecordCodec.base64(seed))
        val bytes = WorldDsRecordCodec.decodeBase64(requireNotNull(record.nativeRecordBase64))
        assertEquals(384, bytes.size)
        assertEquals(2L, (0 until 4).fold(0L) { value, i -> value or ((bytes[i + 4].toLong() and 255) shl (8 * i)) })
        assertEquals(0x7F, bytes[0x100].toInt() and 255)
        assertNull(record.nativeIdentity)
        assertEquals(WorldDsStatConversion.levelForVitalPoints(4200), record.level)
    }
}