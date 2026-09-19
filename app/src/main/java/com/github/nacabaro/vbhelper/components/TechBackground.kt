package com.github.nacabaro.vbhelper.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBg
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark

/** A quiet, non-interactive device pattern placed behind all application content. */
@Composable
fun TechBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.fillMaxSize().background(DeepPurpleBg)) {
        TechStaticPattern(Modifier.fillMaxSize())
        TechRotatingRings(Modifier.fillMaxSize())
        content()
    }
}

/** Static geometry is kept out of the animated subtree so the app content is
 * not invalidated every frame by the decorative ring rotation. */
@Composable
private fun TechStaticPattern(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val subtle = SurfaceStroke.copy(alpha = 0.07f)
        val grid = 64.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(subtle, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
            x += grid
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(subtle, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            y += grid
        }

        val circuit = TextPrimaryOnDark.copy(alpha = 0.035f)
        val start = Offset(0f, size.height * .19f)
        val elbow = Offset(size.width * .18f, size.height * .19f)
        val end = Offset(size.width * .23f, size.height * .14f)
        drawLine(circuit, start, elbow, strokeWidth = 2.dp.toPx())
        drawLine(circuit, elbow, end, strokeWidth = 2.dp.toPx())
        drawCircle(VitalCyan.copy(alpha = .09f), radius = 3.dp.toPx(), center = end)
    }
}

@Composable
private fun TechRotatingRings(modifier: Modifier = Modifier) {
    val allowMotion = motionEnabled()
    val ringRotation = if (allowMotion) {
        rememberInfiniteTransition(label = "backgroundRings").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
            label = "ringRotation"
        ).value
    } else 0f

    Canvas(modifier) {
        // Partial rings create a restrained "device mechanism" motif without
        // competing with text or artwork in the foreground.
        val subtle = SurfaceStroke.copy(alpha = 0.07f)
        val faintCyan = VitalCyan.copy(alpha = 0.035f)
        val center = Offset(size.width * 0.92f, size.height * 0.72f)
        val stroke = Stroke(width = 14.dp.toPx())
        rotate(ringRotation, center) {
            drawArc(
                subtle,
                195f,
                130f,
                false,
                center - Offset(size.width * .42f, size.width * .42f),
                androidx.compose.ui.geometry.Size(size.width * .84f, size.width * .84f),
                style = stroke
            )
            drawArc(
                faintCyan,
                205f,
                112f,
                false,
                center - Offset(size.width * .31f, size.width * .31f),
                androidx.compose.ui.geometry.Size(size.width * .62f, size.width * .62f),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
