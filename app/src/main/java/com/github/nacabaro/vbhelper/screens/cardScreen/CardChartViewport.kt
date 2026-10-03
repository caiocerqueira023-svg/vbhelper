package com.github.nacabaro.vbhelper.screens.cardScreen

/** Bounded, centroid-anchored zoom/pan in logical dp, independent of density and rendering. */
data class CardChartViewport(val scale: Float, val x: Float, val y: Float) {
    /** Centering offsets are not scroll positions: a fitting axis has range/value zero. */
    fun scrollPosition(content: CardChartSize, viewport: CardChartSize) = CardChartPoint(
        maxOf(0f, -x).coerceAtMost(maxOf(0f, content.width * scale - viewport.width)),
        maxOf(0f, -y).coerceAtMost(maxOf(0f, content.height * scale - viewport.height)),
    )

    fun clamped(content: CardChartSize, viewport: CardChartSize): CardChartViewport {
        val safeScale = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        fun bound(offset: Float, contentSize: Float, viewportSize: Float): Float {
            val scaled = contentSize * safeScale
            return if (scaled <= viewportSize) (viewportSize - scaled) / 2
            else offset.coerceIn(viewportSize - scaled, 0f)
        }
        return CardChartViewport(safeScale, bound(x, content.width, viewport.width), bound(y, content.height, viewport.height))
    }

    fun transform(content: CardChartSize, viewport: CardChartSize, zoom: Float, pan: CardChartPoint,
                  centroid: CardChartPoint): CardChartViewport {
        if (!zoom.isFinite() || zoom <= 0 || !pan.x.isFinite() || !pan.y.isFinite() ||
            !centroid.x.isFinite() || !centroid.y.isFinite()) return this
        val nextScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = nextScale / scale
        return CardChartViewport(nextScale, centroid.x - (centroid.x - x) * ratio + pan.x,
            centroid.y - (centroid.y - y) * ratio + pan.y).clamped(content, viewport)
    }

    companion object {
        const val MIN_SCALE = 48f / CardEvolutionLayout.NODE_SIZE
        const val MAX_SCALE = 2.5f

        fun fit(content: CardChartSize, viewport: CardChartSize): CardChartViewport {
            val scale = (viewport.width / content.width).coerceIn(MIN_SCALE, 1f)
            return CardChartViewport(scale, (viewport.width - content.width * scale) / 2, 0f)
                .clamped(content, viewport)
        }
    }
}
