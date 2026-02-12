package com.onyx.android.ink.ui

import android.graphics.Color.parseColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
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
private const val ERASER_PREVIEW_ALPHA = 0.6f
private const val PEN_PREVIEW_ALPHA = 0.35f
private const val ERASER_PREVIEW_COLOR: Long = 0xFF6B6B6B
private const val ALPHA_SHIFT_BITS = 24
private const val ALPHA_MASK = 0xFF
private const val RGB_MASK = 0x00FFFFFF
private const val MAX_ALPHA = 255
private const val PRESSURE_FALLBACK = 0.5f
private const val MIN_STROKE_POINTS = 2

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

internal fun DrawScope.drawStroke(
    stroke: Stroke,
    transform: ViewTransform,
) {
    drawStrokePoints(points = stroke.points, style = stroke.style, transform = transform)
}

internal fun DrawScope.drawStrokePoints(
    points: List<StrokePoint>,
    style: StrokeStyle,
    transform: ViewTransform,
) {
    if (points.size < MIN_STROKE_POINTS) return
    val samples = smoothStrokePoints(points)
    if (samples.size < MIN_STROKE_POINTS) return

    val color = Color(parseColor(style.color))
    for (index in 1 until samples.size) {
        val previousPoint = samples[index - 1]
        val currentPoint = samples[index]
        val (startX, startY) = transform.pageToScreen(previousPoint.x, previousPoint.y)
        val (endX, endY) = transform.pageToScreen(currentPoint.x, currentPoint.y)
        val segmentWidth =
            transform.pageWidthToScreen(
                pressureWidth(
                    baseWidth = style.baseWidth,
                    minWidthFactor = style.minWidthFactor,
                    maxWidthFactor = style.maxWidthFactor,
                    pressure = currentPoint.pressure,
                ),
            ).coerceAtLeast(MIN_INK_BRUSH_SIZE)
        drawLine(
            color = color,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = segmentWidth,
            cap = StrokeCap.Round,
        )
    }
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
