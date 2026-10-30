package com.github.nacabaro.vbhelper.gamesave

import org.junit.Assert.*
import org.junit.Test

/**
 * The NDS Story family layer must be behavior-preserving for the World DS profile: the generic
 * reader, inspector, record codec, copy selection and discovery answer exactly as the World DS
 * facades did, and profile resolution never spills one layout's constants into another.
 */
class NdsStoryProfileTest {
    private fun put(bytes: ByteArray, at: Int, value: Long, width: Int = 4) {
        repeat(width) { bytes[at + it] = (value ushr (it * 8)).toByte() }
    }

    /** Synthetic fixture shaped by the supplied DraStic observation, not private save bytes. */
    private fun fixture(): ByteArray = ByteArray(0x10000) { 0xFF.toByte() }.also { bytes ->
        bytes.fill(0, 0, 0x7DC0)
        repeat(16) { bytes[it] = (it + 1).toByte() }
        put(bytes, 0x208, 10); put(bytes, 0x20C, 7)
        val at = 0x234
        put(bytes, at, 7); put(bytes, at + 4, 20)
        listOf(0x63, 0x6E, 0x7C, 0x7D, 0xFFFF).forEachIndexed { i, code -> put(bytes, at + 8 + i * 2, code.toLong(), 2) }
        put(bytes, at + 0x3C, 100); put(bytes, at + 0x40, 110)
        put(bytes, at + 0x44, 20, 2); put(bytes, at + 0x46, 25, 2)
        for (offset in listOf(0x48, 0x4A, 0x4C, 0x4E)) put(bytes, at + offset, 30, 2)
        put(bytes, at + 0x52, 25, 2); put(bytes, at + 0x74, 3, 2)
        repeat(8) { put(bytes, at + 0x78 + it * 4, (it * 11).toLong()) }
    }

    @Test fun genericReaderIsByteIdenticalToTheWorldDsFacade() {
        val bytes = fixture()
        val original = bytes.copyOf()
        val facade = WorldDsSaveReader().read(bytes)
        val generic = NdsStorySaveReader(NdsStoryProfiles.WORLD_DS).read(bytes)
        assertEquals(facade, generic)
        assertArrayEquals(original, bytes)
    }

    @Test fun genericInspectorMatchesTheWorldDsInspector() {
        val bytes = fixture()
        "DSDIGI\u0000\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x10)
        assertEquals(WorldDsResearchInspector.inspect(bytes), WorldDsResearchInspector.inspect(bytes, NdsStoryProfiles.WORLD_DS))
        assertEquals("ADNE", WorldDsResearchInspector.inspect(bytes).layoutEvidence)
    }

    @Test fun profileResolutionIsTripleBasedAndRejectsForeignInput() {
        assertEquals(NdsStoryProfiles.WORLD_DS,
            NdsStoryProfiles.forTriple(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1))
        assertNull(NdsStoryProfiles.forTriple(WorldDsSaveReader.GAME_ID, "other-profile", 1))
        assertNull(NdsStoryProfiles.forTriple("other-game", WorldDsSaveReader.PROFILE, 1))
        assertNull(NdsStoryProfiles.forTriple(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 2))
        assertEquals(NdsStoryProfiles.WORLD_DS, NdsStoryProfiles.forGameId(WorldDsSaveReader.GAME_ID))
        assertNull(NdsStoryProfiles.forGameId("other-game"))
    }

    @Test fun copyIntegrityPolicySealsAndDetectsADriftedCopy() {
        val bytes = fixture()
        NdsStoryCopyIntegrity.seal(bytes, 0, NdsStoryProfiles.WORLD_DS)
        assertTrue(NdsStoryCopyIntegrity.isValid(bytes, 0, NdsStoryProfiles.WORLD_DS))
        bytes[0x300] = (bytes[0x300].toInt() xor 1).toByte()
        assertFalse(NdsStoryCopyIntegrity.isValid(bytes, 0, NdsStoryProfiles.WORLD_DS))
        assertFalse(NdsStoryCopyIntegrity.isValid(bytes, 0x7E00, NdsStoryProfiles.WORLD_DS))
    }

