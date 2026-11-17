package com.tamerslab.transfer.vpet

import com.tamerslab.domain.vpet.VpetPendingRead
import com.tamerslab.domain.vpet.VpetPendingReason
import com.tamerslab.domain.vpet.VpetReadSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpetPendingReadCodecTest {

    private fun snapshot(
        modelKey: String = "dm20",
        modelLabel: String = "Digital Monster Ver.20th",
        version: Int = 2,
        index: Int = 7,
        name: String? = "Greymon",
        stage: Int? = 3,
        attribute: Int? = 0,
        power: Int? = 42,
        payloadHex: String = "0A0B0C",
        valid: Boolean = true,
        readAtMillis: Long = 1_700_000_000_000L,
    ) = VpetReadSnapshot(
        modelKey = modelKey,
        modelLabel = modelLabel,
        version = version,
        index = index,
        name = name,
        stage = stage,
        attribute = attribute,
        power = power,
        payloadHex = payloadHex,
        valid = valid,
        readAtMillis = readAtMillis,
    )

    @Test
    fun encodeDecodeRoundTripKeepsEveryPendingReadField() {
        val reads = listOf(
            VpetPendingRead("id-unknown", snapshot(), VpetPendingReason.UNKNOWN_SPECIES, "Greymon"),
            VpetPendingRead(
                id = "id-blocked",
                snapshot = snapshot(
                    modelKey = "penc",
                    modelLabel = "Pendulum Color",
                    version = 0,
                    index = 0,
                    name = null,
                    stage = null,
                    attribute = null,
                    power = null,
                    payloadHex = "",
                    valid = false,
                    readAtMillis = 1_700_000_000_123L,
                ),
                reason = VpetPendingReason.PARTICIPATION_BLOCKED,
                blockedSpeciesName = "digimon.greymon",
            ),
        )
        assertEquals(reads, VpetPendingReadCodec.decode(VpetPendingReadCodec.encode(reads)))
    }

    @Test
    fun emptyListRoundTripsToEmptyList() {
        assertEquals(
            emptyList<VpetPendingRead>(),
            VpetPendingReadCodec.decode(VpetPendingReadCodec.encode(emptyList())),
        )
    }

    @Test
    fun missingOrBlankValueDecodesToEmptyList() {
        assertTrue(VpetPendingReadCodec.decode(null).isEmpty())
        assertTrue(VpetPendingReadCodec.decode("").isEmpty())
        assertTrue(VpetPendingReadCodec.decode("   ").isEmpty())
    }

    @Test
    fun corruptJsonDecodesToEmptyListInsteadOfThrowing() {
        assertTrue(VpetPendingReadCodec.decode("{not json").isEmpty())
        assertTrue(VpetPendingReadCodec.decode("""{"id":"an object, not a list"}""").isEmpty())
    }

    @Test
    fun malformedEntryIsDroppedWithoutLosingValidEntries() {
        val raw = """
            [
              {"schemaVersion":1,"id":"keep","modelKey":"dm20","modelLabel":"DM20","version":1,
               "index":3,"name":"Agumon","stage":2,"attribute":0,"power":10,"payloadHex":"AA",
               "valid":true,"readAtMillis":1000,"reason":"UNKNOWN_SPECIES","blockedSpeciesName":"Agumon"},
              "not a pending read"
            ]
        """.trimIndent()
        val decoded = VpetPendingReadCodec.decode(raw)
        assertEquals(listOf("keep"), decoded.map { it.id })
        assertEquals("Agumon", decoded.single().snapshot.name)
    }

    @Test
    fun unknownPendingReasonIsDroppedSoAnOlderBuildKeepsWorking() {
        val raw = """
            [
              {"id":"old","reason":"FUTURE_REASON","blockedSpeciesName":"Mysterymon"},
              {"id":"known","reason":"UNKNOWN_SPECIES","blockedSpeciesName":"Mysterymon"}
            ]
        """.trimIndent()
        val decoded = VpetPendingReadCodec.decode(raw)
        assertEquals(listOf("known"), decoded.map { it.id })
        assertEquals(VpetPendingReason.UNKNOWN_SPECIES, decoded.single().reason)
    }

    @Test
    fun unknownJsonFieldsAreIgnored() {
        val raw = """
            [{"id":"row","reason":"UNKNOWN_SPECIES","blockedSpeciesName":"Mysterymon",
              "futureField":{"nested":1}}]
        """.trimIndent()
        val decoded = VpetPendingReadCodec.decode(raw)
        assertEquals("row", decoded.single().id)
        assertEquals("Mysterymon", decoded.single().blockedSpeciesName)
    }
}
