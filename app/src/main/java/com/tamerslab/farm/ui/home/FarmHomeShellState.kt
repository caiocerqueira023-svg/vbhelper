package com.tamerslab.farm.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tamerslab.farm.ui.digifarm.FarmWorldController
import com.tamerslab.home.ui.PartnerDetent
import com.tamerslab.farm.ui.digifarm.rememberFarmWorldController

/**
 * Home-shell state that must outlive the Home route.
 *
 * The 3D world is composed by the app shell (not by the Home destination) so switching places never
 * tears the Filament engine down. That means the detent and the selected resident — which drive the
 * world's pause flag and the resident sheet — have to live at shell level too.
 */
@Stable
class FarmHomeShellState internal constructor(
    val controller: FarmWorldController,
) {
    var detent by mutableStateOf(PartnerDetent.Peek)
    var selectedResidentId by mutableStateOf<String?>(null)
}

@Composable
fun rememberFarmHomeShellState(): FarmHomeShellState {
    val controller = rememberFarmWorldController()
    return remember(controller) { FarmHomeShellState(controller) }
}
