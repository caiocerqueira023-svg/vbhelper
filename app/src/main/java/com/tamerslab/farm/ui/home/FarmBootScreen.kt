package com.tamerslab.farm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tamerslab.R
import com.tamerslab.companion.ui.CompanionLoading
import com.tamerslab.ui.theme.DeepPurpleBg
import com.tamerslab.ui.theme.TextPrimaryOnDark

/**
 * Process-wide "the app has already opened" flag.
 *
 * It lives outside composition on purpose: the cover belongs to the app's first settle, not to a
 * composable's lifetime. Activity recreation, returning from another app or leaving Home must never
 * raise the cover again.
 */
internal object BootCover {
    var dismissed by mutableStateOf(false)
}

/**
 * Boot cover for Home.
 *
 * The first moments of the farm screen stream the habitat, the residents and the active partner into
 * the scene. Those steps are machinery, not product, so they are covered by the same loading pattern
 * the rest of the app uses - a spinner and a label - on the palette ground.
 */
@Composable
fun FarmBootScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DeepPurpleBg),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides TextPrimaryOnDark) {
            CompanionLoading(loadingText = stringResource(R.string.app_loading))
        }
    }
}
