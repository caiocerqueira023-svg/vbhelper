package com.tamerslab.items.data

import com.tamerslab.R
import com.tamerslab.domain.device_data.SpecialMissions
import com.tamerslab.domain.items.ItemType
import com.tamerslab.dtos.ItemDtos
import com.tamerslab.hardware.nfc.vb.SpecialMission
import com.tamerslab.home.ui.dialogs.specialMissionSlotLabelRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Slot identity for special missions must be the row position, never [SpecialMissions.watchId].
 *
 * The watch ids below are deliberately out of range (they are real watch mission ids / the
 * `(characterId * 4 + slot) % 65535` scheme used by scan, world and migration seeding) and one of
 * them collides with an unrelated slot number, which is exactly how the old `watchId`-as-slot code
 * opened the wrong mission kind and overwrote the wrong row.
 */
class SpecialMissionAssignmentTest {
    private val characterId = 7L
    private val collidingWatchIds = listOf(43157, 2, 9, 65530)

    private fun row(
        slot: Int,
        watchId: Int,
        rowId: Long = 101L + slot,
        type: SpecialMission.Type = SpecialMissionSlots.typeForSlot(slot),
        goal: Int = 100,
    ) = SpecialMissions(
        id = rowId,
        characterId = characterId,
        goal = goal,
        watchId = watchId,
        progress = 0,
        status = SpecialMission.Status.UNAVAILABLE,
        timeElapsedInMinutes = 0,
        timeLimitInMinutes = 0,
        missionType = type,
    )

    private fun rows(watchIds: List<Int> = collidingWatchIds): List<SpecialMissions> =
        watchIds.mapIndexed { slot, watchId -> row(slot, watchId) }

    private fun item(
        rowId: Long,
        itemIcon: Int,
        type: ItemType = ItemType.SPECIALMISSION,
    ) = ItemDtos.ItemsWithQuantities(
        id = rowId,
        name = "item $rowId",
        description = "",
        itemIcon = itemIcon,
        itemLength = 60,
        price = 100,
        quantity = 1,
        itemType = type,
    )

    @Test
    fun slotIsTheRowPositionNotTheWatchId() {
        val missions = rows()

        // The rows carry arbitrary watch ids; their positions are the slot identity.
        assertEquals(collidingWatchIds, missions.map { it.watchId })
        for (slot in 0..3) {
            assertEquals(101L + slot, SpecialMissionAssignment.missionForSlot(missions, slot)?.id)
            assertEquals(slot, SpecialMissionAssignment.slotOfMissionId(missions, 101L + slot))
            assertEquals(
                SpecialMissionSlots.typeForSlot(slot),
                SpecialMissionAssignment.expectedTypeForSlot(slot),
            )
        }
        // watchId 2 belongs to the row in slot 1, so it must not be read as slot 2.
        assertEquals(102L, SpecialMissionAssignment.missionForSlot(missions, 1)?.id)
        assertEquals(2, SpecialMissionAssignment.missionForSlot(missions, 1)?.watchId)
        assertEquals(103L, SpecialMissionAssignment.missionForSlot(missions, 2)?.id)
        assertNull(SpecialMissionAssignment.missionForSlot(missions, 4))
        assertNull(SpecialMissionAssignment.missionForSlot(missions, -1))
    }

    @Test
    fun orderedMissionsNormalizesAnUnorderedList() {
        val missions = rows().reversed()

        assertEquals(
            listOf(101L, 102L, 103L, 104L),
            SpecialMissionAssignment.orderedMissions(missions).map { it.id },
        )
        assertEquals(43157, SpecialMissionAssignment.missionForSlot(missions, 0)?.watchId)
    }

