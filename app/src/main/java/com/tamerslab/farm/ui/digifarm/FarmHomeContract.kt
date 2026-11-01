package com.tamerslab.farm.ui.digifarm

import androidx.compose.runtime.State
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

/** Animation frames the diorama can draw; filled from VB or Dot artwork depending on the filter. */
data class FarmArtwork(
    val idle1: ByteArray,
    val idle2: ByteArray,
    val walk1: ByteArray,
    val walk2: ByteArray,
    val train1: ByteArray,
    val train2: ByteArray,
    val happy: ByteArray,
    val sleep: ByteArray,
    val width: Int,
    val height: Int,
)

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
    internal val recenterRequests: State<Int>,
)