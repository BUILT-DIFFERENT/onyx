@file:Suppress("LongParameterList", "MagicNumber", "ReturnCount", "TooManyFunctions")

package com.onyx.android.ink.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import kotlin.math.roundToInt
import kotlin.math.sqrt
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.ink.brush.Brush as InkBrush

private const val MIN_INK_BRUSH_SIZE = 0.1f
private const val MIN_INK_BRUSH_EPSILON = 0.1f
private const val INK_BRUSH_EPSILON_SCALE = 0.15f
private const val PREVIEW_STROKE_WIDTH_SCALE = 0.12f
private const val MIN_PREVIEW_STROKE_WIDTH = 1f
private const val ERASER_PREVIEW_ALPHA = 0.6f
private const val PEN_PREVIEW_ALPHA = 0.35f
private const val ERASER_PREVIEW_COLOR: Long = 0xFF6B6B6B
private const val ALPHA_SHIFT_BITS = 24
private const val ALPHA_MASK = 0xFF
private const val RGB_MASK = 0x00FFFFFF
private const val MAX_ALPHA = 255
internal const val PRESSURE_FALLBACK = 0.5f
private const val MIN_STROKE_POINTS = 2
private const val CENTRIPETAL_ALPHA = 0.5f
private const val MIN_CATMULL_ROM_SUBDIVISIONS = 2
private const val MAX_CATMULL_ROM_SUBDIVISIONS = 10
internal const val PRESSURE_GAMMA = 0.6f
private const val TAPER_POINT_COUNT = 5
private const val TAPER_MIN_FACTOR = 0.35f
private const val SHORT_STROKE_TAPER_THRESHOLD = 8
private const val SHORT_STROKE_TAPER_REDUCTION = 0.35f
private const val MIN_RENDERED_WIDTH_FACTOR = 0.18f
private const val PATH_CACHE_MAX_ENTRIES = 500
private const val MIN_WIDTH_FOR_OUTLINE = 0.01f
internal const val HIGHLIGHTER_STROKE_ALPHA = 0.35f

/**
 * Thread-safe LRU-bounded color cache to avoid per-frame Color.parseColor() calls.
 */
internal object ColorCache {
    private const val MAX_SIZE = 64
    private val cache =
        object : LinkedHashMap<String, Int>(MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean = size > MAX_SIZE
        }

    @Synchronized
    fun resolve(hex: String): Int = cache.getOrPut(hex) { parseHexColor(hex) }
}

private fun parseHexColor(hex: String): Int {
    val trimmed = hex.trim()
    require(trimmed.startsWith("#")) { "Color must start with #, received: $hex" }
    val digits = trimmed.substring(1)
    val parsed =
        digits.toLongOrNull(16)
            ?: throw IllegalArgumentException("Invalid hex color: $hex")
    return when (digits.length) {
        6 -> (parsed or 0xFF000000L).toInt()
        8 -> parsed.toInt()
        else -> throw IllegalArgumentException("Unsupported hex color length: ${digits.length}")
    }
}

/**
 * Cached filled-outline path for a stroke. The path is a closed shape whose
 * boundary follows the per-point widths (pressure + tapering), giving each
 * stroke natural variable-width appearance. Drawn with `drawPath(Fill)` —
 * no stroke width parameter needed.
 */
internal data class StrokePathCacheEntry(
    val path: Path,
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
            val cacheEntry =
                pathCache.getOrPut(stroke.id) {
                    buildStrokePathCacheEntry(stroke.points, stroke.style)
                }
            val baseColor = Color(ColorCache.resolve(stroke.style.color))
            if (stroke.style.tool == Tool.HIGHLIGHTER) {
                drawPath(
                    path = cacheEntry.path,
                    color = baseColor,
                    alpha = HIGHLIGHTER_STROKE_ALPHA,
                    blendMode = BlendMode.Multiply,
                )
            } else {
                drawPath(
                    path = cacheEntry.path,
                    color = baseColor,
                )
            }
        }
    }
}

private fun boundPathCache(
    pathCache: MutableMap<String, StrokePathCacheEntry>,
    activeStrokes: List<Stroke>,
) {
    if (pathCache.size <= PATH_CACHE_MAX_ENTRIES) return
    val activeIds = activeStrokes.mapTo(mutableSetOf()) { it.id }
    val iter = pathCache.keys.iterator()
    while (iter.hasNext() && pathCache.size > PATH_CACHE_MAX_ENTRIES) {
        val key = iter.next()
        if (key !in activeIds) {
            iter.remove()
        }
    }
}

