package com.github.nacabaro.vbhelper.screens.transferScreen

import com.github.nacabaro.vbhelper.gamesave.NativeStatParameters
import com.github.nacabaro.vbhelper.gamesave.ObservedDigimon
import com.github.nacabaro.vbhelper.gamesave.RecordIssue
import com.github.nacabaro.vbhelper.gamesave.RecordMembership
import com.github.nacabaro.vbhelper.gamesave.SaveContainer
import com.github.nacabaro.vbhelper.gamesave.SaveObservation
import com.github.nacabaro.vbhelper.gamesave.WorldDsSaveReader
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveCopyCollapseTest {
    private fun record(key: String, identity: String?, block: Int?, active: Boolean,
        issues: List<RecordIssue> = emptyList(), container: SaveContainer = SaveContainer.PARTY) =
        ObservedDigimon(key, container, 1, 4, "Native name", 5, null, 0,
            if (active) RecordMembership.ACTIVE_RECORD else RecordMembership.NEEDS_REVIEW, issues,
            nativeIdentity = identity,
            nativeParameters = block?.let { NativeStatParameters(1, 2, 3, 4, 5, 6, 7, 8, 9, emptyList(), it) })

    private fun observation(records: List<ObservedDigimon>, gameId: String = "other") =
        SaveObservation(gameId, "profile", 1, "revision", records, emptyList())

    @Test fun bothUnreviewedCopiesShowOneEntryPerDigimon() {
        val first = record("copy:1:record:0", "save-local:7", 1, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        val second = record("copy:2:record:0", "save-local:7", 2, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        val other = record("copy:1:record:1", "save-local:8", 1, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        assertEquals(listOf(first, other), collapseSaveCopies(listOf(first, second, other)))
    }

    @Test fun theChosenCopysActiveRecordWinsWhicheverCopyCameFirst() {
        val setAside = record("copy:1:record:0", "save-local:7", 1, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        val chosen = record("copy:2:record:0", "save-local:7", 2, true)
        assertEquals(listOf(chosen), collapseSaveCopies(listOf(setAside, chosen)))
        assertEquals(listOf(chosen), collapseSaveCopies(listOf(chosen, setAside)))
    }

    @Test fun aReviewRecordOutranksTheSetAsideCopySoItsIssuesStayVisible() {
        val flagged = record("copy:1:record:0", "save-local:7", 1, false, listOf(RecordIssue.UNKNOWN_NAME_CODES))
        val setAside = record("copy:2:record:0", "save-local:7", 2, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        assertEquals(listOf(flagged), collapseSaveCopies(listOf(setAside, flagged)))
        assertEquals(listOf(flagged), collapseSaveCopies(listOf(flagged, setAside)))
    }

    @Test fun recordsWithoutACardCopyOrNativeIdentityStayUntouched() {
        val strangerA = record("main:1", null, null, true, listOf(RecordIssue.MISSING_IDENTITY))
        val strangerB = record("main:2", null, null, true)
        assertEquals(listOf(strangerA, strangerB), collapseSaveCopies(listOf(strangerA, strangerB)))
    }

    @Test fun theSameIdentityInDifferentContainersIsNotCollapsedForOtherGames() {
        val party = record("copy:1:record:0", "save-local:7", 1, true, container = SaveContainer.PARTY)
        val farm = record("copy:2:record:5", "save-local:7", 2, true, container = SaveContainer.FARM)
        assertEquals(listOf(party, farm), shownRecords(observation(listOf(party, farm))))
    }

    @Test fun worldDsShowsOneContainerWhenCopiesDisagree() {
        val setAsideParty = record("copy:1:record:3", "save-local:7", 1, false,
            listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK), container = SaveContainer.PARTY)
        val chosenStorage = record("copy:2:record:9", "save-local:7", 2, true,
            container = SaveContainer.UNASSIGNED)
        val view = observation(listOf(setAsideParty, chosenStorage), WorldDsSaveReader.GAME_ID)
        assertEquals(listOf(chosenStorage), shownRecords(view))
        assertEquals(emptyList<ObservedDigimon>(), shownRecords(view, SaveContainer.PARTY))
        assertEquals(listOf(chosenStorage), shownRecords(view, SaveContainer.UNASSIGNED))
    }

    @Test fun shownKeysMatchTheCollapsedList() {
        val first = record("copy:1:record:0", "save-local:7", 1, false, listOf(RecordIssue.AMBIGUOUS_SAVE_BLOCK))
        val second = record("copy:2:record:0", "save-local:7", 2, true)
        val untouched = record("main:3", null, null, true)
        assertEquals(setOf(second.recordKey, untouched.recordKey),
            shownRecordKeys(observation(listOf(first, second, untouched))))
    }
}
