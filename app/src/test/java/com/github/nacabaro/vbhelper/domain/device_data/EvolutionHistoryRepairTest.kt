package com.github.nacabaro.vbhelper.domain.device_data

import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Entry
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Route
import com.github.nacabaro.vbhelper.domain.device_data.EvolutionHistoryRepair.Species
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class EvolutionHistoryRepairTest {
    // MadDragonicMetal slots: Botamon, Koromon, Agumon Black, Devidramon,
    // Strikedramon, Arresterdramon, Gulus Gammamon; ids intentionally differ from slots.
    private val species = (0..6).map { Species(100L + it, 27, it, minOf(it, 3)) }
    private val routes = listOf(Route(100, 101), Route(101, 102)) + (103L..106L).map { Route(102, it) }
    private fun repair(slot: Int, history: List<Entry> = emptyList()) =
        EvolutionHistoryRepair.repair(species[slot], species, routes, history, 9999)
    private fun ids(result: EvolutionHistoryRepair.Result) = result.entries.map { it.speciesId }

    @Test fun recruitedAdultGetsRealAncestorsBackToBabyI() {
        val result = repair(3, listOf(Entry(103, 500)))
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(result))
        assertTrue(result.complete)
        assertTrue(result.entries.all { it.date == 500L })
    }

    @Test fun degeneratedAgumonCannotKeepGulusOrAnyOtherAdult() {
        val result = repair(2, listOf(Entry(106, 100), Entry(102, 200), Entry(103, 300)))
        assertEquals(listOf(100L, 101L, 102L), ids(result))
    }

    @Test fun sameStageSiblingsAndDuplicateCurrentFormAreRemoved() {
        val result = repair(3, listOf(Entry(106, 100), Entry(103, 200), Entry(104, 300), Entry(103, 400)))
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(result))
        assertEquals(400L, result.entries.last().date)
    }

    @Test fun scrambledLineageIsOrderedWithoutChangingKnownDeviceDates() {
        val recorded = listOf(Entry(103, 100), Entry(101, 900), Entry(102, 200), Entry(100, 1000))
        assertEquals(listOf(recorded[3], recorded[1], recorded[2], recorded[0]), repair(3, recorded).entries)
    }

    @Test fun missingCurrentFormIsAlwaysAppended() {
        assertEquals(listOf(100L, 101L, 102L, 106L), ids(repair(6, listOf(Entry(102, 400)))))
    }

    @Test fun babyIHasOnlyItselfEvenWithCorruptedHistory() {
        assertEquals(listOf(Entry(100, 20)), repair(0, listOf(Entry(106, 10), Entry(100, 20))).entries)
    }

    @Test fun compatibleRecordedBranchWinsOverLowerSlot() {
        val alternate = Species(200, 27, 9, 2)
        val result = EvolutionHistoryRepair.repair(species[3], species + alternate,
            routes + listOf(Route(101, 200), Route(200, 103)), listOf(Entry(200, 300)), 500)
        assertEquals(listOf(100L, 101L, 200L, 103L), ids(result))
    }

    @Test fun searchesPastRecordedDeadEndToFindCompleteRoute() {
        val deadEnd = Species(200, 27, 0, 2)
        val result = EvolutionHistoryRepair.repair(species[3], species + deadEnd,
            routes + Route(200, 103), listOf(Entry(200, 300)), 500)
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(result))
        assertTrue(result.complete)
    }

    @Test fun sameSlotOnAnotherImportedCardCannotBecomeAncestor() {
        val foreign = Species(200, 28, 2, 2)
        val result = EvolutionHistoryRepair.repair(species[3], species + foreign,
            routes + Route(200, 103), listOf(Entry(200, 300)), 500)
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(result))
    }

    @Test fun validStageButUnrelatedSpeciesIsReplaced() {
        val unrelated = Species(200, 27, 7, 2)
        val result = EvolutionHistoryRepair.repair(species[3], species + unrelated,
            routes, listOf(Entry(200, 300)), 500)
        assertFalse(ids(result).contains(200L))
    }

    @Test fun malformedRoutesCannotCreateCyclesOrSkipStages() {
        val result = EvolutionHistoryRepair.repair(species[3], species,
            routes + listOf(Route(103, 102), Route(103, 103), Route(100, 103), Route(104, 103)), emptyList(), 500)
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(result))
    }

    @Test fun incompleteCardKeepsLongestValidSuffixAndReportsMissingBabyI() {
        val result = EvolutionHistoryRepair.repair(species[3], species,
            listOf(Route(102, 103)), listOf(Entry(100, 100), Entry(106, 100)), 500)
        assertEquals(listOf(102L, 103L), ids(result))
        assertFalse(result.complete)
    }

    @Test fun missingEvolutionGraphDoesNotInventAncestors() {
        val result = EvolutionHistoryRepair.repair(species[3], species, emptyList(), emptyList(), 500)
        assertEquals(listOf(Entry(103, 500)), result.entries)
        assertFalse(result.complete)
    }

    @Test fun deterministicResultDoesNotDependOnDatabaseRouteOrder() {
        val alternative = Species(200, 27, 9, 2)
        val graph = routes + listOf(Route(101, 200), Route(200, 103))
        val first = EvolutionHistoryRepair.repair(species[3], species + alternative, graph, emptyList(), 500)
        val reversed = EvolutionHistoryRepair.repair(species[3], (species + alternative).reversed(), graph.reversed(), emptyList(), 500)
        assertEquals(first, reversed)
        assertEquals(listOf(100L, 101L, 102L, 103L), ids(first))
    }

    @Test fun repairIsIdempotentForAllFormsAndRandomCorruptHistories() {
        val random = Random(1234)
        repeat(300) {
            val slot = random.nextInt(species.size)
            val history = List(random.nextInt(20)) { Entry(random.nextLong(98, 110), random.nextLong(1, 1000)) }
            val first = repair(slot, history)
            assertEquals(first, repair(slot, first.entries))
            val stages = first.entries.map { entry -> species.single { it.id == entry.speciesId }.stage }
            assertEquals((0..species[slot].stage).toList(), stages)
            assertEquals(species[slot].id, first.entries.last().speciesId)
            first.entries.zipWithNext().forEach { (from, to) ->
                assertTrue(Route(from.speciesId, to.speciesId) in routes)
            }
        }
    }

    @Test fun repairedHistoryFitsWatchAndSurvivesNfcRoundTrip() {
        val repaired = repair(3, listOf(Entry(106, 0), Entry(103, 0)))
        val wire = NfcEvolutionHistory.pad(repaired.entries.map { entry ->
            NfcEvolutionHistory.transformation(species.single { it.id == entry.speciesId }.charaIndex, entry.date)
        }, 9)
        val returned = wire.filter { it.toCharIndex != UByte.MAX_VALUE }.map {
            Entry(species[it.toCharIndex.toInt()].id, NfcEvolutionHistory.dateToEpochMillis(it)!!)
        }
        assertEquals(repaired, repair(3, returned))
    }
}
