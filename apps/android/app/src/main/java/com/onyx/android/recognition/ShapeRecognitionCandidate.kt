@file:Suppress("ReturnCount", "MagicNumber")

package com.onyx.android.recognition

import com.onyx.android.ink.model.Stroke
import com.onyx.android.objects.model.ShapeType
import kotlin.math.abs
import kotlin.math.hypot

data class ShapeRecognitionCandidate(
    val shapeType: ShapeType,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

/**
 * Heuristic-only scaffold for REC-07. This is intentionally lightweight and
 * meant to be upgraded with model-backed recognition in a later wave.
 */
fun detectShapeCandidate(stroke: Stroke): ShapeRecognitionCandidate? {
    if (stroke.points.size < 6) {
        return null
    }
    val width = stroke.bounds.w
    val height = stroke.bounds.h
    if (width <= 4f && height <= 4f) {
        return null
    }

    val first = stroke.points.first()
    val last = stroke.points.last()
    val endpointDistance = hypot(last.x - first.x, last.y - first.y)
    val diagonal = hypot(width.toDouble(), height.toDouble()).toFloat().coerceAtLeast(1f)
    val isClosed = endpointDistance <= diagonal * 0.25f

    val lineCandidate =
        detectLineCandidate(stroke, endpointDistance = endpointDistance, isClosed = isClosed)
    if (lineCandidate != null) {
        return lineCandidate
    }

    if (!isClosed) {
        return null
    }

    val aspectRatio = (maxOf(width, height) / maxOf(minOf(width, height), 1f))
    val rectangleScore = estimateRectangleScore(stroke)
    val type =
        if (rectangleScore >= 0.6f || aspectRatio >= 1.4f) {
            ShapeType.RECTANGLE
        } else {
            ShapeType.ELLIPSE
        }
    return ShapeRecognitionCandidate(
        shapeType = type,
        x = stroke.bounds.x,
        y = stroke.bounds.y,
        width = width.coerceAtLeast(1f),
        height = height.coerceAtLeast(1f),
    )
}

private fun detectLineCandidate(
    stroke: Stroke,
    endpointDistance: Float,
    isClosed: Boolean,
): ShapeRecognitionCandidate? {
    if (isClosed) {
        return null
    }
    val pathLength = strokePathLength(stroke)
    if (endpointDistance < 24f || pathLength <= 0f) {
        return null
    }
    val straightness = endpointDistance / pathLength
    if (straightness < 0.92f) {
        return null
    }
    val first = stroke.points.first()
    val last = stroke.points.last()
    return ShapeRecognitionCandidate(
        shapeType = ShapeType.LINE,
        x = minOf(first.x, last.x),
        y = minOf(first.y, last.y),
        width = abs(last.x - first.x).coerceAtLeast(1f),
        height = abs(last.y - first.y).coerceAtLeast(1f),
    )
}

private fun strokePathLength(stroke: Stroke): Float {
    if (stroke.points.size < 2) {
        return 0f
    }
    var total = 0f
    for (i in 1 until stroke.points.size) {
        val prev = stroke.points[i - 1]
        val current = stroke.points[i]
        total += hypot(current.x - prev.x, current.y - prev.y)
    }
    return total
}

private fun estimateRectangleScore(stroke: Stroke): Float {
    if (stroke.points.size < 5) {
        return 0f
    }
    var cornerCount = 0
    for (i in 1 until stroke.points.lastIndex) {
        val previous = stroke.points[i - 1]
        val current = stroke.points[i]
        val next = stroke.points[i + 1]
        val v1x = current.x - previous.x
        val v1y = current.y - previous.y
        val v2x = next.x - current.x
        val v2y = next.y - current.y
        val dot = (v1x * v2x) + (v1y * v2y)
        val mag1 = hypot(v1x, v1y)
        val mag2 = hypot(v2x, v2y)
        if (mag1 <= 0.001f || mag2 <= 0.001f) {
            continue
        }
        val cosine = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        if (cosine in -0.5f..0.45f) {
            cornerCount += 1
        }
    }
    return (cornerCount / 4f).coerceIn(0f, 1f)
}
