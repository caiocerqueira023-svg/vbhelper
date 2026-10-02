package com.github.nacabaro.vbhelper.screens.worldScreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.ui.theme.SurfaceDeepPurple
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.world.ecosystem.EcosystemInteractionSummary

@Composable
internal fun RadarOverheadAnnotation(centerX: Float, spriteTop: Float, viewportWidth: Int, name: String,
    distance: Int, labels: Boolean, activity: EcosystemInteractionSummary?, speech: String?, individualId: String,
    frameNanos: Long, motion: Boolean, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val density=LocalDensity.current
    var measured by remember(name,labels,speech,viewportWidth,density.density,density.fontScale) { mutableStateOf(IntSize.Zero) }
    val margin=with(density) { 8.dp.roundToPx() }
    val position=radarOverheadPosition(centerX,spriteTop,measured.width,measured.height,viewportWidth,margin,margin)
    Column(modifier.offset { position?.let { IntOffset(it.x,it.y) } ?: IntOffset.Zero }
        .then(if(position!=null && onClick!=null) Modifier.combinedClickable(onClick=onClick) else Modifier)
        .widthIn(max=with(density) { (viewportWidth-2*margin).coerceAtLeast(1).toDp().coerceAtMost(180.dp) })
        .heightIn(min=if(labels || activity!=null)48.dp else 0.dp)
        .onSizeChanged { measured=it }.graphicsLayer { alpha=if(position==null)0f else 1f },
        horizontalAlignment=Alignment.CenterHorizontally) {
        if(labels) Surface(color=SurfaceDeepPurple,shape=MaterialTheme.shapes.small,modifier=Modifier.testTag("radar-entity-label")) {
            Column(Modifier.padding(horizontal=8.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Text(name,color=TextPrimaryOnDark,style=MaterialTheme.typography.labelMedium,textAlign=TextAlign.Center,
                    softWrap=true,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(stringResource(R.string.ui_world_distance_metres,distance),color=TextSecondaryOnDark,
                    style=MaterialTheme.typography.labelSmall,maxLines=1)
            }
        }
        activity?.let { event -> RadarActivityIndicator(event.type,speech,frameNanos,motion,
            healthFraction=event.combatants[individualId]?.let { it.health.toFloat()/it.maxHealth.coerceAtLeast(1) }) }
    }
}
