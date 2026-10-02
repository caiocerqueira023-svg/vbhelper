package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.StatusRed
import com.github.nacabaro.vbhelper.world.ecosystem.InteractionType
import kotlin.math.sin

/** Activity stays attached to its participant, with optional live speech above it. */
@Composable
internal fun RadarActivityIndicator(type: InteractionType, speech: String?, frameNanos: Long, motion: Boolean, modifier: Modifier = Modifier, healthFraction: Float? = null) {
    val description=stringResource(if(type==InteractionType.CHAT) R.string.ui_world_spectate_chat else R.string.ui_world_spectate_battle)
    val color=if(type==InteractionType.CHAT) VitalCyan else StatusRed
    Column(modifier.heightIn(min=48.dp), horizontalAlignment=Alignment.CenterHorizontally) {
        if(!speech.isNullOrBlank()) Surface(color=SurfaceDeepPurple,shape=MaterialTheme.shapes.small) {
            Text(speech,color=TextPrimaryOnDark,style=MaterialTheme.typography.bodySmall,maxLines=2,
                overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(horizontal=8.dp,vertical=4.dp))
        }
        Canvas(Modifier.size(32.dp).semantics { contentDescription=description }) {
            val pulse=if(motion) (0.88 + 0.12*sin(frameNanos/350_000_000.0)).toFloat() else 1f
            drawCircle(SurfaceDeepPurple, radius=size.minDimension/2)
            val stroke=2.dp.toPx()
            if(type==InteractionType.CHAT) {
                drawRoundRect(color,Offset(size.width*0.2f,size.height*0.22f),Size(size.width*0.6f,size.height*0.44f),
                    CornerRadius(3.dp.toPx()),style=Stroke(stroke))
                drawLine(color,Offset(size.width*0.34f,size.height*0.66f),Offset(size.width*0.25f,size.height*0.8f),stroke,StrokeCap.Round)
                repeat(3) { drawCircle(color,1.2.dp.toPx(),Offset(size.width*(0.35f+it*0.15f),size.height*0.44f)) }
            } else {
                val ink=color.copy(alpha=pulse)
                drawLine(ink,Offset(size.width*0.24f,size.height*0.76f),Offset(size.width*0.76f,size.height*0.24f),stroke,StrokeCap.Round)
                drawLine(ink,Offset(size.width*0.24f,size.height*0.24f),Offset(size.width*0.76f,size.height*0.76f),stroke,StrokeCap.Round)
                drawLine(ink,Offset(size.width*0.18f,size.height*0.56f),Offset(size.width*0.44f,size.height*0.82f),stroke)
                drawLine(ink,Offset(size.width*0.56f,size.height*0.82f),Offset(size.width*0.82f,size.height*0.56f),stroke)
            }
        }
        healthFraction?.let { health -> LinearProgressIndicator(progress={health.coerceIn(0f,1f)},color=color,trackColor=SurfaceDeepPurple,
            modifier=Modifier.width(64.dp).height(4.dp)) }
    }
}
