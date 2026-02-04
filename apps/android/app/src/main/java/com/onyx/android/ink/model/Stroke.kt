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
)

@Serializable
data class StrokeStyle(
    val tool: Tool,
    val color: String = "#000000",
    val baseWidth: Float,
    val minWidthFactor: Float = 0.85f,
    val maxWidthFactor: Float = 1.15f,
    val nibRotation: Boolean = false,
)

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
}
