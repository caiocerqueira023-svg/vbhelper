package com.github.nacabaro.vbhelper.domain.device_data

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.cfogrady.vbnfc.vb.VBNfcCharacter
import com.github.nacabaro.vbhelper.domain.identity.WatchTransfer
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

class NfcEvolutionHistoryTest {
    @Test fun repeatedWatchRoundTripsDoNotMoveDatesIntoFollowingMonths() {
        for (date in listOf("2026-01-31", "2024-02-29", "2026-09-12", "2026-12-31")) {
            val original = NfcEvolutionHistory.transformation(3, epoch(date))
            var returned = original
            repeat(10) {
                returned = NfcEvolutionHistory.transformation(3, NfcEvolutionHistory.dateToEpochMillis(returned)!!)
            }
            assertEquals(original, returned)
            assertEquals(epoch(date), NfcEvolutionHistory.dateToEpochMillis(returned))
        }
    }

    @Test fun phoneTimezoneDoesNotShiftWatchDates() {
        val previous = TimeZone.getDefault()
        try {
            val event = NfcCharacter.Transformation(3u, 2026u, 9u, 12u)
            for (zone in listOf("America/Bahia", "Asia/Tokyo", "Pacific/Honolulu")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(epoch("2026-09-12"), NfcEvolutionHistory.dateToEpochMillis(event))
            }
        } finally {
            TimeZone.setDefault(previous)
        }
    }

    @Test fun invalidDateIsNotSilentlyRolledIntoAnotherMonth() {
        assertNull(NfcEvolutionHistory.dateToEpochMillis(NfcCharacter.Transformation(3u, 2026u, 2u, 31u)))
        assertNull(NfcEvolutionHistory.dateToEpochMillis(NfcCharacter.Transformation(3u, 2026u, 0u, 0u)))
    }

    @Test fun vbHistoryWithEightEntriesStillHasNineWireSlots() {
        val history = (0..7).map { NfcEvolutionHistory.transformation(it, epoch("2026-09-12")) }
        val padded = NfcEvolutionHistory.pad(history, 9)
        assertEquals(9, padded.size)
        assertEquals(history, padded.take(8))
        assertEquals(UByte.MAX_VALUE, padded.last().toCharIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun oversizedHistoryIsRejectedInsteadOfSilentlyTruncated() {
        NfcEvolutionHistory.pad(List(9) { NfcEvolutionHistory.transformation(it, epoch("2026-09-12")) }, 8)
    }

    @Test fun recruitedLineageKeepsOrderAndIdentityDespiteOlderWatchClock() {
        // Synthetic custom-DIM indices: Agumon Black=2, Gulus=3.
        val agumon = NfcEvolutionHistory.transformation(2, epoch("2026-09-12"))
        val gulus = NfcEvolutionHistory.transformation(3, epoch("2021-01-01"))
        val watch = VBNfcCharacter(dimId = 1u, charIndex = 3u, transformationHistory = NfcEvolutionHistory.pad(listOf(agumon, gulus), 9))
        val receipt = WatchTransfer.capture("token", "1stmaru", watch)
        val importedInWireOrder = watch.transformationHistory.filter { it.toCharIndex != UByte.MAX_VALUE }
            .map { it.toCharIndex.toInt() to NfcEvolutionHistory.dateToEpochMillis(it)!! }
        watch.transformationHistory = NfcEvolutionHistory.pad(importedInWireOrder.map { (index, time) ->
            NfcEvolutionHistory.transformation(index, time)
        }, 9)
        assertEquals(listOf(agumon, gulus), watch.transformationHistory.take(2))
        assertTrue(receipt.matches(watch))
    }

    private fun epoch(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
