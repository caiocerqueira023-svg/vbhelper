package com.tamerslab.storage.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A Digifarm place waiting for a Storage partner.
 *
 * Set by Home when the visitor chooses "Swap for one in Storage", consumed by Storage: while a target is
 * pending, tapping a character activates it for the farm instead of opening its details, then Storage
 * returns to the farm. It lives outside composition so it survives the navigation.
 */
data class FarmSwapTarget(
    val renderId: String,
    val characterId: Long,
    val name: String,
    /** True when the outgoing partner leads the farm; a favourite swap keeps the active partner. */
    val isActivePartner: Boolean,
)

object FarmSwapRequest {
    var target: FarmSwapTarget? by mutableStateOf(null)

    fun clear() {
        target = null
    }
}
