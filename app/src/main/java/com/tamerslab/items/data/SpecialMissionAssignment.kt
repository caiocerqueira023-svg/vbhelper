package com.tamerslab.items.data

import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.items.ItemType
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.hardware.nfc.vb.SpecialMission

/**
 * Decides which of a partner's mission rows an inventory item fills.
 *
 * The device-side `watchId` is never slot identity: it is 0..3 only for app partners seeded by the
 * app factory / `app_state_initial` trigger, watch-imported partners keep the watch mission id and
 * scan/world/migration-created partners carry `(characterId * 4 + slot) % 65535`. The slot of a
 * mission is instead its position among the partner's four mission rows.
 *
 * Every creator inserts the rows in slot order 0..3 (`AppPartnerFactory`, the `app_state_initial`
 * trigger, scan, world recruitment, the NFC importer and migration 12->13), so the auto-generated
 * row ids increase in slot order and `ORDER BY id` (also applied by
 * `UserCharacterDao.getSpecialMissions`) lists the rows by slot.
 */
object SpecialMissionAssignment {
    val slotCount: Int = SpecialMissionSlots.all.size

    /** The partner's mission rows in slot order (ascending row id). */
    fun orderedMissions(missions: List<SpecialMissions>): List<SpecialMissions> =
        missions.sortedBy { it.id }

    /** The mission row that occupies [slot], or null when the partner has no row there. */
    fun missionForSlot(missions: List<SpecialMissions>, slot: Int): SpecialMissions? =
        if (isSlot(slot)) orderedMissions(missions).getOrNull(slot) else null

    /** The slot of the row with [missionId], or null when that is not one of the partner's rows. */
    fun slotOfMissionId(missions: List<SpecialMissions>, missionId: Long): Int? {
        val index = orderedMissions(missions).indexOfFirst { it.id == missionId }
        return if (isSlot(index)) index else null
    }

    /** The watch mission type carried by [slot]. */
    fun expectedTypeForSlot(slot: Int): SpecialMission.Type = SpecialMissionSlots.typeForSlot(slot)

    /** The slot an item fills, or null for ordinary items. */
    fun slotForItemIcon(itemIcon: Int): Int? = SpecialMissionSlots.slotForItemIcon(itemIcon)

    /** Owned items that can fill [slot]. */
    fun candidatesForSlot(
        items: List<ItemDtos.ItemsWithQuantities>,
        slot: Int,
    ): List<ItemDtos.ItemsWithQuantities> {
        if (!isSlot(slot)) return emptyList()
        return items.filter { item ->
            item.itemType == ItemType.SPECIALMISSION && slotForItemIcon(item.itemIcon) == slot
        }
    }

    /**
     * Resolves the mission row an inventory item must update.
     *
     * [missionId] is the row the user tapped on the partner overview; when it is null the item flow
     * applies and the item's kind selects its positional slot. An explicit row must still sit in
     * the slot that kind fills, so a stale or mismatched id can never overwrite another mission.
     * Returns null when no such row exists (e.g. the partner has fewer than four mission rows).
     */
    fun resolveTarget(
        missions: List<SpecialMissions>,
        itemIcon: Int,
        missionId: Long? = null,
    ): SpecialMissions? {
        val itemSlot = slotForItemIcon(itemIcon) ?: return null
        val ordered = orderedMissions(missions)
        val targetIndex = if (missionId != null) {
            ordered.indexOfFirst { it.id == missionId }
        } else {
            itemSlot
        }
        if (targetIndex != itemSlot) return null
        return ordered.getOrNull(targetIndex)
    }

    private fun isSlot(slot: Int): Boolean = slot in SpecialMissionSlots.all
}
