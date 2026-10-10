package com.github.nacabaro.vbhelper.screens.homeScreens

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.nacabaro.vbhelper.source.SingleFlightOperation

class VitalWearShareViewModel(prepare: suspend (Long) -> Intent) : ViewModel() {
    val export = SingleFlightOperation(viewModelScope, prepare)
}