    @Test
    fun slotOneOffersVitalsAndTargetsTheSecondRow() {
        val missions = rows()
        val inventory = listOf(
            item(1, itemIcon = 9),
            item(2, itemIcon = 10),
            item(3, itemIcon = 11),
            item(4, itemIcon = 12),
            item(5, itemIcon = 13),
            item(6, itemIcon = 15),
        )

        val candidates = SpecialMissionAssignment.candidatesForSlot(
            inventory,
            SpecialMissionSlots.VITALS,
        )
        assertEquals(listOf(3L, 4L), candidates.map { it.id })
        assertTrue(candidates.all {
            SpecialMissionAssignment.slotForItemIcon(it.itemIcon) == SpecialMissionSlots.VITALS
        })

        val target = SpecialMissionAssignment.resolveTarget(
            missions,
            itemIcon = 11,
            missionId = 102L,
        )
        assertEquals(102L, target?.id)
        // The second row happens to carry watchId 2, which must not be read as slot 2.
        assertEquals(2, target?.watchId)
    }

    @Test
    fun assigningUpdatesOnlyTheTappedRowAndKeepsItsIdentity() {
        val missions = rows()
        val target = requireNotNull(
            SpecialMissionAssignment.resolveTarget(missions, itemIcon = 11, missionId = 102L),
        )

        // Mirrors GameplayRepository.applySpecialMission: upsert of the resolved row only.
        val updated = missions.map { current ->
            if (current.id == target.id) {
                current.copy(
                    goal = 250,
                    progress = 0,
                    status = SpecialMission.Status.AVAILABLE,
                    timeElapsedInMinutes = 0,
                    timeLimitInMinutes = 60,
                    missionType = SpecialMission.Type.VITALS,
                )
            } else {
                current
            }
        }

        assertEquals(missions[0], updated[0])
        assertEquals(missions[2], updated[2])
        assertEquals(missions[3], updated[3])
        assertEquals(102L, updated[1].id)
        assertEquals(characterId, updated[1].characterId)
        assertEquals(missions[1].watchId, updated[1].watchId)
        assertEquals(SpecialMission.Type.VITALS, updated[1].missionType)
        assertEquals(250, updated[1].goal)
    }

    @Test
    fun explicitMissionIdMustBelongToTheItemKindSlot() {
        val missions = rows()

        // A battles item may not overwrite the wins row, no matter which id it is given.
        assertNull(SpecialMissionAssignment.resolveTarget(missions, itemIcon = 13, missionId = 104L))
        // A stale row id (e.g. the partner was recreated) resolves to nothing instead of another row.
        assertNull(SpecialMissionAssignment.resolveTarget(missions, itemIcon = 11, missionId = 999L))
        // Unknown item kinds and out-of-range targets resolve to nothing.
        assertNull(SpecialMissionAssignment.resolveTarget(missions, itemIcon = 5))
        assertNull(SpecialMissionAssignment.resolveTarget(missions, itemIcon = 9, missionId = 104L))
    }

    @Test
    fun candidateInventoryExcludesOtherKindsAndOrdinaryItems() {
        val inventory = listOf(
            item(1, itemIcon = 9, type = ItemType.UNIVERSAL),
            item(2, itemIcon = 5),
            item(3, itemIcon = 9),
            item(4, itemIcon = 10),
            item(5, itemIcon = 11),
            item(6, itemIcon = 12),
        )

        assertEquals(
            listOf(3L, 4L),
            SpecialMissionAssignment.candidatesForSlot(inventory, SpecialMissionSlots.STEPS)
                .map { it.id },
        )
        assertEquals(
            listOf(5L, 6L),
            SpecialMissionAssignment.candidatesForSlot(inventory, SpecialMissionSlots.VITALS)
                .map { it.id },
        )
        assertTrue(
            SpecialMissionAssignment.candidatesForSlot(inventory, SpecialMissionSlots.BATTLES)
                .isEmpty(),
        )
        assertTrue(SpecialMissionAssignment.candidatesForSlot(inventory, -1).isEmpty())
        assertTrue(SpecialMissionAssignment.candidatesForSlot(inventory, 4).isEmpty())
    }

