package com.onyx.android.pdf

import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Quad
import com.artifex.mupdf.fitz.StructuredText
import com.artifex.mupdf.fitz.StructuredText.TextChar
import com.artifex.mupdf.fitz.android.AndroidDrawDevice
import java.io.File
import kotlin.math.roundToInt

private const val PDF_BITMAP_CACHE_MAX_SIZE_KIB = 64 * 1024
private const val PDF_TEXT_CACHE_MAX_ENTRIES = 12
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

class PdfRenderer(
    pdfFile: File,
) {
    private val document: Document = Document.openDocument(pdfFile.absolutePath)
    private val lock = Any()
    private val bitmapCache =
        object : LruCache<PdfBitmapCacheKey, Bitmap>(PDF_BITMAP_CACHE_MAX_SIZE_KIB) {
            override fun sizeOf(
                key: PdfBitmapCacheKey,
                value: Bitmap,
            ): Int = value.byteCount / BYTES_PER_KIB

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
    private val textCache = LruCache<Int, StructuredText>(PDF_TEXT_CACHE_MAX_ENTRIES)

    fun getPageCount(): Int =
        synchronized(lock) {
            document.countPages()
        }

    fun getPageBounds(pageIndex: Int): Pair<Float, Float> {
        return synchronized(lock) {
            val page = document.loadPage(pageIndex)
            try {
                val bounds = page.bounds
                val width = bounds.x1 - bounds.x0
                val height = bounds.y1 - bounds.y0
                Log.d("PdfRenderer", "Page $pageIndex bounds: ${width}pt x ${height}pt")
                Pair(width, height)
            } finally {
                page.destroy()
            }
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

    fun extractTextStructure(pageIndex: Int): StructuredText {
        return synchronized(lock) {
            val cachedText = textCache.get(pageIndex)
            if (cachedText != null) {
                return@synchronized cachedText
            }
            val page = document.loadPage(pageIndex)
            try {
                page.toStructuredText().also { text ->
                    textCache.put(pageIndex, text)
                }
            } finally {
                page.destroy()
            }
        }
    }

    fun findCharAtPagePoint(
        text: StructuredText,
        pageX: Float,
        pageY: Float,
    ): TextChar? {
        return text.charSequence().firstOrNull { char ->
            char.quad.contains(pageX, pageY)
        }
    }

    fun getSelectionQuads(
        text: StructuredText,
        startChar: TextChar,
        endChar: TextChar,
    ): List<Quad> {
        val (orderedStart, orderedEnd) = orderSelection(text, startChar, endChar)
        val quads = mutableListOf<Quad>()
        var collecting = false

        for (char in text.charSequence()) {
            if (char == orderedStart) {
                collecting = true
            }
            if (collecting) {
                quads.add(char.quad)
            }
            if (char == orderedEnd) {
                break
            }
        }
        return quads
    }

    fun extractSelectedText(
        text: StructuredText,
        startChar: TextChar,
        endChar: TextChar,
    ): String {
        val (orderedStart, orderedEnd) = orderSelection(text, startChar, endChar)
        val builder = StringBuilder()
        var collecting = false

        for (char in text.charSequence()) {
            if (char == orderedStart) {
                collecting = true
            }
            if (collecting) {
                builder.append(char.c.toChar())
            }
            if (char == orderedEnd) {
                break
            }
        }
        return builder.toString()
    }

    private fun orderSelection(
        text: StructuredText,
        startChar: TextChar,
        endChar: TextChar,
    ): Pair<TextChar, TextChar> {
        val startIndex = text.indexOfChar(startChar)
        val endIndex = text.indexOfChar(endChar)
        val shouldSwap = startIndex >= 0 && endIndex >= 0 && endIndex < startIndex
        return if (shouldSwap) Pair(endChar, startChar) else Pair(startChar, endChar)
    }

    private fun renderPageUncached(
        pageIndex: Int,
        zoom: Float,
    ): Bitmap {
        val page = document.loadPage(pageIndex)
        try {
            val bounds = page.bounds
            val pageWidth = bounds.x1 - bounds.x0
            val pageHeight = bounds.y1 - bounds.y0
            Log.d("PdfRenderer", "Page $pageIndex bounds: ${pageWidth}pt x ${pageHeight}pt")

            val matrix = Matrix(zoom)
            val pixelWidth = (pageWidth * zoom).toInt().coerceAtLeast(1)
            val pixelHeight = (pageHeight * zoom).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
            val device = AndroidDrawDevice(bitmap)
            try {
                page.run(device, matrix, null)
            } finally {
                device.close()
            }
            return bitmap
        } finally {
            page.destroy()
        }
    }

    fun close() {
        synchronized(lock) {
            bitmapCache.evictAll()
            textCache.evictAll()
            document.destroy()
        }
    }
}

private fun StructuredText.indexOfChar(target: TextChar): Int {
    return charSequence().indexOfFirst { char -> char == target }
}

private fun StructuredText.charSequence(): Sequence<TextChar> =
    blocks
        .asSequence()
        .flatMap { block -> block.lines.asSequence() }
        .flatMap { line -> line.chars.asSequence() }
