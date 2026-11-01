package com.tamerslab.farm

/** Latest recorded activity, not a reconstructed transcript of the time spent away. */
data class FarmReturnSummary(val minutesSinceUpdate: Long, val residentCount: Int, val recordedResting: Int)
