package com.github.nacabaro.vbhelper.components

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan

/** Quiet technical frame for panels that need to communicate state or selection. */
fun Modifier.cyberFrame(active: Boolean = false): Modifier = drawBehind {
    val color = if (active) VitalCyan else SurfaceStroke
    val alpha = if (active) 0.82f else 0.52f
    val stroke = 1.dp.toPx()
    val corner = 11.dp.toPx()
    val w = size.width
    val h = size.height
    drawRect(color.copy(alpha = alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
    if (active) {
        drawRect(
            color.copy(alpha = 0.06f),
            topLeft = Offset(stroke, stroke),
            size = androidx.compose.ui.geometry.Size(w - 2 * stroke, h - 2 * stroke)
        )
    }
    listOf(
        Offset(0f, 0f) to Offset(corner, 0f), Offset(0f, 0f) to Offset(0f, corner),
        Offset(w, 0f) to Offset(w - corner, 0f), Offset(w, 0f) to Offset(w, corner),
        Offset(0f, h) to Offset(corner, h), Offset(0f, h) to Offset(0f, h - corner),
        Offset(w, h) to Offset(w - corner, h), Offset(w, h) to Offset(w, h - corner)
    ).forEach { (from, to) -> drawLine(color.copy(alpha = alpha), from, to, strokeWidth = 2.dp.toPx()) }
}

@Composable
fun CyberEmptyState(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextMutedOnDark)
    }
}

/**
 * Standard action control for the Vital Arena UI.
 *
 * Actions share the same quiet, angular outline as the favorite control so
 * dialogs and secondary screens do not fall back to rounded Material fills.
 * The default content color is deliberately white for readable contrast;
 * callers can still opt into a status color for a genuinely semantic action.
 */
@Composable
fun VitalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CutCornerShape(8.dp),
    borderColor: Color = SurfaceStroke,
    contentColor: Color = TextPrimaryOnDark,
    disabledContentColor: Color = TextMutedOnDark,
    containerColor: Color = Color.Transparent,
    disabledContainerColor: Color = Color.Transparent,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = disabledContentColor
        ),
        contentPadding = contentPadding,
        content = content
    )
}

@Composable
fun cyberPulseAlpha(): Float {
    if (!motionEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "cyberPulse")
    return transition.animateFloat(
        initialValue = .72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cyberPulseAlpha"
    ).value
}

/** Honors Android's system-wide animator setting for nonessential decorative motion. */
@Composable
fun motionEnabled(): Boolean {
    LocalContext.current
    return remember { ValueAnimator.areAnimatorsEnabled() }
}
