package com.tamerslab.home.ui

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tamerslab.utils.SingleFlightOperation

class VitalWearShareViewModel(prepare: suspend (Long) -> Intent) : ViewModel() {
    val export = SingleFlightOperation(viewModelScope, prepare)
}
