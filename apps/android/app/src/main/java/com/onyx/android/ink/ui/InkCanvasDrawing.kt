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

internal data class StrokePathCacheEntry(
    val path: Path,
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
            Color(parseColor(brush.color))
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
    val viewportRect = transform.viewportPageRect(size.width, size.height)
    withTransform({
        translate(left = transform.panX, top = transform.panY)
        scale(scaleX = transform.zoom, scaleY = transform.zoom, pivot = Offset.Zero)
    }) {
        strokes.forEach { stroke ->
            if (!stroke.isVisibleIn(viewportRect)) {
                return@forEach
            }
            val cacheEntry = pathCache.getOrPut(stroke.id) { buildStrokePathCacheEntry(stroke.points) }
            drawPath(
                path = cacheEntry.path,
                color = Color(parseColor(stroke.style.color)),
                style =
                    ComposeStroke(
                        width = strokeWorldWidth(
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
    val minimumWorldWidth = MIN_SCREEN_STROKE_WIDTH_PX / transform.zoom.coerceAtLeast(0.001f)
    return pressureAdjusted.coerceAtLeast(minimumWorldWidth)
}

private fun buildStrokePathCacheEntry(points: List<StrokePoint>): StrokePathCacheEntry {
    val samples = smoothStrokePoints(points)
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
            val midX = (current.x + next.x) * 0.5f
            val midY = (current.y + next.y) * 0.5f
            path.quadraticBezierTo(current.x, current.y, midX, midY)
        }
        val last = samples.last()
        path.lineTo(last.x, last.y)
    }
    val averagePressure = samples.mapNotNull { it.pressure }.average().toFloat().coerceIn(0f, 1f)
    return StrokePathCacheEntry(path = path, averagePressure = averagePressure)
}

private fun ViewTransform.viewportPageRect(
    viewportWidth: Float,
    viewportHeight: Float,
): Rect {
    val (left, top) = screenToPage(0f, 0f)
    val (right, bottom) = screenToPage(viewportWidth, viewportHeight)
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

private data class StrokeRenderPoint(
    val x: Float,
    val y: Float,
    val pressure: Float?,
)

private fun smoothStrokePoints(points: List<StrokePoint>): List<StrokeRenderPoint> {
    if (points.size <= MIN_STROKE_POINTS) {
        return points.map { point -> StrokeRenderPoint(point.x, point.y, point.p) }
    }

    val smoothed = ArrayList<StrokeRenderPoint>(points.size)
    val first = points.first()
    smoothed += StrokeRenderPoint(first.x, first.y, first.p)
    for (index in 1 until points.lastIndex) {
        val previous = points[index - 1]
        val current = points[index]
        val next = points[index + 1]
        val previousPressure = previous.p ?: PRESSURE_FALLBACK
        val currentPressure = current.p ?: PRESSURE_FALLBACK
        val nextPressure = next.p ?: PRESSURE_FALLBACK
        smoothed +=
            StrokeRenderPoint(
                x = (previous.x + current.x + next.x) / 3f,
                y = (previous.y + current.y + next.y) / 3f,
                pressure = (previousPressure + currentPressure + nextPressure) / 3f,
            )
    }
    val last = points.last()
    smoothed += StrokeRenderPoint(last.x, last.y, last.p)
    return smoothed
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
    val baseColor = parseColor(color)
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
