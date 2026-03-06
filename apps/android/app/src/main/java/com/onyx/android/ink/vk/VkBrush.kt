package com.onyx.android.ink.vk

import com.onyx.android.ink.model.StrokeStyle

data class VkBrush(
    val strokeStyle: StrokeStyle,
    val argbColor: Int,
    val alphaMultiplier: Float,
)
