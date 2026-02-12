package com.onyx.android.ink.ui

import androidx.compose.ui.geometry.Offset
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.ViewTransform
import kotlin.math.min
import kotlin.math.sqrt

private const val ERASE_HIT_RADIUS_PX = 10f

internal fun calculateBounds(
    points: List<StrokePoint>,
    strokeWidthPadding: Float = 0f,
): StrokeBounds {
    if (points.isEmpty()) {
        return StrokeBounds(x = 0f, y = 0f, w = 0f, h = 0f)
    }
    var minX = points.first().x
    var minY = points.first().y
    var maxX = points.first().x
    var maxY = points.first().y
    for (point in points) {
        minX = min(minX, point.x)
        minY = min(minY, point.y)
        maxX = maxOf(maxX, point.x)
        maxY = maxOf(maxY, point.y)
    }
    val halfPadding = strokeWidthPadding / 2f
    return StrokeBounds(
        x = minX - halfPadding,
        y = minY - halfPadding,
        w = maxX - minX + strokeWidthPadding,
        h = maxY - minY + strokeWidthPadding,
    )
}

internal fun findStrokeToErase(
    screenX: Float,
    screenY: Float,
    strokes: List<Stroke>,
    viewTransform: ViewTransform,
): Stroke? {
    val pageX = viewTransform.screenToPageX(screenX)
    val pageY = viewTransform.screenToPageY(screenY)
    val hitRadius = ERASE_HIT_RADIUS_PX / viewTransform.zoom
    for (stroke in strokes) {
        val expandedBounds = stroke.bounds.expandBy(hitRadius)
        if (isOutsideBounds(pageX, pageY, expandedBounds)) {
            continue
        }
        val points = stroke.points
        // Handle single-point strokes (dot) — check point proximity directly
        if (points.size == 1) {
            val dx = pageX - points[0].x
            val dy = pageY - points[0].y
            if (sqrt(dx * dx + dy * dy) <= hitRadius) {
                return stroke
            }
            continue
        }
        for (index in 0 until points.size - 1) {
            val p1 = Offset(points[index].x, points[index].y)
            val p2 = Offset(points[index + 1].x, points[index + 1].y)
            val distance = pointToSegmentDistance(Offset(pageX, pageY), p1, p2)
            if (distance <= hitRadius) {
                return stroke
            }
        }
    }
    return null
}

private fun StrokeBounds.expandBy(padding: Float): StrokeBounds =
    StrokeBounds(
        x = x - padding,
        y = y - padding,
        w = w + 2 * padding,
        h = h + 2 * padding,
    )

private fun isOutsideBounds(
    pageX: Float,
    pageY: Float,
    bounds: StrokeBounds,
): Boolean {
    val outsideX = pageX < bounds.x || pageX > bounds.x + bounds.w
    val outsideY = pageY < bounds.y || pageY > bounds.y + bounds.h
    return outsideX || outsideY
}

private fun pointToSegmentDistance(
    point: Offset,
    start: Offset,
    end: Offset,
): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) {
        val dxPoint = point.x - start.x
        val dyPoint = point.y - start.y
        return sqrt(dxPoint * dxPoint + dyPoint * dyPoint)
    }
    val t = ((point.x - start.x) * dx + (point.y - start.y) * dy) / lenSq
    val clampedT = t.coerceIn(0f, 1f)
    val nearestX = start.x + clampedT * dx
    val nearestY = start.y + clampedT * dy
    val dxPoint = point.x - nearestX
    val dyPoint = point.y - nearestY
    return sqrt(dxPoint * dxPoint + dyPoint * dyPoint)
}
