package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint

internal fun buildStroke(
    points: List<StrokePoint>,
    brush: Brush,
): Stroke {
    val bounds = calculateBounds(points)
    return Stroke(
        id = java.util.UUID.randomUUID().toString(),
        points = points,
        style = brush.toStrokeStyle(),
        bounds = bounds,
        createdAt = points.minOfOrNull { it.t } ?: System.currentTimeMillis(),
    )
}
