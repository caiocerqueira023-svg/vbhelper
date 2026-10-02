package com.github.nacabaro.vbhelper.screens.worldScreen

import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionFailure
import com.github.nacabaro.vbhelper.world.ecosystem.WorldInteractionException
import com.github.nacabaro.vbhelper.world.ecosystem.RadarCommandException
import com.github.nacabaro.vbhelper.world.ecosystem.RadarRejection
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemIssue

internal fun WorldInteractionException.messageResource(): Int = when (reason) {
    InteractionFailure.BUSY -> R.string.ui_world_encounter_busy
    InteractionFailure.UNAVAILABLE -> R.string.ui_world_encounter_unavailable
    InteractionFailure.STALE_LOCATION -> R.string.ui_world_location_stale
}

internal fun RadarCommandException.messageResource(): Int = when {
    issue == EcosystemIssue.UNSUPPORTED_RULES -> R.string.ui_world_unsupported_rules
    issue == EcosystemIssue.STORAGE -> R.string.ui_world_storage_failure
    rejection == RadarRejection.STALE_LOCATION -> R.string.ui_world_location_stale
    rejection == RadarRejection.OUT_OF_RANGE -> R.string.ui_world_too_far
    rejection == RadarRejection.UNAVAILABLE -> R.string.ui_world_encounter_unavailable
    rejection == RadarRejection.CLAIMED -> R.string.ui_world_encounter_busy
    rejection == RadarRejection.STALE_SNAPSHOT -> R.string.ui_world_snapshot_changed
    else -> R.string.ui_world_not_ready
}
