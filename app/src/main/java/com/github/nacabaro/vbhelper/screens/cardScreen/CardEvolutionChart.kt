package com.github.nacabaro.vbhelper.screens.cardScreen

import androidx.compose.foundation.Canvas
import com.github.nacabaro.vbhelper.components.DimLogo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.github.nacabaro.vbhelper.R
import com.github.nacabaro.vbhelper.components.CharacterEntry
import com.github.nacabaro.vbhelper.components.CyberEmptyState
import com.github.nacabaro.vbhelper.components.VitalButton
import com.github.nacabaro.vbhelper.dtos.CardDtos
import com.github.nacabaro.vbhelper.dtos.CardEvolutionGraph
import com.github.nacabaro.vbhelper.ui.theme.DeepPurpleBgAlt
import com.github.nacabaro.vbhelper.ui.theme.SurfaceStroke
import com.github.nacabaro.vbhelper.ui.theme.TextPrimaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextMutedOnDark
import com.github.nacabaro.vbhelper.ui.theme.TextSecondaryOnDark
import com.github.nacabaro.vbhelper.ui.theme.VitalCyan
import com.github.nacabaro.vbhelper.ui.theme.VitalYellow
import com.github.nacabaro.vbhelper.utils.BitmapData
import com.github.nacabaro.vbhelper.utils.getBitmap
import kotlin.math.roundToInt

/** Stateless data boundary; viewport and fusion preference are saved per displayed card. */
@Composable
internal fun CardEvolutionPanel(
    card: CardDtos.CardProgress,
    cards: List<CardDtos.CardProgress>,
    graph: CardEvolutionGraph?,
    onSelectCard: (Long) -> Unit,
    onSelectCharacter: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedCharacterId: Long? = null,
    loadFailed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    var showFusions by rememberSaveable(card.cardId) { mutableStateOf(false) }
    val hasFusions = graph?.links?.any { it.isJogress } == true
    Column(modifier = modifier) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dex_chart_progress, card.obtainedCharacters, card.totalCharacters),
                style = MaterialTheme.typography.titleSmall,
                color = VitalCyan,
            )
            FilterChip(
                    enabled = graph != null && hasFusions,
                    selected = showFusions,
                    onClick = { showFusions = !showFusions },
                    label = { Text(stringResource(R.string.dex_chart_fusions)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.AltRoute, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
        }
        when {
            loadFailed -> Column(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.dex_chart_load_error), textAlign = TextAlign.Center)
                VitalButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) {
                    Text(stringResource(R.string.dex_chart_retry))
                }
            }
            graph == null -> DexChartLoading(Modifier.weight(1f).fillMaxWidth())
            graph.characters.isEmpty() -> CyberEmptyState(
                stringResource(R.string.dex_chart_empty), Modifier.weight(1f).fillMaxWidth())
            else -> CardEvolutionChart(
                graph, showFusions && hasFusions, selectedCharacterId, onSelectCharacter,
                modifier = Modifier.weight(1f).fillMaxWidth())
        }
        CardChartNavigation(card, cards, onSelectCard)
    }
}

@Composable
internal fun DexChartLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.dex_chart_loading)
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = description }, color = VitalCyan)
    }
}

