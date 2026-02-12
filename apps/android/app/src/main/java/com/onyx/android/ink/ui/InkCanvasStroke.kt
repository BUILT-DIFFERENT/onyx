package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint

internal fun buildStroke(
    points: List<StrokePoint>,
    brush: Brush,
): Stroke {
    val style = brush.toStrokeStyle()
    val bounds = calculateBounds(points, style.baseWidth * style.maxWidthFactor)
    return Stroke(
        id = java.util.UUID.randomUUID().toString(),
        points = points,
        style = style,
        bounds = bounds,
        createdAt = points.minOfOrNull { it.t } ?: System.currentTimeMillis(),
    )
}
