package com.github.nacabaro.vbhelper.screens.spriteViewer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.CyberPanel
import com.github.nacabaro.vbhelper.components.TopBanner
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.R

@Composable
fun SpriteViewer(
    navController: NavController,
    spriteViewerController: SpriteViewerController
) {
    val spriteList = remember { mutableStateListOf<Bitmap>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(spriteViewerController) {
        loading = true
        try {
            val sprites = spriteViewerController.getAllSprites()
            val bitmapData = spriteViewerController.convertToBitmap(sprites)
            spriteList.clear()
            spriteList.addAll(bitmapData)
        } finally {
            loading = false
        }
    }

    Scaffold (
        topBar = {
            TopBanner(
                text = "Sprite viewer",
                onBackClick = {
                    navController.popBackStack()
                }
            )
        },
        modifier = Modifier
            .fillMaxSize()
    ) { contentPadding ->
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = VitalCyan)
            }
            spriteList.isEmpty() -> CyberEmptyState(
                stringResource(R.string.sprite_viewer_empty),
                Modifier.fillMaxSize().padding(contentPadding)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = contentPadding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(spriteList) { sprite ->
                    val imageBitmap = remember(sprite) { sprite.asImageBitmap() }
                    CyberPanel(
                        modifier = Modifier.aspectRatio(1f),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = "Sprite",
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