@Composable
private fun CardEvolutionChart(
    graph: CardEvolutionGraph,
    showFusions: Boolean,
    selectedCharacterId: Long?,
    onSelectCharacter: (Long) -> Unit,
    modifier: Modifier,
) {
    val topology = graph.characters.map { CardEvolutionLayoutNode(it.id, it.stage, it.charaIndex) }
    val layout = remember(topology, graph.links) { CardEvolutionLayout.create(topology, graph.links) }
    var viewport by rememberSaveable(graph.cardId, stateSaver = CardChartViewportSaver) {
        mutableStateOf(CardChartViewport(1f, 0f, 0f))
    }
    var initialized by rememberSaveable(graph.cardId) { mutableStateOf(false) }
    var viewSize by remember { mutableStateOf(CardChartSize(0f, 0f)) }
    val density = LocalDensity.current.density
    val scale = viewport.scale
    val panX = viewport.x
    val panY = viewport.y
    fun update(value: CardChartViewport) { viewport = value }
    LaunchedEffect(layout.size, viewSize) {
        if (viewSize.width > 0 && viewSize.height > 0) {
            update(if (initialized) viewport.clamped(layout.size, viewSize)
                else CardChartViewport.fit(layout.size, viewSize))
            initialized = true
        }
    }
    Box(modifier.padding(horizontal = 8.dp).clipToBounds().background(DeepPurpleBgAlt)) {
        Box(
            Modifier.fillMaxSize().testTag("dex-evolution-viewport")
                .onSizeChanged { viewSize = CardChartSize(it.width / density, it.height / density) }
                .semantics {
                    isTraversalGroup = true
                    horizontalScrollAxisRange = ScrollAxisRange(
                        value = { viewport.scrollPosition(layout.size, viewSize).x * density },
                        maxValue = { maxOf(0f, (layout.width * viewport.scale - viewSize.width) * density) },
                    )
                    verticalScrollAxisRange = ScrollAxisRange(
                        value = { viewport.scrollPosition(layout.size, viewSize).y * density },
                        maxValue = { maxOf(0f, (layout.height * viewport.scale - viewSize.height) * density) },
                    )
                    scrollBy { x, y ->
                        update(viewport.transform(layout.size, viewSize, 1f,
                            CardChartPoint(-x / density, -y / density), CardChartPoint(0f, 0f)))
                        true
                    }
                }
                .pointerInput(layout.size, viewSize, density) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        update(viewport.transform(layout.size, viewSize, zoom,
                            CardChartPoint(pan.x / density, pan.y / density),
                            CardChartPoint(centroid.x / density, centroid.y / density)))
                    }
                },
        ) {
            val segments = remember(layout, showFusions) { layout.connectorSegments(showFusions) }
            val gridColor = SurfaceStroke.copy(alpha = .3f)
            val fusionColor = VitalYellow
            val routeColor = TextSecondaryOnDark
            Canvas(Modifier.fillMaxSize()) {
                // A quiet chart grid, anchored to the same world coordinates as the routes.
                val step = 24.dp.toPx() * scale
                var gridX = (panX * density % step + step) % step
                while (gridX < size.width) {
                    drawLine(gridColor, Offset(gridX, 0f), Offset(gridX, size.height), 1f)
                    gridX += step
                }
                var gridY = (panY * density % step + step) % step
                while (gridY < size.height) {
                    drawLine(gridColor, Offset(0f, gridY), Offset(size.width, gridY), 1f)
                    gridY += step
                }
                withTransform({
                    translate(panX * density, panY * density)
                    scale(scale, scale, pivot = Offset.Zero)
                }) {
                    segments.forEach { segment ->
                        drawLine(
                            color = if (segment.isJogress) fusionColor else routeColor,
                            start = Offset(segment.start.x * density, segment.start.y * density),
                            end = Offset(segment.end.x * density, segment.end.y * density),
                            strokeWidth = 1.5.dp.toPx() / scale,
                            pathEffect = if (segment.isJogress) PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx() / scale, 4.dp.toPx() / scale)) else null,
                        )
                    }
                }
            }
            Layout(
                modifier = Modifier.fillMaxSize(),
                content = {
                    graph.characters.forEach { character ->
                        key(character.id) {
                            val description = stringResource(R.string.dex_chart_character, character.charaIndex + 1, stageLabel(character.stage))
                            val status = stringResource(when {
                                character.isCurrentlyAvailable -> R.string.dex_status_currently_available
                                character.discoveredOn != null -> R.string.dex_status_previously_obtained
                                else -> R.string.dex_status_never_obtained
                            })
                            CharacterEntry(
                                icon = BitmapData(character.spriteIdle, character.spriteWidth, character.spriteHeight),
                                idleFrame2 = if (character.isCurrentlyAvailable) BitmapData(character.spriteIdle2,
                                    character.spriteWidth, character.spriteHeight) else null,
                                animationKey = character.id,
                                grayscale = character.discoveredOn == null,
                                entryPadding = 0.dp,
                                fitSpriteToBounds = true,
                                selected = character.id == selectedCharacterId,
                                iconDescription = null,
                                modifier = Modifier.semantics(mergeDescendants = true) {
                                    contentDescription = description
                                    stateDescription = status
                                    selected = character.id == selectedCharacterId
                                },
                                onClick = { onSelectCharacter(character.id) },
                            )
                        }
                    }
                    layout.rows.forEach { row ->
                        Text(stageLabel(row.stage), style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryOnDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            ) { measurables, constraints ->
                val nodeSize = (CardEvolutionLayout.NODE_SIZE * scale * density).roundToInt()
                val nodeCount = graph.characters.size
                val nodes = measurables.take(nodeCount).map { it.measure(Constraints.fixed(nodeSize, nodeSize)) }
                val labels = measurables.drop(nodeCount).map {
                    it.measure(Constraints(maxWidth = (22f * scale * density).roundToInt(), maxHeight = nodeSize))
                }
                layout(constraints.maxWidth, constraints.maxHeight) {
                    nodes.forEachIndexed { index, placeable ->
                        val position = layout.nodes.getValue(graph.characters[index].id)
                        placeable.place((panX * density + position.x * scale * density).roundToInt(),
                            (panY * density + position.y * scale * density).roundToInt())
                    }
                    labels.forEachIndexed { index, label ->
                        label.place((panX * density + 2f * scale * density).roundToInt(),
                            (panY * density + (layout.rows[index].top + CardEvolutionLayout.NODE_SIZE / 2) * scale * density - label.height / 2).roundToInt())
                    }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(DeepPurpleBgAlt.copy(alpha = .88f), CutCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            IconButton(onClick = { update(CardChartViewport.fit(layout.size, viewSize)) }) {
                Icon(Icons.Default.CenterFocusStrong, stringResource(R.string.dex_chart_fit),
                    modifier = Modifier.size(18.dp), tint = TextSecondaryOnDark)
            }
            IconButton(enabled = scale > CardChartViewport.MIN_SCALE, onClick = {
                update(viewport.transform(layout.size, viewSize, 1 / 1.25f, CardChartPoint(0f, 0f),
                    CardChartPoint(viewSize.width / 2, viewSize.height / 2)))
            }) { Icon(Icons.Default.Remove, stringResource(R.string.dex_chart_zoom_out), modifier = Modifier.size(18.dp)) }
            IconButton(enabled = scale < CardChartViewport.MAX_SCALE, onClick = {
                update(viewport.transform(layout.size, viewSize, 1.25f, CardChartPoint(0f, 0f),
                    CardChartPoint(viewSize.width / 2, viewSize.height / 2)))
            }) { Icon(Icons.Default.Add, stringResource(R.string.dex_chart_zoom_in), modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun CardChartNavigation(card: CardDtos.CardProgress, cards: List<CardDtos.CardProgress>, onSelectCard: (Long) -> Unit) {
    val index = cards.indexOfFirst { it.cardId == card.cardId }
    Row(
        modifier = Modifier.fillMaxWidth().testTag("dex-card-navigation")
            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(enabled = index > 0, onClick = { cards.getOrNull(index - 1)?.let { onSelectCard(it.cardId) } }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.dex_chart_previous_card),
                tint = if (index > 0) VitalCyan else TextMutedOnDark)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            val bitmap = remember(card.cardLogo, card.logoWidth, card.logoHeight) {
                BitmapData(card.cardLogo, card.logoWidth, card.logoHeight).getBitmap().asImageBitmap()
            }
            DimLogo(bitmap, contentDescription = null, filterQuality = FilterQuality.None,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(48.dp))
            Text(card.cardName, style = MaterialTheme.typography.labelLarge, color = TextPrimaryOnDark,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
        IconButton(enabled = index >= 0 && index < cards.lastIndex,
            onClick = { cards.getOrNull(index + 1)?.let { onSelectCard(it.cardId) } }) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.dex_chart_next_card),
                tint = if (index >= 0 && index < cards.lastIndex) VitalCyan else TextMutedOnDark)
        }
    }
}

internal fun stageLabel(stage: Int): String = when (stage) {
    0 -> "I"; 1 -> "II"; 2 -> "III"; 3 -> "IV"; 4 -> "V"; 5 -> "VI"; 6 -> "VII"; 7 -> "VIII"
    else -> (stage.toLong() + 1).toString()
}

private val CardChartViewportSaver = Saver<CardChartViewport, List<Float>>(
    save = { listOf(it.scale, it.x, it.y) },
    restore = { CardChartViewport(it[0], it[1], it[2]) },
)
