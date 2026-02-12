package com.onyx.android.ink.ui

import android.graphics.Color.parseColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.ink.brush.StockBrushes
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import kotlin.math.pow
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.ink.brush.Brush as InkBrush

private const val MIN_INK_BRUSH_SIZE = 0.1f
private const val MIN_INK_BRUSH_EPSILON = 0.1f
private const val INK_BRUSH_EPSILON_SCALE = 0.15f
private const val PREVIEW_STROKE_WIDTH_SCALE = 0.12f
private const val MIN_PREVIEW_STROKE_WIDTH = 1f
private const val MIN_SCREEN_STROKE_WIDTH_PX = 1f
private const val ERASER_PREVIEW_ALPHA = 0.6f
private const val PEN_PREVIEW_ALPHA = 0.35f
private const val ERASER_PREVIEW_COLOR: Long = 0xFF6B6B6B
private const val ALPHA_SHIFT_BITS = 24
private const val ALPHA_MASK = 0xFF
private const val RGB_MASK = 0x00FFFFFF
private const val MAX_ALPHA = 255
private const val PRESSURE_FALLBACK = 0.5f
private const val MIN_STROKE_POINTS = 2
private const val MIN_ZOOM_FOR_STROKE_WIDTH = 0.001f
private const val CURVE_MIDPOINT_FACTOR = 0.5f
private const val CATMULL_ROM_TENSION = 0.5f
private const val CATMULL_ROM_SUBDIVISIONS = 8
private const val PRESSURE_GAMMA = 0.6f
private const val TAPER_POINT_COUNT = 5
private const val TAPER_MIN_FACTOR = 0.15f
private const val PATH_CACHE_MAX_ENTRIES = 500

/**
 * Thread-safe LRU-bounded color cache to avoid per-frame Color.parseColor() calls.
 */
internal object ColorCache {
    private const val MAX_SIZE = 64
    private val cache = object : LinkedHashMap<String, Int>(MAX_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean =
            size > MAX_SIZE
    }

    @Synchronized
    fun resolve(hex: String): Int {
        return cache.getOrPut(hex) { parseColor(hex) }
    }
}

internal data class StrokePathCacheEntry(
    val path: Path,
    val perPointWidths: List<Float>,
    val averagePressure: Float,
)

internal class HoverPreviewState {
    var isVisible by mutableStateOf(false)
    var x by mutableStateOf(0f)
    var y by mutableStateOf(0f)
    var tool by mutableStateOf(Tool.PEN)

    fun show(
        x: Float,
        y: Float,
        tool: Tool,
    ) {
        this.x = x
        this.y = y
        this.tool = tool
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }
}

internal fun DrawScope.drawHoverPreview(
    hoverPreviewState: HoverPreviewState,
    brush: Brush,
    viewTransform: ViewTransform,
) {
    if (!hoverPreviewState.isVisible) return
    val size = viewTransform.pageWidthToScreen(brush.baseWidth).coerceAtLeast(MIN_INK_BRUSH_SIZE)
    val radius = size / 2f
    val color =
        if (hoverPreviewState.tool == Tool.ERASER) {
            Color(ERASER_PREVIEW_COLOR)
        } else {
            Color(ColorCache.resolve(brush.color))
        }
    val alpha = if (hoverPreviewState.tool == Tool.ERASER) ERASER_PREVIEW_ALPHA else PEN_PREVIEW_ALPHA
    val strokeWidth = (size * PREVIEW_STROKE_WIDTH_SCALE).coerceAtLeast(MIN_PREVIEW_STROKE_WIDTH)
    drawCircle(
        color = color.copy(alpha = alpha),
        radius = radius,
        center = Offset(hoverPreviewState.x, hoverPreviewState.y),
        style = ComposeStroke(width = strokeWidth),
    )
}