/**
 * Build a filled-outline path for a stroke. Instead of drawing a stroked center line
 * with uniform width, we compute two offset curves (left and right edges) based on
 * per-point widths, then close them into a filled shape. This produces proper
 * variable-width strokes with pressure sensitivity and tapering.
 *
 * For single-point strokes, a small circle is drawn instead.
 */
private fun buildStrokePathCacheEntry(
    points: List<StrokePoint>,
    style: StrokeStyle,
): StrokePathCacheEntry {
    val samples = catmullRomSmooth(points, style.smoothingLevel)
    val widths = computePerPointWidths(samples, style)
    val path = buildVariableWidthOutline(samples, widths)
    return StrokePathCacheEntry(path = path)
}

/**
 * Builds a closed [Path] representing the filled outline of a variable-width stroke.
 *
 * Algorithm:
 * 1. For each sample point, compute the unit normal perpendicular to the stroke direction.
 * 2. Offset left and right by half the per-point width along that normal.
 * 3. Walk forward along the left edge, then backward along the right edge, closing the shape.
 * 4. For end caps, add a semicircle at the start and end for a rounded appearance.
 */
internal fun buildVariableWidthOutline(
    samples: List<StrokeRenderPoint>,
    widths: List<Float>,
): Path {
    val path = Path()
    val geometry = computeStrokeOutlineGeometry(samples, widths) ?: return path

    if (samples.size == 1) {
        val p = samples[0]
        val r = (widths[0] / 2f).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
        path.addOval(Rect(p.x - r, p.y - r, p.x + r, p.y + r))
        return path
    }
    val count = geometry.count
    val leftX = geometry.leftX
    val leftY = geometry.leftY
    val rightX = geometry.rightX
    val rightY = geometry.rightY

    // Build the outline as a single closed contour:
    // 1. Start at right edge of first point
    // 2. Start cap (semicircle from right[0] → left[0])
    // 3. Forward along left edge to last point
    // 4. End cap (semicircle from left[last] → right[last])
    // 5. Backward along right edge to first point
    // 6. Close

    path.moveTo(rightX[0], rightY[0])

    // Start cap: semicircle from right to left at first point
    val startHalfW = (widths[0] / 2f).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
    path.addRoundCap(
        samples[0].x,
        samples[0].y,
        rightX[0],
        rightY[0],
        leftX[0],
        leftY[0],
        startHalfW,
    )

    // Forward along left edge
    for (i in 1 until count) {
        path.lineTo(leftX[i], leftY[i])
    }

    // End cap: semicircle from left to right at last point
    val lastIdx = count - 1
    val endHalfW = (widths[lastIdx] / 2f).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
    path.addRoundCap(
        samples[lastIdx].x,
        samples[lastIdx].y,
        leftX[lastIdx],
        leftY[lastIdx],
        rightX[lastIdx],
        rightY[lastIdx],
        endHalfW,
    )

    // Backward along right edge
    for (i in lastIdx - 1 downTo 0) {
        path.lineTo(rightX[i], rightY[i])
    }

    path.close()
    return path
}

internal data class StrokeOutlineGeometry(
    val count: Int,
    val leftX: FloatArray,
    val leftY: FloatArray,
    val rightX: FloatArray,
    val rightY: FloatArray,
)

internal fun computeStrokeOutlineGeometry(
    samples: List<StrokeRenderPoint>,
    widths: List<Float>,
): StrokeOutlineGeometry? {
    if (samples.isEmpty() || widths.isEmpty()) {
        return null
    }
    require(widths.size >= samples.size) {
        "widths (${widths.size}) must have at least as many entries as samples (${samples.size})"
    }
    val count = samples.size
    val leftX = FloatArray(count)
    val leftY = FloatArray(count)
    val rightX = FloatArray(count)
    val rightY = FloatArray(count)

    if (count == 1) {
        val x = samples[0].x
        val y = samples[0].y
        leftX[0] = x
        leftY[0] = y
        rightX[0] = x
        rightY[0] = y
        return StrokeOutlineGeometry(
            count = count,
            leftX = leftX,
            leftY = leftY,
            rightX = rightX,
            rightY = rightY,
        )
    }

    for (i in 0 until count) {
        val halfW = (widths[i] / 2f).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
        val (nx, ny) = computeNormal(samples, i)
        leftX[i] = samples[i].x + nx * halfW
        leftY[i] = samples[i].y + ny * halfW
        rightX[i] = samples[i].x - nx * halfW
        rightY[i] = samples[i].y - ny * halfW
    }

    return StrokeOutlineGeometry(
        count = count,
        leftX = leftX,
        leftY = leftY,
        rightX = rightX,
        rightY = rightY,
    )
}

