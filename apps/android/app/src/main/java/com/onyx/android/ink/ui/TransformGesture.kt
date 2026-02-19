package com.onyx.android.ink.ui

data class TransformGesture(
    val zoomChange: Float,
    val panChangeX: Float,
    val panChangeY: Float,
    val centroidX: Float,
    val centroidY: Float,
)