internal fun DrawScope.drawStrokesInWorldSpace(
    strokes: List<Stroke>,
    transform: ViewTransform,
    pathCache: MutableMap<String, StrokePathCacheEntry>,
) {
    boundPathCache(pathCache, strokes)
    val viewportRect = transform.viewportPageRect(size.width, size.height)
    withTransform({
        translate(left = transform.panX, top = transform.panY)
        scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
    }) {
        strokes.forEach { stroke ->
            if (!stroke.isVisibleIn(viewportRect)) {
                return@forEach
            }
            val cacheEntry = pathCache.getOrPut(stroke.id) {
                buildStrokePathCacheEntry(stroke.points, stroke.style)
            }
            val color = Color(ColorCache.resolve(stroke.style.color))
            drawPath(
                path = cacheEntry.path,
                color = color,
                style =
                    ComposeStroke(
                        width =
                            strokeWorldWidth(
                                style = stroke.style,
                                averagePressure = cacheEntry.averagePressure,
                                transform = transform,
                            ),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
            )
        }
    }
}

private fun boundPathCache(
    pathCache: MutableMap<String, StrokePathCacheEntry>,
    activeStrokes: List<Stroke>,
) {
    if (pathCache.size <= PATH_CACHE_MAX_ENTRIES) return
    val activeIds = activeStrokes.mapTo(mutableSetOf()) { it.id }
    val keysToRemove = pathCache.keys.filter { it !in activeIds }
    keysToRemove.forEach { pathCache.remove(it) }
    if (pathCache.size > PATH_CACHE_MAX_ENTRIES) {
        val excess = pathCache.size - PATH_CACHE_MAX_ENTRIES
        pathCache.keys.take(excess).forEach { pathCache.remove(it) }
    }
}

private fun strokeWorldWidth(
    style: StrokeStyle,
    averagePressure: Float,
    transform: ViewTransform,
): Float {
    val pressureAdjusted =
        pressureWidth(
            baseWidth = style.baseWidth,
            minWidthFactor = style.minWidthFactor,
            maxWidthFactor = style.maxWidthFactor,
            pressure = averagePressure,
        )
    val minimumWorldWidth = MIN_SCREEN_STROKE_WIDTH_PX / transform.zoom.coerceAtLeast(MIN_ZOOM_FOR_STROKE_WIDTH)
    return pressureAdjusted.coerceAtLeast(minimumWorldWidth)
}

private fun buildStrokePathCacheEntry(
    points: List<StrokePoint>,
    style: StrokeStyle,
): StrokePathCacheEntry {
    val samples = catmullRomSmooth(points)
    val path = Path()
    if (samples.isNotEmpty()) {
        path.moveTo(samples.first().x, samples.first().y)
    }
    if (samples.size == MIN_STROKE_POINTS) {
        path.lineTo(samples.last().x, samples.last().y)
    } else if (samples.size > MIN_STROKE_POINTS) {
        for (index in 1 until samples.lastIndex) {
            val current = samples[index]
            val next = samples[index + 1]
            val midX = (current.x + next.x) * CURVE_MIDPOINT_FACTOR
            val midY = (current.y + next.y) * CURVE_MIDPOINT_FACTOR
            path.quadraticBezierTo(current.x, current.y, midX, midY)
        }
        val last = samples.last()
        path.lineTo(last.x, last.y)
    }
    val perPointWidths = computePerPointWidths(samples, style)
    val averagePressure = samples.mapNotNull { it.pressure }.average().toFloat().coerceIn(0f, 1f)
    return StrokePathCacheEntry(path = path, perPointWidths = perPointWidths, averagePressure = averagePressure)
}

private fun ViewTransform.viewportPageRect(
    viewportWidth: Float,
    viewportHeight: Float,
): Rect {
    val left = screenToPageX(0f)
    val top = screenToPageY(0f)
    val right = screenToPageX(viewportWidth)
    val bottom = screenToPageY(viewportHeight)
    return Rect(
        left = minOf(left, right),
        top = minOf(top, bottom),
        right = maxOf(left, right),
        bottom = maxOf(top, bottom),
    )
}

private fun Stroke.isVisibleIn(viewportRect: Rect): Boolean {
    val strokeRect =
        Rect(
            left = bounds.x,
            top = bounds.y,
            right = bounds.x + bounds.w,
            bottom = bounds.y + bounds.h,
        )
    return strokeRect.overlaps(viewportRect)
}

internal data class StrokeRenderPoint(
    val x: Float,
    val y: Float,
    val pressure: Float?,
)

/**
 * Catmull-Rom spline interpolation replaces the old 3-point moving average.
 *
 * - Provides C¹ continuity (smooth tangents at every control point)
 * - Passes through all original points — preserves user intent
 * - Tension parameter (0.5 default) balances smoothness vs sharpness
 * - Subdivisions between each pair of control points yield fluid curves
 *   instead of the polygonal/jagged lines the 3-point average produced.
 */
internal fun catmullRomSmooth(points: List<StrokePoint>): List<StrokeRenderPoint> {
    if (points.size <= MIN_STROKE_POINTS) {
        return points.map { point -> StrokeRenderPoint(point.x, point.y, point.p) }
    }

    val result = ArrayList<StrokeRenderPoint>(points.size * CATMULL_ROM_SUBDIVISIONS)
    val first = points.first()
    result += StrokeRenderPoint(first.x, first.y, first.p)

    for (index in 0 until points.size - 1) {
        val p0 = points[(index - 1).coerceAtLeast(0)]
        val p1 = points[index]
        val p2 = points[index + 1]
        val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]

        val p0Pressure = p0.p ?: PRESSURE_FALLBACK
        val p1Pressure = p1.p ?: PRESSURE_FALLBACK
        val p2Pressure = p2.p ?: PRESSURE_FALLBACK
        val p3Pressure = p3.p ?: PRESSURE_FALLBACK

        for (step in 1..CATMULL_ROM_SUBDIVISIONS) {
            val t = step.toFloat() / CATMULL_ROM_SUBDIVISIONS
            val x = catmullRomValue(p0.x, p1.x, p2.x, p3.x, t)
            val y = catmullRomValue(p0.y, p1.y, p2.y, p3.y, t)
            val pressure = catmullRomValue(p0Pressure, p1Pressure, p2Pressure, p3Pressure, t)
            result += StrokeRenderPoint(x, y, pressure.coerceIn(0f, 1f))
        }
    }
    return result
}

