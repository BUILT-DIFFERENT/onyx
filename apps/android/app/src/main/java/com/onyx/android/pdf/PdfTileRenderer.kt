package com.onyx.android.pdf

import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MIN_TILE_COUNT = 1
private const val TILE_MAX_COORD_EPSILON = 0.0001f
private const val MIN_SCALE_BUCKET = 0.0001f

data class PdfVisiblePageRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PdfTileRange(
    val minTileX: Int,
    val maxTileX: Int,
    val minTileY: Int,
    val maxTileY: Int,
) {
    val isValid: Boolean
        get() = minTileX <= maxTileX && minTileY <= maxTileY

    fun withPrefetch(prefetchTiles: Int): PdfTileRange {
        if (!isValid) {
            return this
        }
        val expansion = prefetchTiles.coerceAtLeast(0)
        return PdfTileRange(
            minTileX = minTileX - expansion,
            maxTileX = maxTileX + expansion,
            minTileY = minTileY - expansion,
            maxTileY = maxTileY + expansion,
        )
    }

    fun clampedToPage(
        pageMaxTileX: Int,
        pageMaxTileY: Int,
    ): PdfTileRange {
        if (!isValid || pageMaxTileX < 0 || pageMaxTileY < 0) {
            return INVALID
        }
        val clampedMinX = minTileX.coerceIn(0, pageMaxTileX)
        val clampedMaxX = this.maxTileX.coerceIn(clampedMinX, pageMaxTileX)
        val clampedMinY = minTileY.coerceIn(0, pageMaxTileY)
        val clampedMaxY = this.maxTileY.coerceIn(clampedMinY, pageMaxTileY)
        return PdfTileRange(clampedMinX, clampedMaxX, clampedMinY, clampedMaxY)
    }

    companion object {
        val INVALID = PdfTileRange(0, -1, 0, -1)
    }
}

internal fun pageRectToTileRange(
    pageRect: PdfVisiblePageRect,
    scaleBucket: Float,
    tileSizePx: Int,
): PdfTileRange {
    if (
        pageRect.left >= pageRect.right ||
        pageRect.top >= pageRect.bottom ||
        tileSizePx <= 0
    ) {
        return PdfTileRange.INVALID
    }
    val tileSizePage = tileSizePx / scaleBucket.coerceAtLeast(MIN_SCALE_BUCKET)
    val minTileX = floor(pageRect.left / tileSizePage).toInt()
    val maxTileX = floor((pageRect.right - TILE_MAX_COORD_EPSILON) / tileSizePage).toInt()
    val minTileY = floor(pageRect.top / tileSizePage).toInt()
    val maxTileY = floor((pageRect.bottom - TILE_MAX_COORD_EPSILON) / tileSizePage).toInt()
    return PdfTileRange(
        minTileX = minTileX,
        maxTileX = maxTileX,
        minTileY = minTileY,
        maxTileY = maxTileY,
    )
}

internal fun maxTileIndexForPage(
    pageWidthPoints: Float,
    pageHeightPoints: Float,
    scaleBucket: Float,
    tileSizePx: Int,
): Pair<Int, Int> {
    val tileSizePage = tileSizePx / scaleBucket.coerceAtLeast(MIN_SCALE_BUCKET)
    val maxTileX =
        max(
            0,
            ceil(pageWidthPoints / tileSizePage).toInt() - MIN_TILE_COUNT,
        )
    val maxTileY =
        max(
            0,
            ceil(pageHeightPoints / tileSizePage).toInt() - MIN_TILE_COUNT,
        )
    return Pair(maxTileX, maxTileY)
}

internal fun tileKeysForRange(
    pageIndex: Int,
    scaleBucket: Float,
    tileRange: PdfTileRange,
): List<PdfTileKey> {
    if (!tileRange.isValid) {
        return emptyList()
    }
    return buildList {
        for (tileY in tileRange.minTileY..tileRange.maxTileY) {
            for (tileX in tileRange.minTileX..tileRange.maxTileX) {
                add(
                    PdfTileKey(
                        pageIndex = pageIndex,
                        tileX = tileX,
                        tileY = tileY,
                        scaleBucket = scaleBucket,
                    ),
                )
            }
        }
    }
}

interface PdfTileRenderEngine {
    suspend fun renderTile(key: PdfTileKey): Bitmap
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class PdfTileRenderer(
    private val documentSession: PdfiumDocumentSession,
    private val tileSizePx: Int = DEFAULT_PDF_TILE_SIZE_PX,
    private val renderDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : PdfTileRenderEngine {
    private val renderMutex = Mutex()

    init {
        require(tileSizePx > 0) { "tileSizePx must be > 0" }
    }

    override suspend fun renderTile(key: PdfTileKey): Bitmap =
        withContext(renderDispatcher) {
            renderMutex.withLock {
                renderTileLocked(key)
            }
        }

    private fun renderTileLocked(key: PdfTileKey): Bitmap {
        val (pageWidthPoints, pageHeightPoints) = documentSession.getPageBounds(key.pageIndex)
        val pageWidthPx = (pageWidthPoints * key.scaleBucket).roundToInt().coerceAtLeast(1)
        val pageHeightPx = (pageHeightPoints * key.scaleBucket).roundToInt().coerceAtLeast(1)
        val tileOriginX = key.tileX * tileSizePx
        val tileOriginY = key.tileY * tileSizePx
        val bitmapWidth = min(tileSizePx, pageWidthPx - tileOriginX).coerceAtLeast(1)
        val bitmapHeight = min(tileSizePx, pageHeightPx - tileOriginY).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        runCatching {
            documentSession.renderPageBitmap(
                pageIndex = key.pageIndex,
                bitmap = bitmap,
                region =
                    PdfRenderRegion(
                        startX = -tileOriginX,
                        startY = -tileOriginY,
                        width = pageWidthPx,
                        height = pageHeightPx,
                    ),
                renderAnnotations = true,
            )
        }.onFailure { error ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
            throw error
        }
        return bitmap
    }
}
