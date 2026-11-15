package com.tamerslab.items.data

import com.tamerslab.hardware.nfc.vb.SpecialMission

/**
 * The four special-mission slots every receivable partner carries, and the inventory items that fill
 * each one. The item icon index is the authored link between an item and its slot.
 */
object SpecialMissionSlots {
    const val STEPS = 0
    const val VITALS = 1
    const val BATTLES = 2
    const val WINS = 3

    val all: List<Int> = listOf(STEPS, VITALS, BATTLES, WINS)

    /** The slot an item fills, or null when the item is not a special-mission item. */
    fun slotForItemIcon(itemIcon: Int): Int? = when (itemIcon) {
        9, 10 -> STEPS
        11, 12 -> VITALS
        13, 14 -> BATTLES
        15, 16 -> WINS
        else -> null
    }

    fun typeForSlot(slot: Int): SpecialMission.Type = when (slot) {
        STEPS -> SpecialMission.Type.STEPS
        VITALS -> SpecialMission.Type.VITALS
        BATTLES -> SpecialMission.Type.BATTLES
        WINS -> SpecialMission.Type.WINS
        else -> SpecialMission.Type.NONE
    }
}
