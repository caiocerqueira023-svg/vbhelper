package com.github.nacabaro.vbhelper.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Existing UI token names remain source-compatible while resolving from the selected
// palette. Read them in composition and capture the result for drawing/event callbacks.
val SpaceBlack: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.base
val DeepPurpleBg: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.background
val DeepPurpleBgAlt: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.backgroundAlt
val SurfaceDeepPurple: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface
val SurfaceElevatedPurple: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surfaceElevated
val SurfaceHighlightPurple: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surfaceHighlight
val SurfaceStroke: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.stroke
val VitalPurple: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.primary
val VitalPurpleBright: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.primaryBright
val VitalPurpleDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.primaryDim
val OnVitalPrimary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onPrimary
val VitalCyan: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accent
val VitalCyanDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.accentDim
val OnVitalAccent: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onAccent
val RadarCompass: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.compass
val RadarFollower: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.follower
val VitalOrange: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.tertiary
val VitalYellow: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.yellow
val StatusGreen: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.green
val StatusGreenDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.greenDim
val StatusBlue: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blue
val StatusBlueDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blueDim
val StatusRed: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.red
val StatusRedDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.redDim
val StatusYellow: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.statusYellow
val StatusYellowDim: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.yellowDim
val TextPrimaryOnDark: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textPrimary
val TextSecondaryOnDark: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textSecondary
val TextMutedOnDark: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.textMuted
val OnStatus: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onStatus
val TextSignalHint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.signalHint
val BattlePanel: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battlePanel
val BattleBackdrop: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleBackdrop
val BattleErrorSurface: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleErrorSurface
val BattleErrorOutline: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleErrorOutline
val BattleErrorHint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleErrorHint
val BattleDestructive: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleDestructive
val BattleEnemyHealth: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleEnemyHealth
val BattleTrack: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.battleTrack
val BlastControl: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastControl
val OnBlastControl: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.onBlastControl
val BlastOutline: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastOutline
val BlastTrack: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastTrack
val BlastTarget: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastTarget
val BlastProgress: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastProgress
val BlastNeedle: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.blastNeedle
val SceneTextShadow: Color @Composable @ReadOnlyComposable get() =
    if (LocalAppPalette.current.isDark) Color.Black else Color.White
