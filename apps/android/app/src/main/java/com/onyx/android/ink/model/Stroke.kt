package com.onyx.android.ink.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class Stroke(
    val id: String,
    val points: List<StrokePoint>,
    val style: StrokeStyle,
    val bounds: StrokeBounds,
    val createdAt: Long,
    val createdLamport: Long = 0,
    val displayPoints: List<StrokePoint> = points,
)

val Stroke.rawPoints: List<StrokePoint>
    get() = points

val Stroke.renderPoints: List<StrokePoint>
    get() = displayPoints

@Serializable
data class StrokeStyle(
    val tool: Tool,
    val color: String = "#000000",
    val baseWidth: Float,
    val minWidthFactor: Float = 0.15f,
    val maxWidthFactor: Float = 2.5f,
    val smoothingLevel: Float = 0.5f,
    val endTaperStrength: Float = 0.6f,
    val lineStyle: StrokeLineStyle = StrokeLineStyle.SOLID,
    val nibRotation: Boolean = false,
)

@Serializable
enum class StrokeLineStyle(
    val apiValue: String,
) {
    @SerialName("solid")
    SOLID("solid"),

    @SerialName("dashed")
    DASHED("dashed"),

    @SerialName("dotted")
    DOTTED("dotted"),
}

@Serializable
data class StrokeBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

@Serializable
enum class Tool(
    val apiValue: String,
) {
    @SerialName("pen")
    PEN("pen"),

    @SerialName("highlighter")
    HIGHLIGHTER("highlighter"),

    @SerialName("eraser")
    ERASER("eraser"),

    @SerialName("lasso")
    LASSO("lasso"),
}
