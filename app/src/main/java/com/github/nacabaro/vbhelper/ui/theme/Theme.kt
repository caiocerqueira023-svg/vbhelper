package com.github.nacabaro.vbhelper.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// "Vital Arena" dark theme - the app's signature look.
private val VitalArenaDarkColorScheme = darkColorScheme(
    primary = VitalPurpleBright,
    onPrimary = SpaceBlack,
    primaryContainer = VitalPurpleDim,
    onPrimaryContainer = TextPrimaryOnDark,

    secondary = VitalCyan,
    onSecondary = SpaceBlack,
    secondaryContainer = VitalCyanDim,
    onSecondaryContainer = TextPrimaryOnDark,

    tertiary = VitalOrange,
    onTertiary = SpaceBlack,
    tertiaryContainer = SurfaceHighlightPurple,
    onTertiaryContainer = VitalOrange,

    background = DeepPurpleBg,
    onBackground = TextPrimaryOnDark,

    surface = SurfaceDeepPurple,
    onSurface = TextPrimaryOnDark,
    surfaceVariant = SurfaceElevatedPurple,
    onSurfaceVariant = TextSecondaryOnDark,
    surfaceContainer = SurfaceDeepPurple,
    surfaceContainerLow = DeepPurpleBgAlt,
    surfaceContainerLowest = SpaceBlack,
    surfaceContainerHigh = SurfaceElevatedPurple,
    surfaceContainerHighest = SurfaceHighlightPurple,

    outline = SurfaceStroke,
    outlineVariant = VitalPurpleDim,

    error = StatusRed,
    onError = TextPrimaryOnDark,
    errorContainer = StatusRedDim,
    onErrorContainer = TextPrimaryOnDark,
)

// Kept mostly in sync with the dark scheme so the app never loses its
// signature dark-purple identity even if a caller asks for "light".
private val VitalArenaLightColorScheme = lightColorScheme(
    primary = VitalPurple,
    onPrimary = TextPrimaryOnDark,
    primaryContainer = VitalPurpleDim,
    onPrimaryContainer = TextPrimaryOnDark,

    secondary = VitalCyanDim,
    onSecondary = TextPrimaryOnDark,
    secondaryContainer = VitalCyanDim,
    onSecondaryContainer = TextPrimaryOnDark,

    tertiary = VitalOrange,
    onTertiary = SpaceBlack,

    background = DeepPurpleBg,
    onBackground = TextPrimaryOnDark,

    surface = SurfaceDeepPurple,
    onSurface = TextPrimaryOnDark,
    surfaceVariant = SurfaceElevatedPurple,
    onSurfaceVariant = TextSecondaryOnDark,
    surfaceContainerHighest = SurfaceHighlightPurple,

    outline = SurfaceStroke,

    error = StatusRed,
    onError = TextPrimaryOnDark,
)

@Composable
fun VBHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appFont: AppFont = AppFont.OXANIUM,
    // Dynamic color intentionally defaults to OFF: this app has its own
    // dark-purple "Vital Arena" identity that we always want to keep,
    // regardless of the device's Material You wallpaper colors.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // The whole visual identity of this app is the dark purple/cyan look,
    // so we intentionally always apply it rather than switching to a
    // washed-out light scheme.
    val colorScheme = if (darkTheme) VitalArenaDarkColorScheme else VitalArenaLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography(appFontFamily(appFont)),
        shapes = VitalArenaShapes,
        content = content
    )
}
