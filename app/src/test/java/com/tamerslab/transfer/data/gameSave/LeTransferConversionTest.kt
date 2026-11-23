package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.transfer.NativeAppProjection
import com.tamerslab.gamesave.LostEvolution
import com.tamerslab.gamesave.NdsStoryProfiles
import com.tamerslab.gamesave.NativeStatParameters
import com.tamerslab.gamesave.ObservedDigimon
import com.tamerslab.gamesave.RecordMembership
import com.tamerslab.gamesave.SaveContainer
import com.tamerslab.gamesave.WorldDsStatConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeTransferConversionTest {
    private val table = LeSpeciesTable.parse(shippedText())
    private val stats = WorldDsStatConversion.AppStats(stage = 2, baseHp = 120, baseBp = 110, baseAp = 130,
        trainingHp = 180, trainingBp = 90, trainingAp = 120, vitalPoints = 4200, mood = 60)

    private fun shippedText(): String {
        val file = java.io.File("src/main/assets/le_species.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/le_species.json")
        assertTrue("le_species.json asset is missing", file.isFile)
        return file.readText()
    }

    @Test fun forwardProjectionStaysInsideTheRecordBands() {
        val native = LeStatConversion.toNative(stats, aptitude = 100)
        assertTrue(native.level in 1..99)
        assertTrue(native.maxHp in 10..9999)
        assertTrue(native.hp == native.maxHp)
        assertTrue(native.maxMp in 0..9999)
        assertTrue(native.attack in 1..999)
        assertTrue(native.defense in 1..999)
        assertTrue(native.spirit in 1..999)
        assertTrue(native.speed in 1..99)
        assertEquals(100, native.aptitude)
        assertEquals(LeStatConversion.levelForVitalPoints(stats.vitalPoints), native.level)
    }

    @Test fun levelMappingRoundTripsAcrossTheWholeRange() {
        for (level in 1..99) {
            val vital = LeStatConversion.vitalPointsForLevel(level)
            assertEquals(level, LeStatConversion.levelForVitalPoints(vital))
        }
    }

    @Test fun nativeStatsRequireTheSingleLostEvolutionExpChannel() {
        val projected = LeStatConversion.toNative(stats, aptitude = 100)
        val record = ObservedDigimon(
            "copy:1:record:0", SaveContainer.PARTY, 1, 77, "Agumon", projected.level, null, 0,
            RecordMembership.ACTIVE_RECORD,
            nativeParameters = NativeStatParameters(projected.hp, projected.maxHp, projected.mp, projected.maxMp,
                projected.attack, projected.defense, projected.spirit, projected.speed, projected.aptitude,
                listOf(7L), 1),
        )
        val native = requireNotNull(LeStatConversion.nativeStats(record))
        assertEquals(projected.level, native.level)
        assertEquals(projected.maxHp, native.maxHp)
        assertEquals(7L, native.exp.single())
        val wrongChannels = record.copy(nativeParameters = NativeStatParameters(projected.hp, projected.maxHp,
            projected.mp, projected.maxMp, projected.attack, projected.defense, projected.spirit, projected.speed,
            projected.aptitude, List(9) { 0L }, 1))
        assertNull(LeStatConversion.nativeStats(wrongChannels))
    }

    @Test fun generatedRecordCarriesTheProjectionAndAppSpeciesIdentity() {
        val state = LeOutboundState(77, "digimon.agumon", "Agumon", "Agumon", stats, table.defaults(77), 100)
        val record = LeOutboundConversion.generate(state, seedBase64 = null)
        assertEquals(77L, record.nativeSpeciesId)
        assertEquals("Agumon", record.storedName)
        assertNull(record.nativeIdentity)
        val bytes = LostEvolution.base64Decode(requireNotNull(record.nativeRecordBase64))
        assertEquals(LostEvolution.RECORD_BYTES, bytes.size)
        assertEquals(77, LostEvolution.species(bytes))
        assertEquals(record.level, LostEvolution.level(bytes))
        assertEquals(0L, LostEvolution.identity(bytes))
        assertEquals(table.defaults(77)!!.skills.first(), LostEvolution.u16(bytes, LostEvolution.OFFSET_SKILLS))
        assertNull(record.nativeIdentity)
    }

    @Test fun seededRecordKeepsItsNativeIdentityAndSpecies() {
        val seed = LostEvolution.buildRecord(77, "Agumon", "Agumon", 10, 500, 500, 300, 300, 200, 150, 180, 40, 100,
            listOf(32, 105))
        LostEvolution.putU64(seed, LostEvolution.OFFSET_IDENTITY, (LostEvolution.IDENTITY_MARKER shl 32) or 42L)
        val state = LeOutboundState(77, "digimon.agumon", "Agumon", "Agumon", stats, table.defaults(77), 100)
        val record = LeOutboundConversion.generate(state, LostEvolution.base64Encode(seed))
        assertEquals(LostEvolution.identityText((LostEvolution.IDENTITY_MARKER shl 32) or 42L), record.nativeIdentity)
        assertEquals(77L, record.nativeSpeciesId)
        val bytes = LostEvolution.base64Decode(requireNotNull(record.nativeRecordBase64))
        assertEquals((LostEvolution.IDENTITY_MARKER shl 32) or 42L, LostEvolution.identity(bytes))
        assertEquals(record.level, LostEvolution.level(bytes))
    }

    @Test fun retainedRecordOnlyCoversLevelsTheAppHasAlreadyReached() {
        val state = LeOutboundState(77, "digimon.agumon", "Agumon", "Agumon",
            stats.copy(vitalPoints = LeStatConversion.vitalPointsForLevel(30)), table.defaults(77), 100)
        fun retainedAt(level: Int): ObservedDigimon {
            val bytes = LostEvolution.buildRecord(77, "Agumon", "Agumon", level, 100, 100, 50, 50, 10, 10, 10, 5, 100,
                emptyList())
            return ObservedDigimon("copy:1:record:0", SaveContainer.PARTY, 1, 77, "Agumon", level, null, 0,
                RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = LostEvolution.base64Encode(bytes),
                nativeParameters = NativeStatParameters(1, 1, 0, 0, 1, 1, 1, 1, 100, listOf(0L), 1))
        }
        assertTrue(LeOutboundConversion.retainedCoversAppLevel(state, retainedAt(30)))
        assertTrue(!LeOutboundConversion.retainedCoversAppLevel(state, retainedAt(29)))
        val app = state.appStats
        val projection = NativeAppProjection("p1", 1L, LeStatConversion.VERSION, "APP_SENT_VALUES", app.stage,
            app.baseHp, app.baseBp, app.baseAp, app.trainingHp, app.trainingAp, app.trainingBp,
            app.vitalPoints, app.wins, app.losses, 0L, 0L, 10000)
        assertTrue(LeOutboundConversion.matchesBaseline(state, projection))
        assertTrue(!LeOutboundConversion.matchesBaseline(state, projection.copy(trainingHp = 1)))
    }

    @Test fun lostEvolutionLauncherIconIsAnAcceptedVerifiedTitle() {
        assertTrue(NdsLauncherIconRepository.supportsTitle(com.tamerslab.navigation.TransferGame.LOST_EVOLUTION))
        assertTrue(NdsLauncherIconRepository.acceptsCode(com.tamerslab.navigation.TransferGame.LOST_EVOLUTION, "BLEJ"))
        assertTrue(NdsLauncherIconRepository.acceptsCode(com.tamerslab.navigation.TransferGame.LOST_EVOLUTION, "BLEV"))
        assertTrue(!NdsLauncherIconRepository.acceptsCode(com.tamerslab.navigation.TransferGame.LOST_EVOLUTION, "ADNE"))
        assertTrue(!NdsLauncherIconRepository.acceptsCode(com.tamerslab.navigation.TransferGame.DAWN, "BLEJ"))
        assertTrue(NdsLauncherIconRepository.supportsTitle(com.tamerslab.navigation.TransferGame.DAWN))
        assertTrue(!NdsLauncherIconRepository.supportsTitle(com.tamerslab.navigation.TransferGame.TIME_STRANGER))
    }

    @Test fun shippedSpeciesTableResolvesTheAbbreviatedNames() {
        val catalogFile = java.io.File("src/main/assets/species_catalog.json").takeIf { it.isFile }
            ?: java.io.File("app/src/main/assets/species_catalog.json")
        val catalog = com.tamerslab.species.CanonicalSpeciesCatalog(
            com.google.gson.Gson().fromJson(catalogFile.reader(),
                com.tamerslab.species.CanonicalSpeciesDatabaseDto::class.java))
        assertEquals("Agumon", table.name(77))
        assertEquals("digimon.agumon", table.canonicalId(77, catalog))
        assertEquals("digimon.kyubimon", table.canonicalId(141, catalog))
        assertEquals("digimon.weregarurumonblack", table.canonicalId(190, catalog))
        assertEquals("digimon.algomonperfect", table.canonicalId(245, catalog))
        assertEquals("digimon.algomonultimate", table.canonicalId(320, catalog))
        assertEquals("digimon.dukemoncrimsonmode", table.canonicalId(347, catalog))
        assertNull(table.name(742))
        assertEquals(1, table.stage(77))
        assertEquals(2, table.stage(120))
        assertEquals(3, table.stage(200))
        assertEquals(4, table.stage(320))
        assertEquals(0, table.stage(60))
        assertNotNull(table.defaults(77))
        assertNull(table.defaults(500))
        assertEquals(NdsStoryProfiles.LOST_EVOLUTION.gameId, table.gameId)
        assertEquals(NdsStoryProfiles.LOST_EVOLUTION.formatProfile, table.formatProfile)
    }
}
