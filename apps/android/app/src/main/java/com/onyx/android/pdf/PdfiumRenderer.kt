package com.onyx.android.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.util.LruCache
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val PDF_BITMAP_CACHE_MAX_SIZE_KIB = 64 * 1024
private const val BYTES_PER_KIB = 1024
private const val RENDER_SCALE_CACHE_KEY_MULTIPLIER = 1000f
private const val MIN_RENDER_SCALE_CACHE_KEY = 1
private const val THUMBNAIL_CACHE_MAX_SIZE_KIB = 8 * 1024
private const val THUMBNAIL_SCALE_DEFAULT = 0.1f

internal data class PdfBitmapCacheKey(
    val pageIndex: Int,
    val renderScaleKey: Int,
)

internal fun renderScaleCacheKey(renderScale: Float): Int =
    (renderScale.coerceAtLeast(0f) * RENDER_SCALE_CACHE_KEY_MULTIPLIER)
        .roundToInt()
        .coerceAtLeast(MIN_RENDER_SCALE_CACHE_KEY)

@Suppress("TooManyFunctions")
class PdfiumRenderer(
    context: Context,
    private val pdfFile: File,
    password: String? = null,
) : PdfDocumentRenderer {
    private val lock = Any()
    private val documentSession = PdfiumDocumentSession.open(context, pdfFile, password = password)
    private val tileRenderer = PdfTileRenderer(documentSession = documentSession)
    private val bitmapCache =
        object : LruCache<PdfBitmapCacheKey, Bitmap>(PDF_BITMAP_CACHE_MAX_SIZE_KIB) {
            override fun sizeOf(
                key: PdfBitmapCacheKey,
                value: Bitmap,
            ): Int = max(1, value.byteCount / BYTES_PER_KIB)

            override fun entryRemoved(
                evicted: Boolean,
                key: PdfBitmapCacheKey,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (oldValue !== newValue && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }

    private val thumbnailCache =
        object : LruCache<Int, Bitmap>(THUMBNAIL_CACHE_MAX_SIZE_KIB) {
            override fun sizeOf(
                key: Int,
                value: Bitmap,
            ): Int = max(1, value.byteCount / BYTES_PER_KIB)

            override fun entryRemoved(
                evicted: Boolean,
                key: Int,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (oldValue !== newValue && !oldValue.isRecycled) {
                    oldValue.recycle()
                }
            }
        }

    override fun getPageCount(): Int =
        synchronized(lock) {
            documentSession.pageCount
        }

    override fun getPageBounds(pageIndex: Int): RectF =
        synchronized(lock) {
            val (pageWidth, pageHeight) = documentSession.getPageBounds(pageIndex)
            RectF(0f, 0f, pageWidth, pageHeight)
        }

    override fun renderPage(
        pageIndex: Int,
        bitmap: Bitmap,
        pageWidth: Int,
        pageHeight: Int,
    ) {
        synchronized(lock) {
            documentSession.renderPageBitmap(
                pageIndex = pageIndex,
                bitmap = bitmap,
                region = PdfRenderRegion(startX = 0, startY = 0, width = pageWidth, height = pageHeight),
                renderAnnotations = true,
            )
        }
    }

    fun renderPage(
        pageIndex: Int,
        zoom: Float,
    ): Bitmap {
        val cacheKey =
            PdfBitmapCacheKey(
                pageIndex = pageIndex,
                renderScaleKey = renderScaleCacheKey(zoom),
            )
        return synchronized(lock) {
            val cachedBitmap = bitmapCache.get(cacheKey)
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                return@synchronized cachedBitmap
            }
            if (cachedBitmap?.isRecycled == true) {
                bitmapCache.remove(cacheKey)
            }
            val renderedBitmap = renderPageUncached(pageIndex, zoom)
            bitmapCache.put(cacheKey, renderedBitmap)
            renderedBitmap
        }
    }

    @Suppress("ComplexCondition")
    override fun renderThumbnail(
        pageIndex: Int,
        bitmap: Bitmap,
        thumbWidth: Int,
        thumbHeight: Int,
    ) {
        val renderedThumbnail =
            synchronized(lock) {
                val cachedThumbnail = thumbnailCache.get(pageIndex)
                if (
                    cachedThumbnail != null &&
                    !cachedThumbnail.isRecycled &&
                    cachedThumbnail.width == thumbWidth &&
                    cachedThumbnail.height == thumbHeight
                ) {
                    return@synchronized cachedThumbnail
                }
                if (cachedThumbnail?.isRecycled == true) {
                    thumbnailCache.remove(pageIndex)
                }

                val thumbnail = renderThumbnailUncached(pageIndex, thumbWidth, thumbHeight)
                if (thumbnail != null) {
                    thumbnailCache.put(pageIndex, thumbnail)
                }
                thumbnail
            }

        if (renderedThumbnail != null && !renderedThumbnail.isRecycled) {
            Canvas(bitmap).drawBitmap(renderedThumbnail, 0f, 0f, null)
        }
    }

    fun renderThumbnail(pageIndex: Int): Bitmap? {
        return synchronized(lock) {
            val cachedThumbnail = thumbnailCache.get(pageIndex)
            if (cachedThumbnail != null && !cachedThumbnail.isRecycled) {
                return@synchronized cachedThumbnail
            }
            if (cachedThumbnail?.isRecycled == true) {
                thumbnailCache.remove(pageIndex)
            }

            val (pageWidth, pageHeight) = documentSession.getPageBounds(pageIndex)
            val thumbnailWidth = (pageWidth * THUMBNAIL_SCALE_DEFAULT).roundToInt().coerceAtLeast(1)
            val thumbnailHeight = (pageHeight * THUMBNAIL_SCALE_DEFAULT).roundToInt().coerceAtLeast(1)
            val thumbnail = renderThumbnailUncached(pageIndex, thumbnailWidth, thumbnailHeight)
            if (thumbnail != null) {
                thumbnailCache.put(pageIndex, thumbnail)
            }
            thumbnail
        }
    }

    override fun getCharacters(pageIndex: Int): List<PdfTextChar> =
        synchronized(lock) {
            documentSession.getTextCharacters(pageIndex)
        }

    override fun getTableOfContents(): List<PdfTocItem> =
        synchronized(lock) {
            documentSession.getTableOfContents()
        }

    override suspend fun renderTile(key: PdfTileKey): Bitmap = tileRenderer.renderTile(key)

    override fun close() {
        synchronized(lock) {
            bitmapCache.evictAll()
            thumbnailCache.evictAll()
            documentSession.close()
        }
    }

    private fun renderPageUncached(
        pageIndex: Int,
        zoom: Float,
    ): Bitmap {
        val (pageWidth, pageHeight) = documentSession.getPageBounds(pageIndex)
        val pixelWidth = (pageWidth * zoom).roundToInt().coerceAtLeast(1)
        val pixelHeight = (pageHeight * zoom).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
        runCatching {
            documentSession.renderPageBitmap(
                pageIndex = pageIndex,
                bitmap = bitmap,
                region =
                    PdfRenderRegion(
                        startX = 0,
                        startY = 0,
                        width = pixelWidth,
                        height = pixelHeight,
                    ),
                renderAnnotations = true,
            )
        }.onFailure {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }.getOrThrow()
        return bitmap
    }

    private fun renderThumbnailUncached(
        pageIndex: Int,
        thumbnailWidth: Int,
        thumbnailHeight: Int,
    ): Bitmap? =
        runCatching {
            val bitmap = Bitmap.createBitmap(thumbnailWidth, thumbnailHeight, Bitmap.Config.ARGB_8888)
            documentSession.renderPageBitmap(
                pageIndex = pageIndex,
                bitmap = bitmap,
                region =
                    PdfRenderRegion(
                        startX = 0,
                        startY = 0,
                        width = thumbnailWidth,
                        height = thumbnailHeight,
                    ),
                renderAnnotations = true,
            )
            bitmap
        }.getOrNull()
}

fun openPdfDocumentRenderer(
    context: Context,
    pdfFile: File,
    password: String? = null,
): PdfDocumentRenderer = PdfiumRenderer(context = context, pdfFile = pdfFile, password = password)
