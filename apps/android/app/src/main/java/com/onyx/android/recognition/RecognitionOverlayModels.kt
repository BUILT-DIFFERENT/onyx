package com.onyx.android.recognition

import kotlinx.serialization.Serializable

@Serializable
data class OverlayBounds(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
)

@Serializable
data class ConvertedTextBlock(
    val id: String,
    val text: String,
    val bounds: OverlayBounds,
    val updatedAt: Long,
)
