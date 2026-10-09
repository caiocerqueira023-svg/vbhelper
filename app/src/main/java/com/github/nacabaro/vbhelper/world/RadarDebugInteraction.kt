package com.github.nacabaro.vbhelper.world

/**
 * Debug-only bypass for player-initiated Radar range checks (chat, battles,
 * quest meetings, joining group encounters). Only ever enabled from the
 * debug-only Radar toggle, which is compiled out of release builds by the
 * debuggable check at the call site. Autonomous ecosystem movement, anchors
 * and population visibility are intentionally unaffected.
 */
object RadarDebugInteraction {
    @Volatile var allowAnyDistance: Boolean = false
}
