package com.github.nacabaro.vbhelper.domain.device_data

import com.github.cfogrady.vbnfc.data.NfcCharacter
import com.github.nacabaro.vbhelper.domain.card.CardCharacter
import com.github.nacabaro.vbhelper.transfer.ExportFormat
import com.github.nacabaro.vbhelper.utils.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterCapabilitiesTest {
    private fun catalogDefinition(id: Long = 100, speciesId: String = "digimon.garurumon") = CardCharacter(
        id = id, cardId = null, spriteId = -1, charaIndex = 0, stage = 3,
        attribute = NfcCharacter.Attribute.Vaccine, baseHp = 1, baseBp = 1, baseAp = 1,
        nameSprite = byteArrayOf(), nameWidth = 1, nameHeight = 1,
        speciesId = speciesId, catalogSpeciesId = speciesId)

    private fun slot(id: Long = 200, cardId: Long = 7, speciesId: String = "digimon.garurumon") = CardCharacter(
        id = id, cardId = cardId, spriteId = 5, charaIndex = 1, stage = 3,
        attribute = NfcCharacter.Attribute.Vaccine, baseHp = 1, baseBp = 1, baseAp = 1,
        nameSprite = byteArrayOf(), nameWidth = 1, nameHeight = 1, speciesId = speciesId)

    private fun userCharacter(charId: Long, type: DeviceType = DeviceType.APP, exportId: Long? = null) = UserCharacter(
        id = 1, individualId = "ind-1", charId = charId, ageInDays = 0, mood = 80, vitalPoints = 0,
        transformationCountdown = 0, injuryStatus = NfcCharacter.InjuryStatus.None, trophies = 0,
        currentPhaseBattlesWon = 0, currentPhaseBattlesLost = 0, totalBattlesWon = 0, totalBattlesLost = 0,
        activityLevel = 0, heartRateCurrent = 0, characterType = type, isActive = false, exportCardCharacterId = exportId)

    @Test fun catalogPartnerNeedsAssociationUntilARealSlotIsChosen() {
        val catalog = CharacterCapabilities(definition = catalogDefinition())
        assertTrue(catalog.isCatalogPartner)
        assertTrue(catalog.needsAssociation)
        assertFalse(catalog.hasPhysicalSlot)
        assertNull(catalog.transferFormat)
    }

    @Test fun convertedCatalogPartnerKeepsItsFamilyButExportsThroughTheSlot() {
        val capabilities = CharacterCapabilities(
            definition = catalogDefinition(),
            exportTarget = slot(cardId = 7),
            exportCardIsBem = false,
        )
        assertTrue(capabilities.isCatalogPartner)
        assertFalse(capabilities.needsAssociation)
        assertTrue(capabilities.hasPhysicalSlot)
        assertEquals(200L, capabilities.slotId)
        assertEquals(ExportFormat.VB, capabilities.transferFormat)
    }

    @Test fun bemExportTargetProducesBeFormat() {
        val capabilities = CharacterCapabilities(
            definition = catalogDefinition(),
            exportTarget = slot(cardId = 9),
            exportCardIsBem = true,
        )
        assertEquals(ExportFormat.BE, capabilities.transferFormat)
    }

    @Test fun deviceCharacterExportsThroughItsOwnSlotWithoutAssociation() {
        val capabilities = CharacterCapabilities(
            definition = slot(cardId = 3),
            definitionCardIsBem = true,
        )
        assertFalse(capabilities.isCatalogPartner)
        assertFalse(capabilities.needsAssociation)
        assertEquals(ExportFormat.BE, capabilities.transferFormat)
        assertEquals(200L, capabilities.slotId)
    }

    @Test fun missingDefinitionYieldsNoSlotAndNoAssociationDemand() {
        val capabilities = CharacterCapabilities(definition = null)
        assertTrue(capabilities.needsAssociation)
        assertFalse(capabilities.hasPhysicalSlot)
        assertNull(capabilities.transferFormat)
    }

    @Test fun uiNeedsAssociationMatchesCatalogWithoutExport() {
        assertTrue(CharacterCapabilities.needsAssociation(catalogDefinition(), null))
        assertFalse(CharacterCapabilities.needsAssociation(catalogDefinition(), 200L))
        assertFalse(CharacterCapabilities.needsAssociation(slot(), null))
    }

    @Test fun representativePrefersAppPartnerThenFallsBackToTheSingleDeviceRow() {
        val app = userCharacter(charId = 100, type = DeviceType.APP)
        val device = userCharacter(charId = 200, type = DeviceType.VBDevice)
        assertEquals(app, IndividualCharacters.representative(listOf(device, app)))
        assertEquals(device, IndividualCharacters.representative(listOf(device)))
        assertNull(IndividualCharacters.representative(emptyList()))
    }
}
