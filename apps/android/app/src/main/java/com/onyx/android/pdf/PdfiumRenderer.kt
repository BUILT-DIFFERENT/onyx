package com.onyx.android.pdf

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val PDFIUM_RENDERER_LOG_TAG = "PdfiumRenderer"
private const val PDF_BITMAP_CACHE_MAX_SIZE_KIB = 64 * 1024
private const val BYTES_PER_KIB = 1024
private const val RENDER_SCALE_CACHE_KEY_MULTIPLIER = 1000f
private const val MIN_RENDER_SCALE_CACHE_KEY = 1

internal data class PdfBitmapCacheKey(
    val pageIndex: Int,
    val renderScaleKey: Int,
)

internal fun renderScaleCacheKey(renderScale: Float): Int =
    (renderScale.coerceAtLeast(0f) * RENDER_SCALE_CACHE_KEY_MULTIPLIER)
        .roundToInt()
        .coerceAtLeast(MIN_RENDER_SCALE_CACHE_KEY)

interface PdfDocumentRenderer : PdfTextExtractor {
    fun getPageCount(): Int

    fun getPageBounds(pageIndex: Int): Pair<Float, Float>

    fun renderPage(
        pageIndex: Int,
        zoom: Float,
    ): Bitmap

    fun close()
}

class PdfiumRenderer(
    context: Context,
    private val pdfFile: File,
    password: String? = null,
) : PdfDocumentRenderer {
    private val lock = Any()
    private val documentSession = PdfiumDocumentSession.open(context, pdfFile, password = password)
    private var muPdfTextExtractor: MuPdfTextExtractor? = null
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

    override fun getPageCount(): Int {
        return synchronized(lock) {
            documentSession.pageCount
        }
    }

    override fun getPageBounds(pageIndex: Int): Pair<Float, Float> {
        return synchronized(lock) {
            documentSession.getPageBounds(pageIndex)
        }
    }

    override fun renderPage(
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

    override suspend fun getCharacters(pageIndex: Int): List<PdfTextChar> {
        val textExtractor =
            synchronized(lock) {
                ensureMuPdfTextExtractor()
            } ?: return emptyList()
        return textExtractor.getCharacters(pageIndex)
    }

    override fun close() {
        synchronized(lock) {
            bitmapCache.evictAll()
            muPdfTextExtractor?.close()
            muPdfTextExtractor = null
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

    private fun ensureMuPdfTextExtractor(): MuPdfTextExtractor? {
        val existingExtractor = muPdfTextExtractor
        if (existingExtractor != null) {
            return existingExtractor
        }
        val createdExtractor =
            runCatching { MuPdfTextExtractor(pdfFile) }
                .onFailure { error ->
                    Log.w(PDFIUM_RENDERER_LOG_TAG, "MuPDF text fallback unavailable", error)
                }.getOrNull()
        muPdfTextExtractor = createdExtractor
        return createdExtractor
    }
}
