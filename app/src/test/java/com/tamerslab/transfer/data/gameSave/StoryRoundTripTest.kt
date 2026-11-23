package com.tamerslab.transfer.data.gameSave

import com.google.gson.Gson
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDatabaseDto
import java.io.File
import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import com.tamerslab.gamesave.WorldDsRecordCodec
import com.tamerslab.gamesave.WorldDsStatConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Story progression must survive a write into a story title and a read back unchanged: that is the
 * core promise of the shared story block (story games differ only by layout, not by progress).
 */
class StoryRoundTripTest {
    private fun story() = StoryProgressDomain(
        individualId = "individual-rt",
        level = 63,
        expTotal = 123_456L,
        hp = 3_210,
        maxHp = 4_000,
        mp = 210,
        maxMp = 260,
        attack = 540,
        defense = 480,
        spirit = 410,
        speed = 95,
        aptitude = 74,
        seededAt = 1_700_000_000_000L,
        seedSource = ProgressSeedSource.VB,
        revision = 2L,
        updatedAt = 1_700_000_000_000L,
    )

    private fun appStats() = WorldDsStatConversion.AppStats(
        stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
        trainingHp = 12, trainingBp = 8, trainingAp = 5, vitalPoints = 4200, mood = 60,
        wins = 2, losses = 1, generation = 2, rank = 1,
    )

    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun ds2Table(): Ds2SpeciesTable = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())

    private fun ds2Catalog(): CanonicalSpeciesCatalog = CanonicalSpeciesCatalog(
        Gson().fromJson(asset("species_catalog.json").reader(), CanonicalSpeciesDatabaseDto::class.java))

    private fun ds2State(name: String, nickname: String?): Ds2OutboundState {
        val table = ds2Table()
        val catalog = ds2Catalog()
        val canonical = requireNotNull(catalog.resolve(name))
        val species = requireNotNull(WorldDsSpeciesCrosswalk.ds2IdsByCanonical(table, catalog)[canonical.id])
        return Ds2OutboundState(
            dsSpeciesId = species,
            canonicalSpeciesId = canonical.id,
            displayName = Ds2OutboundConversion.recordName(nickname, requireNotNull(table.name(species))),
            fallbackName = requireNotNull(table.name(species)),
            appStats = appStats(),
            defaults = requireNotNull(Ds2OutboundConversion.defaultsOf(table, species)),
            lineage = table.lineage(species),
        )
    }

    @Test
    fun `world ds write and read back keeps the story block and the nickname`() {
        val state = WorldDsOutboundState(
            dsSpeciesId = 2, canonicalSpeciesId = "digimon.greymon", displayName = "CAIO",
            fallbackName = "GREYMON", stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
            appStats = appStats(),
        )
        val record = WorldDsOutboundConversion.generate(state, seedBase64 = null,
            nativeStats = StoryProgressTracking.toWorldDsNative(story()))
        assertEquals("CAIO", record.storedName)
        val decoded = requireNotNull(WorldDsReceiveConversion.nativeStats(record))
        val roundTripped = StoryProgressTracking.fromWorldDs(decoded)
        assertEquals(63, roundTripped.level)
        assertEquals(4_000, roundTripped.maxHp)
        assertEquals(3_210, roundTripped.hp)
        assertEquals(260, roundTripped.maxMp)
        assertEquals(540, roundTripped.attack)
        assertEquals(480, roundTripped.defense)
        assertEquals(410, roundTripped.spirit)
        assertEquals(95, roundTripped.speed)
        assertEquals(123_456L, roundTripped.expTotal)
        // Aptitude is a Lost Evolution extra: not written into a World DS record.
        assertEquals(null, roundTripped.aptitude)
    }

    @Test
    fun `lost evolution write and read back keeps the story stats and aptitude`() {
        val state = LeOutboundState(
            dsSpeciesId = 77, canonicalSpeciesId = "digimon.agumon", displayName = "Agumon",
            fallbackName = "Agumon", appStats = appStats(), defaults = null, aptitude = 100,
        )
        val record = LeOutboundConversion.generate(state, seedBase64 = null,
            nativeStats = StoryProgressTracking.toLeNative(story()),
            expTotal = story().expTotal)
        val decoded = requireNotNull(LeStatConversion.nativeStats(record))
        val roundTripped = StoryProgressTracking.fromLe(decoded)
        assertEquals(63, roundTripped.level)
        assertEquals(4_000, roundTripped.maxHp)
        assertEquals(3_210, roundTripped.hp)
        assertEquals(540, roundTripped.attack)
        assertEquals(480, roundTripped.defense)
        assertEquals(410, roundTripped.spirit)
        assertEquals(95, roundTripped.speed)
        assertEquals(74, roundTripped.aptitude)
        // LE stores one EXP word, now written from the tracked story total.
        assertEquals(123_456L, decoded.exp.sum())
    }

    @Test
    fun `a retained seed keeps the record identity while story stats are written`() {
        val seed = ByteArray(0x180)
        seed[0x00] = 0x07 // a valid local id, preserved by the seed
        val state = WorldDsOutboundState(
            dsSpeciesId = 2, canonicalSpeciesId = "digimon.greymon", displayName = "CAIO",
            fallbackName = "GREYMON", stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
            appStats = appStats(),
        )
        val seeded = WorldDsOutboundConversion.generate(state,
            seedBase64 = WorldDsRecordCodec.base64(seed),
            nativeStats = StoryProgressTracking.toWorldDsNative(story()))
        val decoded = requireNotNull(WorldDsReceiveConversion.nativeStats(seeded))
        assertEquals(63, decoded.level)
        assertEquals("save-local:7", seeded.nativeIdentity)
    }

    @Test
    fun `dawn dusk write and read back keeps the story stats`() {
        val record = Ds2OutboundConversion.generate(ds2State("Agumon", "CAIO"), seedBase64 = null,
            nativeStats = StoryProgressTracking.toDs2Native(story()),
            expCounters = StoryProgressTracking.expChannels(story().expTotal, Ds2StatConversion.EXP_CHANNELS))
        assertEquals("CAIO", record.storedName)
        val decoded = requireNotNull(Ds2StatConversion.nativeStats(record))
        val roundTripped = StoryProgressTracking.fromDs2(decoded)
        assertEquals(63, roundTripped.level)
        assertEquals(4_000, roundTripped.maxHp)
        assertEquals(3_210, roundTripped.hp)
        assertEquals(260, roundTripped.maxMp)
        assertEquals(540, roundTripped.attack)
        assertEquals(480, roundTripped.defense)
        assertEquals(410, roundTripped.spirit)
        assertEquals(95, roundTripped.speed)
        assertEquals(123_456L, decoded.exp.sum())
    }
}
