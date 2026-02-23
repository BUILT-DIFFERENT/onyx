package com.onyx.android.objects.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ShapeType(
    val storageValue: String,
) {
    @SerialName("line")
    LINE("line"),

    @SerialName("rectangle")
    RECTANGLE("rectangle"),

    @SerialName("ellipse")
    ELLIPSE("ellipse"),
}

@Serializable
data class ShapePayload(
    val shapeType: ShapeType,
    val strokeColor: String = "#1E88E5",
    val strokeWidth: Float = 2f,
    val fillColor: String? = null,
)
