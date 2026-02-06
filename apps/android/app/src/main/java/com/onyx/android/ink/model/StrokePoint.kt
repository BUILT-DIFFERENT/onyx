package com.onyx.android.ink.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    // timestamp Unix ms
    val t: Long,
    // pressure 0..1
    val p: Float? = null,
    // tilt x (radians)
    val tx: Float? = null,
    // tilt y (radians)
    val ty: Float? = null,
    // rotation/azimuth (optional)
    val r: Float? = null,
)
