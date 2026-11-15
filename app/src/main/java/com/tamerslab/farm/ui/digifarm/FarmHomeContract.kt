package com.tamerslab.farm.ui.digifarm

import androidx.compose.runtime.State
import com.tamerslab.dtos.CharacterDtos
import com.tamerslab.farm.FARM_MAX_RESIDENTS
import com.tamerslab.farm.FarmSpriteFilter

/**
 * Contract between the farm home's living diorama (`FarmHomeWorld.kt`) and its HUD
 * (`screens/farmHome/`).
 *
 * Owner revision (2026-10-30): the Digifarm is **presentation only**. There is exactly one home farm;
 * it shows the active partner plus favourites (up to a configurable limit, chosen at random when
 * there are more) walking around and doing things. No simulation, no farm management, no needs,
 * activities, chat scheduling or grouping is attached to what is on screen.
 */
data class FarmWorldResident(
    /** Stable synthetic id used by the renderer. */
    val renderId: String,
    val characterId: Long,
    val name: String,
    val attribute: String?,
    val isActivePartner: Boolean,
    /** Artwork the diorama draws for this resident (card/VB sprites or Dot sprites). */
    val artwork: FarmArtwork,
)

/**
 * Every animation frame a resident can draw: the full pose set a VB Digimon ships with.
 *
 * The frames mirror the twelve-pose `Sprite` columns except attack/dodge, which stay battle-only.
 * Dot artwork can legitimately repeat frames (Dot sets resolve walk_1/walk_2 to idle frames and may
 * reuse idle for run); the renderer dedupes identical frames into a single extruded mesh.
 */
data class FarmArtwork(
    val idle1: ByteArray,
    val idle2: ByteArray,
    val walk1: ByteArray,
    val walk2: ByteArray,
    val run1: ByteArray,
    val run2: ByteArray,
    val train1: ByteArray,
    val train2: ByteArray,
    val happy: ByteArray,
    val sleep: ByteArray,
    val width: Int,
    val height: Int,
) {
    /**
     * Identity of the whole frame set; the diorama rebuilds extruded models only when this changes.
     * [ByteArray] equality is identity-based, so a content mix is what the renderer can key on.
     */
    val contentKey: Int
        get() {
            var result = 1
            result = 31 * result + idle1.contentHashCode()
            result = 31 * result + idle2.contentHashCode()
            result = 31 * result + walk1.contentHashCode()
            result = 31 * result + walk2.contentHashCode()
            result = 31 * result + run1.contentHashCode()
            result = 31 * result + run2.contentHashCode()
            result = 31 * result + train1.contentHashCode()
            result = 31 * result + train2.contentHashCode()
            result = 31 * result + happy.contentHashCode()
            result = 31 * result + sleep.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }

    /** Width/height clamped to the renderer's legal extrusion range (0.5..1.8). */
    val bodyAspect: Float
        get() = if (width <= 0 || height <= 0) {
            1f
        } else {
            (width.toFloat() / height).coerceIn(0.5f, 1.8f)
        }

    /** Half the rendered body width in farm world units, used by resident collision. */
    val bodyRadius: Float get() = bodyAspect * 0.5f * FARM_RESIDENT_HEIGHT_UNITS
}

/** Rendered height of an extruded resident in Digifarm scene units (must match Digifarm3dViewport). */
const val FARM_RESIDENT_HEIGHT_UNITS = 0.091f

data class FarmWorldSummary(
    val residents: List<FarmWorldResident> = emptyList(),
    val followedRenderId: String? = null,
    val loading: Boolean = true,
    /** Owner setting: how many residents the farm shows (1..12). */
    val residentLimit: Int = FARM_MAX_RESIDENTS,
    /** Owner setting: which artwork the farm draws. */
    val spriteFilter: FarmSpriteFilter = FarmSpriteFilter.MIXED,
    /** Active partner + favourites before filtering/limiting, so the UI can explain what is hidden. */
    val candidateCount: Int = 0,
    /** Every stored partner and whether it lives on the island; drives the roster sheet. */
    val memberOptions: List<FarmMemberOption> = emptyList(),
) {
    val residentCount: Int get() = residents.size
    val hasResidents: Boolean get() = residents.isNotEmpty()
    val activePartner: FarmWorldResident? get() = residents.firstOrNull { it.isActivePartner }

    companion object {
        /** Hard ceiling for the farm, regardless of the owner setting. */
        const val CAPACITY = FARM_MAX_RESIDENTS
    }
}

/** Minimal hand-off from the HUD to the diorama. */
class FarmWorldController internal constructor(
    val summary: State<FarmWorldSummary>,
    /** Camera follows this resident; null falls back to the active partner. */
    val setFollowTarget: (renderId: String?) -> Unit,
    /** Ease the camera back onto the followed resident after a manual orbit. */
    val recenterOnPartner: () -> Unit,
    /** Persisted owner setting: how many residents to show. */
    val setResidentLimit: (Int) -> Unit,
    /** Persisted owner setting: which artwork the farm may draw. */
    val setSpriteFilter: (FarmSpriteFilter) -> Unit,
    /** Removes a partner from the island (the active partner cannot be removed this way). */
    val removeFromFarm: (characterId: Long) -> Unit,
    /** Puts a stored partner on the island or takes it off. */
    val setFarmMember: (characterId: Long, member: Boolean) -> Unit,
    /** True once the diorama has streamed the complete roster and drawn it. Drives the boot cover. */
    val sceneReady: State<Boolean>,
    /** Called by the diorama host once it has actually drawn the settled roster. */
    internal val notifySceneSettled: () -> Unit,
    internal val recenterRequests: State<Int>,
)

/** One stored partner as the roster sheet needs it: who it is, and whether it lives on the island. */
data class FarmMemberOption(
    val characterId: Long,
    val name: String,
    val isMember: Boolean,
    val isActive: Boolean,
    /** Full sprite data, so the roster can open the Storage partner picker for adding a member. */
    val character: CharacterDtos.CharacterWithSprites,
)
