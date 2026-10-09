package com.github.nacabaro.vbhelper.ui.theme

import android.app.Activity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

@Composable
@Suppress("DEPRECATION")
fun VBHelperTheme(
    appFont: AppFont = AppFont.OXANIUM,
    appTheme: AppTheme = AppTheme.VB_HELPER,
    content: @Composable () -> Unit
) {
    // Appearance is chosen by the user, never by the phone mode or wallpaper.
    val palette = appTheme.palette
    val colorScheme = remember(appTheme) { palette.colorScheme }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity
            val barColor = palette.background.toArgb()
            if (activity is ComponentActivity) {
                val barStyle = if (palette.isDark) SystemBarStyle.dark(barColor)
                    else SystemBarStyle.light(barColor, barColor)
                activity.enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            activity?.window?.let { window ->
                window.statusBarColor = barColor
                window.navigationBarColor = barColor
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !palette.isDark
                    isAppearanceLightNavigationBars = !palette.isDark
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalAppPalette provides palette,
        LocalMinimumInteractiveComponentSize provides 48.dp,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = appTypography(appFontFamily(appFont)),
            shapes = VitalArenaShapes,
            content = content,
        )
    }
}
