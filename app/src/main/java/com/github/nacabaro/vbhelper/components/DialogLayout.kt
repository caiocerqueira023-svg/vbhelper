package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Keep editor content scrollable within the current window, including a visible IME. */
@Composable
fun Modifier.editorDialogBounds(): Modifier {
    val height = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() * .88f }
    return windowInsetsPadding(WindowInsets.safeDrawing).heightIn(max = height)
}
