package com.tamerslab.transfer.data.gameSave

import com.tamerslab.domain.personality.DigimonPersonalityType
import com.tamerslab.gamesave.*
import org.junit.Assert.*
import org.junit.Test

class VerifiedSnapshotImportProfileTest {
    private val profile = VerifiedSnapshotImportProfile("fixture", "fixture-v1", 1, "controlled-fixture-id-v1",
        mapOf(16 to DigimonPersonalityType.COMPASSIONATE))
    private val record = ObservedDigimon("main:1", SaveContainer.PARTY, 1, 10, "Name", 42, 16,
        123, RecordMembership.ACTIVE_RECORD, nativeRecordBase64 = "AQID")
    private val observation = SaveObservation("fixture", "fixture-v1", 1, "a".repeat(64), listOf(record),
        emptyList(), SaveCapabilities(canImportSnapshot = true))

    @Test fun explicitNativeNumbersMapWithoutEnumOrdinals() {
        assertEquals(DigimonPersonalityType.COMPASSIONATE, profile.personality(record))
        assertEquals("123", profile.nativeIdentity(record))
    }
    @Test fun gameProfileAndCodecMustAllMatch() {
        assertTrue(profile.matches(observation))
        assertFalse(profile.matches(observation.copy(gameId = "other")))
        assertFalse(profile.matches(observation.copy(formatProfile = "other")))
        assertFalse(profile.matches(observation.copy(codecVersion = 2)))
    }
    @Test fun unmappedPersonalityIsNotRandomized() {
        assertThrows(IllegalArgumentException::class.java) { profile.personality(record.copy(personalityId = 99)) }
    }
    @Test fun zeroIdentityCannotBecomeAPermanentBinding() {
        assertThrows(IllegalArgumentException::class.java) { profile.nativeIdentity(record.copy(identityCandidate = 0)) }
    }
    @Test fun productionSnapshotPolicyDoesNotClaimVerifiedNativeIdentityOrFarmSupport() {
        val policy = GameImportAcquisitionRepository.productionProfiles.single { it.gameId == TimeStrangerSaveReader.GAME_ID }
        assertTrue(policy.provisionalIdentity)
        assertEquals(setOf(SaveContainer.PARTY, SaveContainer.BANK), policy.allowedSnapshotContainers)
        assertEquals(TimeStrangerSaveReader.GAME_ID, policy.gameId)
    }
    @Test fun profileRejectsMissingVerificationMetadata() {
        assertThrows(IllegalArgumentException::class.java) {
            VerifiedSnapshotImportProfile("fixture", "fixture-v1", 1, "", emptyMap())
        }
    }
    @Test fun worldDsAppPersonalityIsAppOnlyStableAndNeverInventedAsNative() {
        val policy = GameImportAcquisitionRepository.productionProfiles.single { it.gameId == WorldDsSaveReader.GAME_ID }
        val native = record.copy(personalityId = null, nativeIdentity = "save-local:7")
        assertTrue(policy.appPersonalityOnly)
        assertFalse(policy.crossSourceIdentityReview)
        assertTrue(policy.supportsPersonality(native))
        assertFalse(policy.supportsPersonality(record))
        assertEquals(policy.initialAppPersonality(native, "individual"), policy.initialAppPersonality(native, "individual"))
        assertNull(native.personalityId)
        assertTrue(policy.provisionalIdentity)
        assertEquals(setOf(SaveContainer.PARTY), policy.allowedSnapshotContainers)
        assertEquals(1..99, policy.nativeLevelRange)
    }
}
