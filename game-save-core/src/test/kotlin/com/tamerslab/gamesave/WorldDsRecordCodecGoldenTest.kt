package com.tamerslab.gamesave

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldDsRecordCodecGoldenTest {
    @Test
    fun fixedWorldDsRecordMatchesThePhase0GoldenFixture() {
        val stats = WorldDsStatConversion.NativeStats(
            level = 20,
            aptitude = 50,
            hp = 100,
            maxHp = 200,
            mp = 50,
            maxMp = 100,
            attack = 150,
            defense = 120,
            spirit = 110,
            speed = 90,
            exp = List(WorldDsStatConversion.EXP_CHANNELS) { 0L },
        )
        val defaults = WorldDsRecordDefaults(
            unk1A = 3,
            unk50 = 2,
            ability = 53,
            skills = listOf(1, 19, 186, 149),
            resistances = listOf(187, 500, 850, 500, 500, 500, 500),
        )

        val generated = WorldDsRecordCodec.generate(
            localId = 7,
            speciesId = 20,
            name = "AGUMON",
            fallbackName = "AGUMON",
            stats = stats,
            defaults = defaults,
        )

        assertEquals(WorldDsRecordCodec.RECORD_BYTES, generated.bytes.size)
        assertEquals(EXPECTED_BASE64, WorldDsRecordCodec.base64(generated.bytes))
    }

    private companion object {
        const val EXPECTED_BASE64 =
            "BwAAABQAAABQAFYAZABcAF4AXQD///////8DAAIAAQAAADUA////////AAAUAAAA////////////////ZAAAAMgAAAAyAGQAlgB4AG4AWgACADIAAQATALoAlQD///////8AAAAAAAAAAAAAAAAAACEAAAAUAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAhgIAABgAAAD/////FAAAAP///////////////wAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAAC7APQBUgP0AfQB9AH0AQAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                "AAAAAAAAAAAAAAAA"
    }
}
