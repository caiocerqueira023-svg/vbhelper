package com.tamerslab.farm.ui.digiline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Asks DigiLine to open with one conversation pinned above the rest.
 *
 * The active partner's chat action on Home wants the whole list - not a single thread - but with the
 * partner it is about already at the top. The request is a one-shot: DigiLine consumes it on arrival,
 * so later visits keep their normal recency order. It lives outside composition so it survives the
 * navigation.
 */
object DigilinePin {
    var target: Long? by mutableStateOf(null)

    /** Returns the pending character id and clears it. */
    fun consume(): Long? {
        val pending = target
        target = null
        return pending
    }
}
