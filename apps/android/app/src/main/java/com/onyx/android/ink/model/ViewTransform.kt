package com.onyx.android.ink.model

import android.graphics.PointF

/**
 * ViewTransform holds the current zoom/pan state for a page.
 * This is the SINGLE SOURCE OF TRUTH for all coordinate conversions.
 *
 * For multi-page documents, use the document-space coordinate system:
 * - documentY = sum(pageHeights[0..pageIndex-1]) + (pageIndex * gapHeight) + pageLocalY
 * - All touch-to-page-local conversion happens via documentToPage()
 * - Strokes are stored in page-local coordinates; conversion happens at input/render time
 */
@Suppress("TooManyFunctions")
data class ViewTransform(
    // 1.0 = 72 DPI (1pt = 1px), 2.0 = 144 DPI
    val zoom: Float = 1f,
    // Pan offset in screen pixels
    val panX: Float = 0f,
    // Pan offset in screen pixels
    val panY: Float = 0f,
    // Page heights in points (pt) for multi-page documents
    val pageHeights: List<Float> = emptyList(),
    // Gap between pages in points (pt)
    val gapHeight: Float = 0f,
    // Page rotations (0, 90, 180, 270 degrees) for PDF pages
    val pageRotations: List<Int> = emptyList(),
) {
    companion object {
        val DEFAULT = ViewTransform()
        const val MIN_ZOOM = 0.5f
        const val MAX_ZOOM = 4.0f

        // PDF rotation constants
        const val ROTATION_0 = 0
        const val ROTATION_90 = 90
        const val ROTATION_180 = 180
        const val ROTATION_270 = 270
    }

    /**
     * Convert page coordinates (pt) to screen pixels.
     */
    fun pageToScreen(
        pageX: Float,
        pageY: Float,
    ): Pair<Float, Float> =
        Pair(
            pageToScreenX(pageX),
            pageToScreenY(pageY),
        )

    fun pageToScreenX(pageX: Float): Float = pageX * zoom + panX

    fun pageToScreenY(pageY: Float): Float = pageY * zoom + panY

    /**
     * Convert screen pixels to page coordinates (pt).
     */
    fun screenToPage(
        screenX: Float,
        screenY: Float,
    ): Pair<Float, Float> =
        Pair(
            screenToPageX(screenX),
            screenToPageY(screenY),
        )

    fun screenToPageX(screenX: Float): Float = (screenX - panX) / zoom

    fun screenToPageY(screenY: Float): Float = (screenY - panY) / zoom

    /**
     * Convert stroke width from page units to screen pixels.
     */
    fun pageWidthToScreen(pageWidth: Float): Float = pageWidth * zoom

    /**
     * Convert document-space coordinates to page index and page-local coordinates.
     *
     * Document-space Y is continuous across all pages:
     * documentY = sum(pageHeights[0..pageIndex-1]) + (pageIndex * gapHeight) + pageLocalY
     *
     * @param docX Document X coordinate (same as page X for vertical layout)
     * @param docY Document Y coordinate in points
     * @return Pair of (pageIndex, PointF with page-local x,y) or null if out of bounds
     */
    fun documentToPage(
        docX: Float,
        docY: Float,
    ): Pair<Int, PointF>? {
        if (pageHeights.isEmpty()) {
            // Single-page mode: treat as page 0
            return Pair(0, PointF(docX, docY))
        }

        var remainingY = docY
        var pageIndex = 0

        // Find which page contains this Y coordinate
        for ((index, pageHeight) in pageHeights.withIndex()) {
            val pageStartY =
                if (index == 0) {
                    0f
                } else {
                    (0 until index).sumOf { pageHeights[it].toDouble() }.toFloat() +
                        index * gapHeight
                }
            val pageEndY = pageStartY + pageHeight

            if (docY < pageEndY || index == pageHeights.lastIndex) {
                pageIndex = index
                remainingY = docY - pageStartY
                break
            }
        }

        // Clamp page-local Y to valid range
        val pageHeight = pageHeights.getOrElse(pageIndex) { 0f }
        val localY = remainingY.coerceIn(0f, pageHeight)

        // Apply rotation normalization if needed
        val rotation = pageRotations.getOrElse(pageIndex) { ROTATION_0 }
        val normalizedPoint = normalizeForRotation(docX, localY, pageHeight, rotation)

        return Pair(pageIndex, normalizedPoint)
    }

    /**
     * Convert page-local coordinates to document-space coordinates.
     *
     * @param pageIndex Index of the page (0-based)
     * @param localPoint Page-local coordinates in points
     * @return PointF with document-space coordinates
     */
    fun pageToDocument(
        pageIndex: Int,
        localPoint: PointF,
    ): PointF {
        if (pageHeights.isEmpty() || pageIndex < 0 || pageIndex >= pageHeights.size) {
            // Single-page mode or invalid index
            return PointF(localPoint.x, localPoint.y)
        }

        // Calculate document Y offset for this page
        val pageStartY = getDocumentY(pageIndex, 0f)

        // Reverse rotation normalization if needed
        val pageHeight = pageHeights[pageIndex]
        val rotation = pageRotations.getOrElse(pageIndex) { ROTATION_0 }
        val denormalizedPoint = denormalizeFromRotation(localPoint, pageHeight, rotation)

        return PointF(denormalizedPoint.x, pageStartY + denormalizedPoint.y)
    }

    /**
     * Get the document-space Y coordinate for a point within a page.
     *
     * @param pageIndex Index of the page (0-based)
     * @param pageLocalY Y coordinate within the page in points
     * @return Document-space Y coordinate
     */
    fun getDocumentY(
        pageIndex: Int,
        pageLocalY: Float,
    ): Float {
        if (pageHeights.isEmpty() || pageIndex < 0) {
            return pageLocalY
        }

        // Sum heights of all preceding pages
        val precedingHeight =
            if (pageIndex == 0) {
                0f
            } else {
                (0 until pageIndex).sumOf { pageHeights[it].toDouble() }.toFloat()
            }

        // Add gaps between pages
        val gapOffset = pageIndex * gapHeight

        return precedingHeight + gapOffset + pageLocalY
    }

    /**
     * Get the total document height in points.
     */
    fun getDocumentHeight(): Float {
        if (pageHeights.isEmpty()) return 0f

        val totalPageHeight = pageHeights.sum()
        val totalGapHeight = (pageHeights.size - 1).coerceAtLeast(0) * gapHeight

        return totalPageHeight + totalGapHeight
    }

    /**
     * Get the page index for a given document Y coordinate.
     *
     * @param docY Document Y coordinate in points
     * @return Page index, or -1 if out of bounds
     */
    @Suppress("ReturnCount")
    fun getPageIndexForDocumentY(docY: Float): Int {
        if (pageHeights.isEmpty()) return 0

        var currentY = 0f
        for ((index, pageHeight) in pageHeights.withIndex()) {
            val pageEndY = currentY + pageHeight
            if (docY < pageEndY) {
                return index
            }
            currentY = pageEndY + gapHeight
        }

        // Return last page if beyond document bounds
        return pageHeights.lastIndex
    }

    /**
     * Normalize coordinates for PDF page rotation.
     * PDF pages may have rotation metadata (0°, 90°, 180°, 270°).
     * This converts from upright view coordinates to internal page coordinates.
     *
     * @param x X coordinate in upright view
     * @param y Y coordinate in upright view
     * @param pageHeight Page height (used for rotation transform)
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @return PointF with normalized coordinates
     */
    private fun normalizeForRotation(
        x: Float,
        y: Float,
        pageHeight: Float,
        rotation: Int,
    ): PointF {
        return when (rotation) {
            ROTATION_90 -> PointF(pageHeight - y, x)
            ROTATION_180 -> PointF(-x, pageHeight - y)
            ROTATION_270 -> PointF(y, -x)
            else -> PointF(x, y) // ROTATION_0 or unknown
        }
    }

    /**
     * Reverse the rotation normalization.
     * Converts from internal page coordinates to upright view coordinates.
     *
     * @param point Point in internal page coordinates
     * @param pageHeight Page height (used for rotation transform)
     * @param rotation Rotation in degrees (0, 90, 180, 270)
     * @return PointF with upright view coordinates
     */
    private fun denormalizeFromRotation(
        point: PointF,
        pageHeight: Float,
        rotation: Int,
    ): PointF {
        return when (rotation) {
            ROTATION_90 -> PointF(point.y, pageHeight - point.x)
            ROTATION_180 -> PointF(-point.x, pageHeight - point.y)
            ROTATION_270 -> PointF(-point.y, point.x)
            else -> point // ROTATION_0 or unknown
        }
    }

    /**
     * Check if a document Y coordinate is within valid bounds.
     */
    fun isDocumentYValid(docY: Float): Boolean {
        if (pageHeights.isEmpty()) return true
        return docY >= 0f && docY <= getDocumentHeight()
    }

    /**
     * Convert screen coordinates directly to document-space coordinates.
     * Convenience method combining screenToPage with page index lookup.
     *
     * @param screenX Screen X in pixels
     * @param screenY Screen Y in pixels
     * @return Pair of (pageIndex, PointF with page-local coordinates) or null
     */
    fun screenToDocumentPage(
        screenX: Float,
        screenY: Float,
    ): Pair<Int, PointF>? {
        // First convert screen to page coordinates (assumes current pan/zoom)
        val pageX = screenToPageX(screenX)
        val pageY = screenToPageY(screenY)

        // For multi-page, the pageY represents document Y
        return documentToPage(pageX, pageY)
    }
}
