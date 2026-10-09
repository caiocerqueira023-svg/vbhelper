package com.github.nacabaro.vbhelper.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.github.nacabaro.vbhelper.ui.theme.LocalAppPalette
import kotlin.math.roundToInt

/** Keeps imported logo/name pixels intact, with a quiet silhouette shadow on light surfaces. */
@Composable
fun DimLogo(
    bitmap: ImageBitmap,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    filterQuality: FilterQuality = FilterQuality.None,
    alignment: Alignment = Alignment.Center,
) {
    val shadow = if (LocalAppPalette.current.isDark) Modifier else {
        // Blur the alpha mask in software, so the shadow also works on Android 9/10/11.
        val source = remember(bitmap) {
            checkNotNull(bitmap.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false))
        }
        Modifier.drawWithCache {
            val scale = contentScale.computeScaleFactor(
                Size(bitmap.width.toFloat(), bitmap.height.toFloat()), size
            )
            val width = (bitmap.width * scale.scaleX).roundToInt().coerceAtLeast(1)
            val height = (bitmap.height * scale.scaleY).roundToInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(source, width, height, false)
            val offsets = IntArray(2)
            val mask = scaled.extractAlpha(Paint().apply {
                maskFilter = BlurMaskFilter(1.5.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
            }, offsets).asImageBitmap()
            if (scaled !== source) scaled.recycle()
            val aligned = alignment.align(IntSize(width, height),
                IntSize(size.width.roundToInt(), size.height.roundToInt()), layoutDirection)
            val position = Offset(
                aligned.x.toFloat() + offsets[0],
                aligned.y.toFloat() + offsets[1] + 1.dp.toPx(),
            )
            onDrawBehind {
                drawImage(mask, topLeft = position, alpha = .5f,
                    colorFilter = ColorFilter.tint(Color.Black))
            }
        }
    }
    Image(bitmap = bitmap, contentDescription = contentDescription,
        modifier = modifier.then(shadow), contentScale = contentScale, filterQuality = filterQuality,
        alignment = alignment)
}
