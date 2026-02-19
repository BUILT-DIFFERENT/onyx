package com.onyx.android.pdf

import android.graphics.Bitmap
import android.graphics.RectF
import kotlin.math.roundToInt

private const val DEFAULT_THUMBNAIL_SCALE = 0.1f

typealias PdfTocItem = OutlineItem

interface PdfRenderEngine {
    fun getPageCount(): Int

    fun getPageBounds(pageIndex: Int): RectF

    fun renderPage(
        pageIndex: Int,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
    )

    fun renderThumbnail(
        pageIndex: Int,
        bitmap: Bitmap,
        thumbWidth: Int,
        thumbHeight: Int,
    )

    fun getTableOfContents(): List<PdfTocItem>

    fun close()
}

fun PdfRenderEngine.renderPage(
    pageIndex: Int,
    zoom: Float,
): Bitmap {
    val boundedZoom = zoom.coerceAtLeast(0f)
    val bounds = getPageBounds(pageIndex)
    val pageWidth = (bounds.width() * boundedZoom).roundToInt().coerceAtLeast(1)
    val pageHeight = (bounds.height() * boundedZoom).roundToInt().coerceAtLeast(1)
    return Bitmap.createBitmap(pageWidth, pageHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
        renderPage(
            pageIndex = pageIndex,
            bitmap = bitmap,
            pageWidth = pageWidth,
            pageHeight = pageHeight,
        )
    }
}

fun PdfRenderEngine.renderThumbnail(pageIndex: Int): Bitmap? {
    val bounds = getPageBounds(pageIndex)
    val thumbWidth = (bounds.width() * DEFAULT_THUMBNAIL_SCALE).roundToInt().coerceAtLeast(1)
    val thumbHeight = (bounds.height() * DEFAULT_THUMBNAIL_SCALE).roundToInt().coerceAtLeast(1)
    return runCatching {
        Bitmap.createBitmap(thumbWidth, thumbHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
            renderThumbnail(
                pageIndex = pageIndex,
                bitmap = bitmap,
                thumbWidth = thumbWidth,
                thumbHeight = thumbHeight,
            )
        }
    }.getOrNull()
}
