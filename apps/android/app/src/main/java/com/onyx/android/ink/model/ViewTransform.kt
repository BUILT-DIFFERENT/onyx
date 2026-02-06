package com.onyx.android.ink.model

/**
 * ViewTransform holds the current zoom/pan state for a page.
 * This is the SINGLE SOURCE OF TRUTH for all coordinate conversions.
 */
data class ViewTransform(
    // 1.0 = 72 DPI (1pt = 1px), 2.0 = 144 DPI
    val zoom: Float = 1f,
    // Pan offset in screen pixels
    val panX: Float = 0f,
    // Pan offset in screen pixels
    val panY: Float = 0f,
) {
    companion object {
        val DEFAULT = ViewTransform()
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4.0f
    }

    /**
     * Convert page coordinates (pt) to screen pixels.
     */
    fun pageToScreen(
        pageX: Float,
        pageY: Float,
    ): Pair<Float, Float> =
        Pair(
            pageX * zoom + panX,
            pageY * zoom + panY,
        )

    /**
     * Convert screen pixels to page coordinates (pt).
     */
    fun screenToPage(
        screenX: Float,
        screenY: Float,
    ): Pair<Float, Float> =
        Pair(
            (screenX - panX) / zoom,
            (screenY - panY) / zoom,
        )

    /**
     * Convert stroke width from page units to screen pixels.
     */
    fun pageWidthToScreen(pageWidth: Float): Float = pageWidth * zoom
}
