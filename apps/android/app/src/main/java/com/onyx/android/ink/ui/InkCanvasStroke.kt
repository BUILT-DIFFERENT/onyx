package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint

internal fun buildStroke(
    rawPoints: List<StrokePoint>,
    brush: Brush,
    displayPoints: List<StrokePoint> = rawPoints,
): Stroke {
    val style = brush.toStrokeStyle()
    val bounds = calculateBounds(displayPoints, style.baseWidth * style.maxWidthFactor)
    return Stroke(
        id = java.util.UUID.randomUUID().toString(),
        points = rawPoints,
        style = style,
        bounds = bounds,
        createdAt = rawPoints.minOfOrNull { it.t } ?: System.currentTimeMillis(),
        displayPoints = displayPoints,
    )
}
