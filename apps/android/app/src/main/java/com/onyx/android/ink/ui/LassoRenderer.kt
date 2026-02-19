@file:Suppress("MagicNumber", "UnusedParameter")

package com.onyx.android.ink.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.onyx.android.ink.model.LassoSelection
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.ViewTransform

private const val SELECTION_STROKE_WIDTH = 2f
private const val HANDLE_RADIUS = 8f
private const val DASH_INTERVAL = 8f

internal fun DrawScope.drawLassoOverlay(
    selection: LassoSelection,
    transform: ViewTransform,
) {
    if (selection.lassoPath.isNotEmpty()) {
        drawLassoPath(selection.lassoPath, transform)
    }
    if (selection.hasSelection && selection.selectionBounds != null) {
        drawSelectionBounds(selection.selectionBounds, transform)
    }
}

private fun DrawScope.drawLassoPath(
    path: List<Pair<Float, Float>>,
    transform: ViewTransform,
) {
    if (path.size < 2) return
    val screenPath = Path()
    val first = path.first()
    val (screenX, screenY) = transform.pageToScreen(first.first, first.second)
    screenPath.moveTo(screenX, screenY)
    for (i in 1 until path.size) {
        val (x, y) = transform.pageToScreen(path[i].first, path[i].second)
        screenPath.lineTo(x, y)
    }
    screenPath.close()
    drawPath(
        path = screenPath,
        color = Color.Blue,
        style =
            androidx.compose.ui.graphics.drawscope.Stroke(
                width = SELECTION_STROKE_WIDTH,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_INTERVAL, DASH_INTERVAL)),
            ),
    )
}

private fun DrawScope.drawSelectionBounds(
    bounds: StrokeBounds,
    transform: ViewTransform,
) {
    val left = transform.pageToScreenX(bounds.x)
    val top = transform.pageToScreenY(bounds.y)
    val right = transform.pageToScreenX(bounds.x + bounds.w)
    val bottom = transform.pageToScreenY(bounds.y + bounds.h)
    val rectPath = Path()
    rectPath.addRect(
        androidx.compose.ui.geometry
            .Rect(left, top, right, bottom),
    )
    drawPath(
        path = rectPath,
        color = Color(0xFF2196F3),
        style =
            androidx.compose.ui.graphics.drawscope
                .Stroke(width = SELECTION_STROKE_WIDTH),
    )
    drawSelectionHandles(left, top, right, bottom)
}

private fun DrawScope.drawSelectionHandles(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val handleColor = Color.White
    val handleBorderColor = Color(0xFF2196F3)
    val handles =
        listOf(
            Offset(left, top),
            Offset(right, top),
            Offset(left, bottom),
            Offset(right, bottom),
            Offset((left + right) / 2, top),
            Offset((left + right) / 2, bottom),
            Offset(left, (top + bottom) / 2),
            Offset(right, (top + bottom) / 2),
        )
    for (handle in handles) {
        drawCircle(
            color = handleBorderColor,
            radius = HANDLE_RADIUS,
            center = handle,
        )
        drawCircle(
            color = handleColor,
            radius = HANDLE_RADIUS - SELECTION_STROKE_WIDTH,
            center = handle,
        )
    }
}

internal fun DrawScope.drawSelectedStrokesHighlight(
    strokes: List<Stroke>,
    selectedIds: Set<String>,
    transform: ViewTransform,
    pathCache: MutableMap<String, StrokePathCacheEntry>,
) {
    for (stroke in strokes) {
        if (stroke.id !in selectedIds) continue
        val entry =
            pathCache.getOrPut(stroke.id) {
                val samples = catmullRomSmooth(stroke.points, stroke.style.smoothingLevel)
                val widths = computePerPointWidths(samples, stroke.style)
                StrokePathCacheEntry(
                    path = buildVariableWidthOutline(samples, widths),
                )
            }
        val highlightPath = entry.path
        drawPath(
            path = highlightPath,
            color = Color(0x332196F3),
            blendMode = BlendMode.SrcOver,
        )
    }
}
