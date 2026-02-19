@file:Suppress("MatchingDeclarationName", "ReturnCount", "LongParameterList")

package com.onyx.android.ink.algorithm

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.ui.calculateBounds
import java.util.UUID

data class StrokeSplit(
    val originalStrokeId: String,
    val segments: List<Stroke>,
)

fun splitStrokeAtTouchedIndices(
    stroke: Stroke,
    touchedIndices: Set<Int>,
): StrokeSplit {
    if (touchedIndices.isEmpty()) {
        return StrokeSplit(stroke.id, listOf(stroke))
    }
    val points = stroke.points
    if (points.isEmpty()) {
        return StrokeSplit(stroke.id, listOf(stroke))
    }
    val sortedTouched = touchedIndices.sorted()
    val segments = mutableListOf<Stroke>()
    var segmentStart = 0
    for (touchedIdx in sortedTouched) {
        val segmentEnd = touchedIdx - 1
        if (segmentEnd >= segmentStart) {
            val segmentPoints = points.subList(segmentStart, segmentEnd + 1).toList()
            if (segmentPoints.isNotEmpty()) {
                segments.add(createSegment(stroke, segmentPoints))
            }
        }
        segmentStart = touchedIdx + 1
    }
    if (segmentStart < points.size) {
        val remainingPoints = points.subList(segmentStart, points.size).toList()
        if (remainingPoints.isNotEmpty()) {
            segments.add(createSegment(stroke, remainingPoints))
        }
    }
    if (segments.isEmpty()) {
        return StrokeSplit(stroke.id, emptyList())
    }
    return StrokeSplit(stroke.id, segments)
}

private fun createSegment(
    original: Stroke,
    points: List<StrokePoint>,
): Stroke {
    val bounds = calculateBounds(points, original.style.baseWidth)
    return Stroke(
        id = UUID.randomUUID().toString(),
        points = points,
        style = original.style,
        bounds = bounds,
        createdAt = original.createdAt,
        createdLamport = original.createdLamport,
    )
}

fun findTouchedIndices(
    stroke: Stroke,
    eraserPathPoints: List<Pair<Float, Float>>,
    eraserRadius: Float,
): Set<Int> {
    if (stroke.points.isEmpty() || eraserPathPoints.isEmpty()) {
        return emptySet()
    }
    val touched = mutableSetOf<Int>()
    for (i in stroke.points.indices) {
        val pt = stroke.points[i]
        if (isPointTouchedByPath(pt.x, pt.y, eraserPathPoints, eraserRadius)) {
            touched.add(i)
        }
    }
    return touched
}

fun isPointTouchedByPath(
    pointX: Float,
    pointY: Float,
    pathPoints: List<Pair<Float, Float>>,
    radius: Float,
): Boolean {
    if (pathPoints.size == 1) {
        val (px, py) = pathPoints[0]
        val dx = pointX - px
        val dy = pointY - py
        return kotlin.math.sqrt(dx * dx + dy * dy) <= radius
    }
    for (i in 0 until pathPoints.size - 1) {
        val (p1x, p1y) = pathPoints[i]
        val (p2x, p2y) = pathPoints[i + 1]
        val dist = pointToSegmentDistance(pointX, pointY, p1x, p1y, p2x, p2y)
        if (dist <= radius) {
            return true
        }
    }
    return false
}

fun pointToSegmentDistance(
    px: Float,
    py: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq == 0f) {
        val dxPoint = px - x1
        val dyPoint = py - y1
        return kotlin.math.sqrt(dxPoint * dxPoint + dyPoint * dyPoint)
    }
    val t = ((px - x1) * dx + (py - y1) * dy) / lenSq
    val clampedT = t.coerceIn(0f, 1f)
    val nearestX = x1 + clampedT * dx
    val nearestY = y1 + clampedT * dy
    val dxPoint = px - nearestX
    val dyPoint = py - nearestY
    return kotlin.math.sqrt(dxPoint * dxPoint + dyPoint * dyPoint)
}
