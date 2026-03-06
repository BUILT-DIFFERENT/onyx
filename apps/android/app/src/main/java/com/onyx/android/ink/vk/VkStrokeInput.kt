package com.onyx.android.ink.vk

data class VkStrokeInput(
    val x: Float,
    val y: Float,
    val eventTimeMillis: Long,
    val toolType: Int,
    val pressure: Float,
    val tiltRadians: Float,
    val orientationRadians: Float,
)
