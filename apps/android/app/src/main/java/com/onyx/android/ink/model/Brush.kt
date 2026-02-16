package com.onyx.android.ink.model

/**
 * Brush represents the current UI brush state.
 * When creating a Stroke, convert Brush -> StrokeStyle for persistence.
 */
data class Brush(
    val tool: Tool = Tool.PEN,
    // hex color
    val color: String = "#000000",
    // in page units (pt)
    val baseWidth: Float = 2.0f,
    val minWidthFactor: Float = 0.85f,
    val maxWidthFactor: Float = 1.15f,
    // 0..1 controls geometric stroke smoothing.
    val smoothingLevel: Float = 0.35f,
    // 0..1 controls start/end taper intensity.
    val endTaperStrength: Float = 0.35f,
) {
    fun toStrokeStyle(): StrokeStyle =
        StrokeStyle(
            tool = tool,
            color = color,
            baseWidth = baseWidth,
            minWidthFactor = minWidthFactor,
            maxWidthFactor = maxWidthFactor,
            smoothingLevel = smoothingLevel,
            endTaperStrength = endTaperStrength,
            nibRotation = false,
        )
}