/**
 * Adds a semicircular end cap by drawing a quadratic Bézier arc from one side to the other.
 * The control point is offset perpendicular to the chord (from → to) by the radius,
 * creating an arc that approximates a semicircle.
 */
private fun Path.addRoundCap(
    cx: Float,
    cy: Float,
    fromX: Float,
    fromY: Float,
    toX: Float,
    toY: Float,
    radius: Float,
) {
    // Control point perpendicular to chord (from → to), offset by radius
    val dx = toX - fromX
    val dy = toY - fromY
    val perpX = -dy
    val perpY = dx
    val len = sqrt(perpX * perpX + perpY * perpY)
    if (len < MIN_WIDTH_FOR_OUTLINE) {
        lineTo(toX, toY)
        return
    }
    val nx = perpX / len * radius
    val ny = perpY / len * radius
    quadraticBezierTo(cx + nx, cy + ny, toX, toY)
}

/**
 * Computes the unit normal (perpendicular) at sample index [i].
 * Uses central differences for interior points and forward/backward differences at endpoints.
 */
private fun computeNormal(
    samples: List<StrokeRenderPoint>,
    i: Int,
): Pair<Float, Float> {
    val dx: Float
    val dy: Float
    when {
        i == 0 -> {
            dx = samples[1].x - samples[0].x
            dy = samples[1].y - samples[0].y
        }

        i == samples.lastIndex -> {
            dx = samples[i].x - samples[i - 1].x
            dy = samples[i].y - samples[i - 1].y
        }

        else -> {
            dx = samples[i + 1].x - samples[i - 1].x
            dy = samples[i + 1].y - samples[i - 1].y
        }
    }
    val len = sqrt(dx * dx + dy * dy)
    return if (len < MIN_WIDTH_FOR_OUTLINE) {
        Pair(0f, -1f)
    } else {
        Pair(-dy / len, dx / len)
    }
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
internal fun catmullRomSmooth(
    points: List<StrokePoint>,
    smoothingLevel: Float = 0.35f,
): List<StrokeRenderPoint> {
    if (points.size <= MIN_STROKE_POINTS) {
        return points.map { point -> StrokeRenderPoint(point.x, point.y, point.p) }
    }

    val clampedSmoothing = smoothingLevel.coerceIn(0f, 1f)
    if (clampedSmoothing <= 0.01f) {
        return points.map { point -> StrokeRenderPoint(point.x, point.y, point.p) }
    }
    val subdivisions =
        (
            MIN_CATMULL_ROM_SUBDIVISIONS +
                (MAX_CATMULL_ROM_SUBDIVISIONS - MIN_CATMULL_ROM_SUBDIVISIONS) * clampedSmoothing
        ).roundToInt().coerceIn(MIN_CATMULL_ROM_SUBDIVISIONS, MAX_CATMULL_ROM_SUBDIVISIONS)

    val result = ArrayList<StrokeRenderPoint>(points.size * subdivisions)
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

        val t0 = 0f
        val t1 = t0 + centripetalStep(p0.x, p0.y, p1.x, p1.y)
        val t2 = t1 + centripetalStep(p1.x, p1.y, p2.x, p2.y)
        val t3 = t2 + centripetalStep(p2.x, p2.y, p3.x, p3.y)
        if (t2 <= t1) {
            result += StrokeRenderPoint(p2.x, p2.y, p2.p)
            continue
        }
        for (step in 1..subdivisions) {
            val t = t1 + (t2 - t1) * (step.toFloat() / subdivisions)
            val x = centripetalCatmullRomValue(p0.x, p1.x, p2.x, p3.x, t, t0, t1, t2, t3)
            val y = centripetalCatmullRomValue(p0.y, p1.y, p2.y, p3.y, t, t0, t1, t2, t3)
            val pressure =
                centripetalCatmullRomValue(
                    p0Pressure,
                    p1Pressure,
                    p2Pressure,
                    p3Pressure,
                    t,
                    t0,
                    t1,
                    t2,
                    t3,
                )
            result += StrokeRenderPoint(x, y, pressure.coerceIn(0f, 1f))
        }
    }
    return result
}

/**
 * Uses centripetal parameterization to reduce overshoot and endpoint artifacts.
 */
