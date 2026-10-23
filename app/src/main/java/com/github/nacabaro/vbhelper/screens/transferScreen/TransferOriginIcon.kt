package com.github.nacabaro.vbhelper.screens.transferScreen

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.github.nacabaro.vbhelper.navigation.TransferGame
import com.github.nacabaro.vbhelper.source.gameSave.NdsLauncherIconRepository

@Composable
internal fun TransferOriginIcon(game: TransferGame, modifier: Modifier = Modifier, revision: Int = 0) {
    val context = LocalContext.current
    val repository = remember(context) { NdsLauncherIconRepository(context) }
    val icon by produceState<Bitmap?>(null, game, revision) { value = repository.icon(game) }
    if (icon != null) Image(requireNotNull(icon).asImageBitmap(), null, modifier, filterQuality = FilterQuality.None)
    else Icon(Icons.Default.VideogameAsset, contentDescription = null, modifier = modifier)
}
