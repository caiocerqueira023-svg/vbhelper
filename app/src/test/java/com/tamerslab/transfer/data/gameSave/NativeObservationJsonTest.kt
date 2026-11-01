package com.tamerslab.transfer.data.gameSave

import com.tamerslab.gamesave.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class NativeObservationJsonTest {
    private val gson = Gson()

    @Test fun cachedWorldDsObservationRetainsNativeParametersAndAbsentFields() {
        val record = ObservedDigimon("copy:1:record:0", SaveContainer.PARTY, 1, 20, "Test", 3,
            null, 0, RecordMembership.ACTIVE_RECORD, totalExp = null, talentRaw = null, bondRaw = null,
            nativeRecordBase64 = "AQID", nativeIdentity = "save-local:7",
            nativeParameters = NativeStatParameters(100, 110, 20, 25, 30, 30, 30, 30, 25,
                listOf(0L, 11L, 22L, 33L, 44L, 55L, 66L, 77L), 1, 11))
        val observation = SaveObservation(WorldDsSaveReader.GAME_ID, WorldDsSaveReader.PROFILE, 1,
            "a".repeat(64), listOf(record), listOf(ObservationNotice.SAVE_INTEGRITY_UNVERIFIED),
            SaveCapabilities(), mapOf(1 to true))
        assertEquals(observation, gson.fromJson(gson.toJson(observation), SaveObservation::class.java))
        val roundTrip = gson.fromJson(gson.toJson(record), ObservedDigimon::class.java)
        assertNull(roundTrip.personalityId)
        assertNull(roundTrip.totalExp)
        assertEquals(record.nativeParameters, roundTrip.nativeParameters)
    }

    @Test fun previousTimeStrangerJsonStillReadsWithNoNewParameterObject() {
        val legacy = """{"recordKey":"main:1","container":"PARTY","position":1,"nativeSpeciesId":20,"storedName":"Test","level":3,"personalityId":16,"identityCandidate":7,"membership":"ACTIVE_RECORD","issues":[],"totalExp":100,"talentRaw":2,"bondRaw":3.0,"previousSpeciesIds":[]}"""
        val record = gson.fromJson(legacy, ObservedDigimon::class.java)
        assertEquals(16, record.personalityId)
        assertEquals(100L, record.totalExp)
        assertNull(record.nativeParameters)
        assertEquals("Test", record.storedName)
    }
}
