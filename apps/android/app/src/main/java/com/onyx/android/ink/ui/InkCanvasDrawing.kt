package com.onyx.android.ink.ui

import android.graphics.Color.parseColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.ink.brush.StockBrushes
import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
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
    if (stroke.points.size < 2) return
    val path = Path()
    val firstPoint = stroke.points.first()
    val (startX, startY) = transform.pageToScreen(firstPoint.x, firstPoint.y)
    path.moveTo(startX, startY)
    for (index in 1 until stroke.points.size) {
        val point = stroke.points[index]
        val (x, y) = transform.pageToScreen(point.x, point.y)
        path.lineTo(x, y)
    }
    drawPath(
        path = path,
        color = Color(parseColor(stroke.style.color)),
        style =
            ComposeStroke(
                width = transform.pageWidthToScreen(stroke.style.baseWidth),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
    )
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
