@file:Suppress("ReturnCount", "LoopWithTooManyJumpStatements", "MagicNumber", "LongParameterList")

package com.onyx.android.ink.ui

import com.onyx.android.ink.model.SpatialIndex
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import kotlin.math.max
import kotlin.math.min

internal fun findStrokesInLasso(
    polygon: List<Pair<Float, Float>>,
    strokes: List<Stroke>,
    spatialIndex: SpatialIndex,
): Set<String> {
    if (polygon.size < 3) return emptySet()
    val candidateIds = spatialIndex.queryPolygon(polygon)
    val result = mutableSetOf<String>()
    for (stroke in strokes) {
        if (stroke.id !in candidateIds) continue
        if (isStrokeInsidePolygon(stroke, polygon)) {
            result.add(stroke.id)
        }
    }
    return result
}

internal fun isStrokeInsidePolygon(
    stroke: Stroke,
    polygon: List<Pair<Float, Float>>,
): Boolean {
    if (polygon.size < 3) return false
    if (!boundsIntersectPolygon(stroke.bounds, polygon)) return false
    val points = stroke.points
    if (points.isEmpty()) return false
    for (point in points) {
        if (pointInPolygon(point.x, point.y, polygon)) {
            return true
        }
    }
    return boundsCenterInPolygon(stroke.bounds, polygon)
}

internal fun boundsIntersectPolygon(
    bounds: StrokeBounds,
    polygon: List<Pair<Float, Float>>,
): Boolean {
    if (polygon.size < 3) return false
    val polygonBounds = calculatePolygonBounds(polygon)
    val overlapsX =
        bounds.x < polygonBounds.x + polygonBounds.w &&
            bounds.x + bounds.w > polygonBounds.x
    val overlapsY =
        bounds.y < polygonBounds.y + polygonBounds.h &&
            bounds.y + bounds.h > polygonBounds.y
    return overlapsX && overlapsY
}

internal fun pointInPolygon(
    x: Float,
    y: Float,
    polygon: List<Pair<Float, Float>>,
): Boolean {
    if (polygon.size < 3) return false
    var inside = false
    var j = polygon.size - 1
    for (i in polygon.indices) {
        val xi = polygon[i].first
        val yi = polygon[i].second
        val xj = polygon[j].first
        val yj = polygon[j].second
        if (((yi > y) != (yj > y)) &&
            (x < (xj - xi) * (y - yi) / (yj - yi) + xi)
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}

private fun boundsCenterInPolygon(
    bounds: StrokeBounds,
    polygon: List<Pair<Float, Float>>,
): Boolean {
    val centerX = bounds.x + bounds.w / 2
    val centerY = bounds.y + bounds.h / 2
    return pointInPolygon(centerX, centerY, polygon)
}

private fun calculatePolygonBounds(polygon: List<Pair<Float, Float>>): StrokeBounds {
    if (polygon.isEmpty()) return StrokeBounds(0f, 0f, 0f, 0f)
    var minX = polygon[0].first
    var minY = polygon[0].second
    var maxX = polygon[0].first
    var maxY = polygon[0].second
    for (point in polygon) {
        minX = min(minX, point.first)
        minY = min(minY, point.second)
        maxX = max(maxX, point.first)
        maxY = max(maxY, point.second)
    }
    return StrokeBounds(x = minX, y = minY, w = maxX - minX, h = maxY - minY)
}

internal fun calculateSelectionBounds(strokes: List<Stroke>): StrokeBounds? {
    if (strokes.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    for (stroke in strokes) {
        minX = min(minX, stroke.bounds.x)
        minY = min(minY, stroke.bounds.y)
        maxX = max(maxX, stroke.bounds.x + stroke.bounds.w)
        maxY = max(maxY, stroke.bounds.y + stroke.bounds.h)
    }
    return StrokeBounds(
        x = minX,
        y = minY,
        w = maxX - minX,
        h = maxY - minY,
    )
}

internal fun transformStroke(
    stroke: Stroke,
    translateX: Float,
    translateY: Float,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    pivotX: Float = 0f,
    pivotY: Float = 0f,
): Stroke {
    val transformedPoints =
        stroke.points.map { point ->
            val relX = point.x - pivotX
            val relY = point.y - pivotY
            val scaledX = relX * scaleX
            val scaledY = relY * scaleY
            StrokePoint(
                x = scaledX + pivotX + translateX,
                y = scaledY + pivotY + translateY,
                t = point.t,
                p = point.p,
                tx = point.tx,
                ty = point.ty,
                r = point.r,
            )
        }
    val style =
        stroke.style.copy(
            baseWidth = stroke.style.baseWidth * ((scaleX + scaleY) / 2f),
        )
    return stroke.copy(
        points = transformedPoints,
        style = style,
        bounds = calculateBounds(transformedPoints, style.baseWidth * style.maxWidthFactor),
    )
}

internal fun moveStrokes(
    strokes: List<Stroke>,
    deltaX: Float,
    deltaY: Float,
): List<Stroke> =
    strokes.map { stroke ->
        transformStroke(stroke, translateX = deltaX, translateY = deltaY)
    }

internal fun resizeStrokes(
    strokes: List<Stroke>,
    scale: Float,
    pivotX: Float,
    pivotY: Float,
): List<Stroke> =
    strokes.map { stroke ->
        transformStroke(
            stroke = stroke,
            translateX = 0f,
            translateY = 0f,
            scaleX = scale,
            scaleY = scale,
            pivotX = pivotX,
            pivotY = pivotY,
        )
    }
