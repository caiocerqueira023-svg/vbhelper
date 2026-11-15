package com.tamerslab.items.data

import com.tamerslab.hardware.nfc.vb.SpecialMission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpecialMissionSlotsTest {
    @Test
    fun eachMissionItemIconFillsOneSlot() {
        assertEquals(SpecialMissionSlots.STEPS, SpecialMissionSlots.slotForItemIcon(9))
        assertEquals(SpecialMissionSlots.STEPS, SpecialMissionSlots.slotForItemIcon(10))
        assertEquals(SpecialMissionSlots.VITALS, SpecialMissionSlots.slotForItemIcon(11))
        assertEquals(SpecialMissionSlots.VITALS, SpecialMissionSlots.slotForItemIcon(12))
        assertEquals(SpecialMissionSlots.BATTLES, SpecialMissionSlots.slotForItemIcon(13))
        assertEquals(SpecialMissionSlots.BATTLES, SpecialMissionSlots.slotForItemIcon(14))
        assertEquals(SpecialMissionSlots.WINS, SpecialMissionSlots.slotForItemIcon(15))
        assertEquals(SpecialMissionSlots.WINS, SpecialMissionSlots.slotForItemIcon(16))
    }

    @Test
    fun ordinaryItemsDoNotFillAMissionSlot() {
        for (icon in listOf(1, 2, 3, 4, 5, 6, 7, 8, 0, 17, -1)) {
            assertNull("icon $icon must not claim a mission slot", SpecialMissionSlots.slotForItemIcon(icon))
        }
    }

    @Test
    fun slotsMapToTheWatchMissionTypes() {
        assertEquals(SpecialMission.Type.STEPS, SpecialMissionSlots.typeForSlot(SpecialMissionSlots.STEPS))
        assertEquals(SpecialMission.Type.VITALS, SpecialMissionSlots.typeForSlot(SpecialMissionSlots.VITALS))
        assertEquals(SpecialMission.Type.BATTLES, SpecialMissionSlots.typeForSlot(SpecialMissionSlots.BATTLES))
        assertEquals(SpecialMission.Type.WINS, SpecialMissionSlots.typeForSlot(SpecialMissionSlots.WINS))
        assertEquals(SpecialMission.Type.NONE, SpecialMissionSlots.typeForSlot(99))
    }
}