    @Test
    fun fewerThanFourRowsOnlyResolveExistingPositions() {
        val partial = rows(listOf(43157, 2))

        assertEquals(101L, SpecialMissionAssignment.missionForSlot(partial, 0)?.id)
        assertEquals(102L, SpecialMissionAssignment.missionForSlot(partial, 1)?.id)
        assertNull(SpecialMissionAssignment.missionForSlot(partial, 2))
        assertNull(SpecialMissionAssignment.slotOfMissionId(partial, 103L))
        // No row exists for slot 2, so neither the item flow nor an explicit id may invent one.
        assertNull(SpecialMissionAssignment.resolveTarget(partial, itemIcon = 13, missionId = null))
        assertNull(SpecialMissionAssignment.resolveTarget(partial, itemIcon = 13, missionId = 103L))
        // Existing rows still resolve by position.
        assertEquals(101L, SpecialMissionAssignment.resolveTarget(partial, itemIcon = 9, missionId = null)?.id)
        assertEquals(102L, SpecialMissionAssignment.resolveTarget(partial, itemIcon = 11, missionId = 102L)?.id)
    }

    @Test
    fun itemFlowResolvesByTheItemsPositionalSlot() {
        val missions = rows()
        val itemIcons = mapOf(
            9 to SpecialMissionSlots.STEPS,
            10 to SpecialMissionSlots.STEPS,
            11 to SpecialMissionSlots.VITALS,
            12 to SpecialMissionSlots.VITALS,
            13 to SpecialMissionSlots.BATTLES,
            14 to SpecialMissionSlots.BATTLES,
            15 to SpecialMissionSlots.WINS,
            16 to SpecialMissionSlots.WINS,
        )

        itemIcons.forEach { (itemIcon, slot) ->
            val target = SpecialMissionAssignment.resolveTarget(missions, itemIcon, missionId = null)
            assertEquals("icon $itemIcon must fill slot $slot", 101L + slot, target?.id)
        }
    }

    @Test
    fun appPartnerWatchIdsZeroToThreeKeepWorking() {
        val appPartner = listOf(0, 1, 2, 3).mapIndexed { slot, watchId -> row(slot, watchId) }

        for (slot in 0..3) {
            assertEquals(101L + slot, SpecialMissionAssignment.missionForSlot(appPartner, slot)?.id)
            assertEquals(slot, SpecialMissionAssignment.slotOfMissionId(appPartner, 101L + slot))
        }
        val target = SpecialMissionAssignment.resolveTarget(appPartner, itemIcon = 11, missionId = 102L)
        assertEquals(102L, target?.id)
        assertEquals(1, target?.watchId)
    }

    @Test
    fun slotTypesAndDialogLabelsStayInSync() {
        assertEquals(SpecialMission.Type.STEPS, SpecialMissionAssignment.expectedTypeForSlot(0))
        assertEquals(SpecialMission.Type.VITALS, SpecialMissionAssignment.expectedTypeForSlot(1))
        assertEquals(SpecialMission.Type.BATTLES, SpecialMissionAssignment.expectedTypeForSlot(2))
        assertEquals(SpecialMission.Type.WINS, SpecialMissionAssignment.expectedTypeForSlot(3))
        assertEquals(SpecialMission.Type.NONE, SpecialMissionAssignment.expectedTypeForSlot(4))
        assertEquals(SpecialMission.Type.NONE, SpecialMissionAssignment.expectedTypeForSlot(-1))

        assertEquals(R.string.special_mission_slot_steps, specialMissionSlotLabelRes(0))
        assertEquals(R.string.special_mission_slot_vitals, specialMissionSlotLabelRes(1))
        assertEquals(R.string.special_mission_slot_battles, specialMissionSlotLabelRes(2))
        assertEquals(R.string.special_mission_slot_wins, specialMissionSlotLabelRes(3))
        assertEquals(
            4,
            (0..3).map(::specialMissionSlotLabelRes).toSet().size,
        )
    }
}
