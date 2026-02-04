package com.onyx.android.ink.model

data class StrokePoint(
    val x: Float,
    val y: Float,
    val t: Long, // timestamp Unix ms
    val p: Float? = null, // pressure 0..1
    val tx: Float? = null, // tilt x (radians)
    val ty: Float? = null, // tilt y (radians)
    val r: Float? = null, // rotation/azimuth (optional)
)