    @Test fun genericCopySelectionMatchesTheWorldDsFacade() {
        val bytes = fixture()
        bytes.copyOfRange(0, WorldDsSaveReader.COPY_STRIDE).copyInto(bytes, WorldDsSaveReader.COPY_STRIDE)
        put(bytes, WorldDsSaveReader.COPY_STRIDE + 0x18, 11)
        NdsStoryCopyIntegrity.seal(bytes, 0, NdsStoryProfiles.WORLD_DS)
        NdsStoryCopyIntegrity.seal(bytes, WorldDsSaveReader.COPY_STRIDE, NdsStoryProfiles.WORLD_DS)
        val observation = WorldDsSaveReader().read(bytes)
        assertEquals(WorldDsCopySelection.latestCopy(observation), NdsStoryCopySelection.latestCopy(observation))
        assertEquals(WorldDsCopySelection.authoritativeCopy(observation), NdsStoryCopySelection.authoritativeCopy(observation))
        assertEquals(WorldDsCopySelection.applyAutomatic(observation), NdsStoryCopySelection.applyAutomatic(observation))
    }

    @Test fun genericRecordCodecMatchesTheWorldDsFacade() {
        val stats = WorldDsStatConversion.toNative(WorldDsStatConversion.AppStats(stage = 2, baseHp = 100, baseBp = 100, baseAp = 100))
        val defaults = WorldDsRecordDefaults(unk1A = 7, unk50 = 2, ability = 16,
            skills = listOf(4, 9, 16, 32), resistances = listOf(500, 500, 500, 500, 500, 500, 500), historyUnk50 = 9)
        val facade = WorldDsRecordCodec.generate(7, 166, "GARUDAMON", "GARUDAMON", stats, defaults = defaults)
        val codec = NdsStoryRecordCodec(NdsStoryProfiles.WORLD_DS)
        val generic = codec.generate(7, 166, "GARUDAMON", "GARUDAMON", stats, defaults = defaults)
        assertArrayEquals(facade.bytes, generic.bytes)
        assertEquals(facade.name, generic.name)
        assertEquals(facade.maxLevel, generic.maxLevel)
        assertEquals(WorldDsRecordCodec.previousSpeciesIds(facade.bytes), codec.previousSpeciesIds(generic.bytes))
        assertEquals(WorldDsRecordCodec.needsSpeciesRepair(facade.bytes), codec.needsSpeciesRepair(generic.bytes))
    }

    @Test fun editorAndConversionPolicyAreResolvedPerLayout() {
        assertEquals(Ds1SaveEditor, NdsStorySaveEditors.forProfile(NdsStoryProfiles.WORLD_DS))
        assertNull(NdsStorySaveEditors.forProfile(NdsStoryProfiles.WORLD_DS.copy(gameId = "other-game")))
        assertEquals("world-ds-stat-v1", NdsStoryConversionPolicies.forProfile(NdsStoryProfiles.WORLD_DS)?.policyId)
        assertEquals(NdsStoryProfiles.WORLD_DS.layoutId,
            NdsStoryConversionPolicies.forProfile(NdsStoryProfiles.WORLD_DS)?.layoutId)
        assertNull(NdsStoryConversionPolicies.forProfile(NdsStoryProfiles.WORLD_DS.copy(layoutId = "other-layout")))
    }

    @Test fun discoveryRangesComeFromRegisteredProfiles() {
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", WorldDsSaveReader.FILE_BYTES.toLong()))
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", WorldDsSaveReader.SAVE_BYTES.toLong()))
        assertTrue(SaveDiscoveryPolicy.isCandidate("anything at all", (WorldDsSaveReader.SAVE_BYTES + 0x400).toLong()))
        assertFalse(SaveDiscoveryPolicy.isCandidate("anything at all", 12345L))
    }

    @Test fun facadeConstantsStayInSyncWithTheProfile() {
        assertEquals(WorldDsSaveReader.SAVE_BYTES, NdsStoryProfiles.WORLD_DS.saveBytes)
        assertEquals(WorldDsSaveReader.COPY_STRIDE, NdsStoryProfiles.WORLD_DS.copyStride)
        assertEquals(WorldDsSaveReader.FILE_BYTES, NdsStoryProfiles.WORLD_DS.fileBytes)
        assertEquals(WorldDsSaveReader.SPECIES_COUNT, NdsStoryProfiles.WORLD_DS.speciesCount)
        assertEquals(WorldDsRecordCodec.RECORD_BYTES, NdsStoryProfiles.WORLD_DS.record.bytes)
        assertEquals(WorldDsPartyEdits.PARTY_CAP, NdsStoryProfiles.WORLD_DS.partyVisibleCap)
    }
}
