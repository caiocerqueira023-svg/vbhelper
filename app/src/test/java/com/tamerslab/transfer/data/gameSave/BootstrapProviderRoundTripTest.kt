package com.tamerslab.transfer.data.gameSave

import com.google.gson.Gson
import com.tamerslab.domain.transfer.ProgressSeedSource
import com.tamerslab.domain.transfer.StoryProgressDomain
import com.tamerslab.gamesave.WorldDsStatConversion
import com.tamerslab.species.CanonicalSpeciesCatalog
import com.tamerslab.species.CanonicalSpeciesDatabaseDto
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-time bootstrap path end to end at record level: vital points → level curve → the game's
 * created-at-level stats → a written title record → decoded back as a story snapshot. Covers all
 * three story titles and keeps the nickname as the stored name.
 */
class BootstrapProviderRoundTripTest {
    private fun asset(name: String): File =
        File("src/main/assets/$name").takeIf { it.isFile } ?: File("app/src/main/assets/$name")

    private fun storyFor(individualId: String, level: Int, stats: CreationStats): StoryProgressDomain =
        StoryProgressDomain(
            individualId = individualId,
            level = stats.level,
            expTotal = 0L,
            hp = stats.hp,
            maxHp = stats.maxHp,
            mp = stats.mp,
            maxMp = stats.maxMp,
            attack = stats.attack,
            defense = stats.defense,
            spirit = stats.spirit,
            speed = stats.speed,
            aptitude = stats.aptitude,
            seededAt = 1_700_000_000_000L,
            seedSource = ProgressSeedSource.VB,
            revision = 1L,
            updatedAt = 1_700_000_000_000L,
        )

    private fun appStats() = WorldDsStatConversion.AppStats(
        stage = 3, baseHp = 40, baseBp = 30, baseAp = 20, trainingHp = 12, trainingBp = 8,
        trainingAp = 5, vitalPoints = 7500, mood = 60, wins = 2, losses = 1, generation = 2, rank = 1,
    )

    @Test
    fun `world ds bootstrap writes the created level and stats and keeps the nickname`() {
        val table = WorldDsSpeciesTable.parse(asset("world_ds_species.json").readText())
        val species = (0L..425L).first { table.creation(it) != null && table.name(it) != null }
        val level = StoryBootstrapPolicy.levelFor(7500)
        val stats = requireNotNull(WorldDsCreationStats(table).statsFor(species, level))
        val story = storyFor("bootstrap-ds1", level, stats)
        val state = WorldDsOutboundState(
            dsSpeciesId = species, canonicalSpeciesId = "digimon.test", displayName = "CAIO",
            fallbackName = "TESTMON", stage = 3, baseHp = 40, baseBp = 30, baseAp = 20,
            appStats = appStats(),
        )
        val record = WorldDsOutboundConversion.generate(state, seedBase64 = null,
            nativeStats = StoryProgressTracking.toWorldDsNative(story))
        assertEquals("CAIO", record.storedName)
        assertEquals(75, level)
        assertEquals(75, record.level)
        val decoded = requireNotNull(WorldDsReceiveConversion.nativeStats(record))
        assertEquals(stats.level, decoded.level)
        assertEquals(stats.maxHp, decoded.maxHp)
        assertEquals(stats.attack, decoded.attack)
        assertTrue(decoded.level > 1)
    }

    @Test
    fun `dawn dusk bootstrap writes the created level and stats and keeps the nickname`() {
        val table = Ds2SpeciesTable.parse(asset("ds2_species.json").readText())
        val catalog = CanonicalSpeciesCatalog(
            Gson().fromJson(asset("species_catalog.json").reader(), CanonicalSpeciesDatabaseDto::class.java))
        val canonical = requireNotNull(catalog.resolve("Agumon"))
        val species = requireNotNull(WorldDsSpeciesCrosswalk.ds2IdsByCanonical(table, catalog)[canonical.id])
        val level = StoryBootstrapPolicy.levelFor(7500)
        val stats = requireNotNull(Ds2CreationStats(table).statsFor(species, level))
        val story = storyFor("bootstrap-ds2", level, stats)
        val state = Ds2OutboundState(
            dsSpeciesId = species,
            canonicalSpeciesId = canonical.id,
            displayName = "CAIO",
            fallbackName = requireNotNull(table.name(species)),
            appStats = appStats(),
            defaults = requireNotNull(Ds2OutboundConversion.defaultsOf(table, species)),
            lineage = table.lineage(species),
        )
        val record = Ds2OutboundConversion.generate(state, seedBase64 = null,
            nativeStats = StoryProgressTracking.toDs2Native(story),
            expCounters = StoryProgressTracking.expChannels(story.expTotal, Ds2StatConversion.EXP_CHANNELS))
        assertEquals("CAIO", record.storedName)
        val decoded = requireNotNull(Ds2StatConversion.nativeStats(record))
        assertEquals(stats.level, decoded.level)
        assertEquals(stats.maxHp, decoded.maxHp)
        assertEquals(stats.attack, decoded.attack)
        assertTrue(decoded.level > 1)
    }

    @Test
    fun `lost evolution bootstrap writes the created level and stats and keeps the nickname`() {
        val table = LeSpeciesTable.parse(asset("le_species.json").readText())
        val species = (54L..742L).first { table.defaults(it) != null && table.name(it) != null }
        val level = StoryBootstrapPolicy.levelFor(7500)
        val stats = requireNotNull(LeCreationStats(table).statsFor(species, level))
        val story = storyFor("bootstrap-le", level, stats)
        val state = LeOutboundState(
            dsSpeciesId = species,
            canonicalSpeciesId = "digimon.test",
            displayName = "CAIO",
            fallbackName = requireNotNull(table.name(species)),
            appStats = appStats(),
            defaults = table.defaults(species),
            aptitude = 100,
        )
        val record = LeOutboundConversion.generate(state, seedBase64 = null,
            nativeStats = StoryProgressTracking.toLeNative(story))
        assertEquals("CAIO", record.storedName)
        val decoded = requireNotNull(LeStatConversion.nativeStats(record))
        assertEquals(stats.level, decoded.level)
        assertEquals(stats.maxHp, decoded.maxHp)
        assertEquals(stats.attack, decoded.attack)
        assertTrue(decoded.level > 1)
    }
}