/**
 * Evaluates a single Catmull-Rom spline component at parameter t ∈ [0,1].
 *
 * Uses the standard matrix form with tension = 0.5:
 *   q(t) = 0.5 * [ (-t + 2t² - t³)·v0 + (2 - 5t² + 3t³)·v1 +
 *                   (t + 4t² - 3t³)·v2 + (-t² + t³)·v3 ]
 */
internal fun catmullRomValue(
    v0: Float,
    v1: Float,
    v2: Float,
    v3: Float,
    t: Float,
): Float {
    val t2 = t * t
    val t3 = t2 * t
    return CATMULL_ROM_TENSION * (
        (-t + 2f * t2 - t3) * v0 +
            (2f - 5f * t2 + 3f * t3) * v1 +
            (t + 4f * t2 - 3f * t3) * v2 +
            (-t2 + t3) * v3
        )
}

/**
 * Computes per-point stroke widths that incorporate:
 * 1. Non-linear pressure mapping (gamma curve) for more responsive light-pressure input
 * 2. Start/end tapering that narrows the stroke at the first and last few points
 *
 * The gamma curve (`pressure^gamma` with gamma=0.6) makes light pressure more
 * responsive, addressing the "dead" / uniform-width feel of the old linear mapping.
 *
 * Tapering over the first/last TAPER_POINT_COUNT points fades width from
 * TAPER_MIN_FACTOR to 1.0, mimicking the natural ink taper of a real pen stroke.
 */
internal fun computePerPointWidths(
    points: List<StrokeRenderPoint>,
    style: StrokeStyle,
): List<Float> {
    if (points.isEmpty()) return emptyList()
    val count = points.size
    return List(count) { index ->
        val pressure = points[index].pressure ?: PRESSURE_FALLBACK
        val gammaPressure = pressure.coerceIn(0f, 1f).pow(PRESSURE_GAMMA)
        val factor = style.minWidthFactor + (style.maxWidthFactor - style.minWidthFactor) * gammaPressure
        val baseAdjusted = style.baseWidth * factor

        val taperFactor = computeTaperFactor(index, count)
        baseAdjusted * taperFactor
    }
}

/**
 * Returns a multiplier in [TAPER_MIN_FACTOR, 1.0] that fades width at the
 * start and end of a stroke to create natural-looking tapering.
 */
internal fun computeTaperFactor(
    index: Int,
    totalPoints: Int,
): Float {
    if (totalPoints <= 1) return 1f
    val taperLength = TAPER_POINT_COUNT.coerceAtMost(totalPoints / 2)
    if (taperLength <= 0) return 1f

    val startTaper = if (index < taperLength) {
        TAPER_MIN_FACTOR + (1f - TAPER_MIN_FACTOR) * (index.toFloat() / taperLength)
    } else {
        1f
    }
    val distFromEnd = totalPoints - 1 - index
    val endTaper = if (distFromEnd < taperLength) {
        TAPER_MIN_FACTOR + (1f - TAPER_MIN_FACTOR) * (distFromEnd.toFloat() / taperLength)
    } else {
        1f
    }
    return minOf(startTaper, endTaper)
}

internal fun pressureWidth(
    baseWidth: Float,
    minWidthFactor: Float,
    maxWidthFactor: Float,
    pressure: Float?,
    pressureFallback: Float = PRESSURE_FALLBACK,
): Float {
    val resolvedPressure = (pressure ?: pressureFallback).coerceIn(0f, 1f)
    val factor = minWidthFactor + (maxWidthFactor - minWidthFactor) * resolvedPressure
    return baseWidth * factor
}

internal fun Brush.toInkBrush(
    viewTransform: ViewTransform,
    alphaMultiplier: Float = 1f,
): InkBrush {
    val family =
        when (tool) {
            Tool.PEN -> StockBrushes.pressurePenLatest
            Tool.HIGHLIGHTER -> StockBrushes.highlighterLatest
            Tool.ERASER -> StockBrushes.markerLatest
        }
    val size = viewTransform.pageWidthToScreen(baseWidth).coerceAtLeast(MIN_INK_BRUSH_SIZE)
    val epsilon = (size * INK_BRUSH_EPSILON_SCALE).coerceAtLeast(MIN_INK_BRUSH_EPSILON)
    val baseColor = ColorCache.resolve(color)
    val adjustedColor = applyAlpha(baseColor, alphaMultiplier)
    return InkBrush.createWithColorIntArgb(
        family,
        adjustedColor,
        size,
        epsilon,
    )
}

private fun applyAlpha(
    colorInt: Int,
    alphaMultiplier: Float,
): Int {
    val clampedMultiplier = alphaMultiplier.coerceIn(0f, 1f)
    val baseAlpha = (colorInt ushr ALPHA_SHIFT_BITS) and ALPHA_MASK
    val adjustedAlpha = (baseAlpha * clampedMultiplier).toInt().coerceIn(0, MAX_ALPHA)
    return (adjustedAlpha shl ALPHA_SHIFT_BITS) or (colorInt and RGB_MASK)
}
