package com.onyx.android.ink.gl

import com.onyx.android.ink.model.StrokeStyle

data class GlBrush(
    val strokeStyle: StrokeStyle,
    val argbColor: Int,
    val alphaMultiplier: Float,
)