internal fun centripetalCatmullRomValue(
    v0: Float,
    v1: Float,
    v2: Float,
    v3: Float,
    t: Float,
    t0: Float,
    t1: Float,
    t2: Float,
    t3: Float,
): Float {
    val safeT1 = if (t1 <= t0) t0 + 0.0001f else t1
    val safeT2 = if (t2 <= safeT1) safeT1 + 0.0001f else t2
    val safeT3 = if (t3 <= safeT2) safeT2 + 0.0001f else t3

    val a1 = lerpParameter(v0, v1, t0, safeT1, t)
    val a2 = lerpParameter(v1, v2, safeT1, safeT2, t)
    val a3 = lerpParameter(v2, v3, safeT2, safeT3, t)

    val b1 = lerpParameter(a1, a2, t0, safeT2, t)
    val b2 = lerpParameter(a2, a3, safeT1, safeT3, t)

    return lerpParameter(b1, b2, safeT1, safeT2, t)
}

private fun centripetalStep(
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
): Float {
    val dx = x1 - x0
    val dy = y1 - y0
    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
    return distance.pow(CENTRIPETAL_ALPHA)
}

private fun lerpParameter(
    v0: Float,
    v1: Float,
    t0: Float,
    t1: Float,
    t: Float,
): Float {
    if (t1 <= t0) {
        return v1
    }
    val ratio = ((t - t0) / (t1 - t0)).coerceIn(0f, 1f)
    return v0 + (v1 - v0) * ratio
}

/**
 * Compatibility wrapper for tests and call sites that still evaluate Catmull-Rom in [0, 1].
 */
internal fun catmullRomValue(
    v0: Float,
    v1: Float,
    v2: Float,
    v3: Float,
    t: Float,
): Float {
    val normalizedT = t.coerceIn(0f, 1f)
    // Map compatibility t in [0, 1] to the active segment [t1, t2].
    val segmentT = 1f + normalizedT
    return centripetalCatmullRomValue(v0, v1, v2, v3, segmentT, 0f, 1f, 2f, 3f)
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
    val minRenderedWidth = (style.baseWidth * MIN_RENDERED_WIDTH_FACTOR).coerceAtLeast(MIN_WIDTH_FOR_OUTLINE)
    return List(count) { index ->
        val pressure = points[index].pressure ?: PRESSURE_FALLBACK
        val gammaPressure = applyPressureGamma(pressure)
        val factor = style.minWidthFactor + (style.maxWidthFactor - style.minWidthFactor) * gammaPressure
        val baseAdjusted = style.baseWidth * factor

        val taperFactor =
            computeTaperFactor(
                index = index,
                totalPoints = count,
                taperStrength = style.endTaperStrength,
            )
        (baseAdjusted * taperFactor).coerceAtLeast(minRenderedWidth)
    }
}

internal fun applyPressureGamma(pressure: Float): Float = pressure.coerceIn(0f, 1f).pow(PRESSURE_GAMMA)

/**
 * Returns a multiplier in [TAPER_MIN_FACTOR, 1.0] that fades width at the
 * start and end of a stroke to create natural-looking tapering.
 */
internal fun computeTaperFactor(
    index: Int,
    totalPoints: Int,
    taperStrength: Float = 0.35f,
): Float {
    if (totalPoints <= 1) {
        return 1f
    }
    val clampedStrength = taperStrength.coerceIn(0f, 1f)
    if (clampedStrength <= 0f) {
        return 1f
    }
    if (totalPoints <= SHORT_STROKE_TAPER_THRESHOLD) {
        return 1f - (SHORT_STROKE_TAPER_REDUCTION * clampedStrength)
    }
    val taperLengthBase = maxOf(1, (TAPER_POINT_COUNT * clampedStrength).roundToInt())
    val taperLength = taperLengthBase.coerceAtMost(totalPoints / 2)
    if (taperLength <= 0) {
        return 1f
    }

    val startTaper =
        if (index < taperLength) {
            TAPER_MIN_FACTOR + (1f - TAPER_MIN_FACTOR) * (index.toFloat() / taperLength)
        } else {
            1f
        }
    val distFromEnd = totalPoints - 1 - index
    val endTaper =
        if (distFromEnd < taperLength) {
            TAPER_MIN_FACTOR + (1f - TAPER_MIN_FACTOR) * (distFromEnd.toFloat() / taperLength)
        } else {
            1f
        }
    val rawFactor = minOf(startTaper, endTaper)
    return 1f - clampedStrength * (1f - rawFactor)
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
            Tool.LASSO -> StockBrushes.pressurePenLatest
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
