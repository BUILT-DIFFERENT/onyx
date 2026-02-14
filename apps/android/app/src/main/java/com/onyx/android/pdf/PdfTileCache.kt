package com.onyx.android.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val DEFAULT_PDF_TILE_CACHE_SIZE_BYTES = 64 * 1024 * 1024
private const val LOW_RAM_PDF_TILE_CACHE_SIZE_BYTES = 32 * 1024 * 1024
private const val LOW_RAM_MEMORY_CLASS_THRESHOLD_MB = 192
private const val MIN_CACHE_SIZE_BYTES = 1
private const val MIN_TILE_SIZE_PX = 1
internal const val DEFAULT_PDF_TILE_SIZE_PX = 512

data class PdfTileKey(
    val pageIndex: Int,
    val tileX: Int,
    val tileY: Int,
    val scaleBucket: Float,
)

interface PdfTileStore {
    suspend fun getTile(key: PdfTileKey): Bitmap?

    suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): Bitmap

    suspend fun clear()
}

class PdfTileCache(
    maxSizeBytes: Int = DEFAULT_PDF_TILE_CACHE_SIZE_BYTES,
    val tileSizePx: Int = DEFAULT_PDF_TILE_SIZE_PX,
    private val recycleBitmap: (Bitmap) -> Unit = { bitmap ->
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    },
) : PdfTileStore {
    private val cacheMutex = Mutex()
    private val cache =
        object : LruCache<PdfTileKey, Bitmap>(maxSizeBytes.coerceAtLeast(MIN_CACHE_SIZE_BYTES)) {
            override fun sizeOf(
                key: PdfTileKey,
                value: Bitmap,
            ): Int = value.allocationByteCount.coerceAtLeast(MIN_CACHE_SIZE_BYTES)

            override fun entryRemoved(
                evicted: Boolean,
                key: PdfTileKey,
                oldValue: Bitmap,
                newValue: Bitmap?,
            ) {
                if (oldValue !== newValue) {
                    recycleBitmap(oldValue)
                }
            }
        }

    init {
        require(tileSizePx >= MIN_TILE_SIZE_PX) { "tileSizePx must be >= $MIN_TILE_SIZE_PX" }
    }

    override suspend fun getTile(key: PdfTileKey): Bitmap? =
        cacheMutex.withLock {
            val bitmap = cache.get(key) ?: return@withLock null
            if (bitmap.isRecycled) {
                cache.remove(key)
                return@withLock null
            }
            bitmap
        }

    override suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): Bitmap {
        val existing = getTile(key)
        if (existing != null) {
            if (existing !== bitmap) {
                recycleBitmap(bitmap)
            }
            return existing
        }
        return cacheMutex.withLock {
            val lockedExisting = cache.get(key)
            if (lockedExisting != null && !lockedExisting.isRecycled) {
                if (lockedExisting !== bitmap) {
                    recycleBitmap(bitmap)
                }
                return@withLock lockedExisting
            }
            if (lockedExisting?.isRecycled == true) {
                cache.remove(key)
            }
            cache.put(key, bitmap)
            bitmap
        }
    }

    override suspend fun clear() {
        cacheMutex.withLock {
            cache.evictAll()
        }
    }

    suspend fun snapshotForPage(pageIndex: Int): Map<PdfTileKey, Bitmap> =
        cacheMutex.withLock {
            cache
                .snapshot()
                .filterKeys { key -> key.pageIndex == pageIndex }
                .filterValues { bitmap -> !bitmap.isRecycled }
        }

    suspend fun sizeBytes(): Int =
        cacheMutex.withLock {
            cache.size()
        }

    suspend fun maxSizeBytes(): Int =
        cacheMutex.withLock {
            cache.maxSize()
        }
}

internal fun resolvePdfTileCacheSizeBytes(
    isLowRamDevice: Boolean,
    memoryClassMb: Int,
): Int =
    if (isLowRamDevice || memoryClassMb < LOW_RAM_MEMORY_CLASS_THRESHOLD_MB) {
        LOW_RAM_PDF_TILE_CACHE_SIZE_BYTES
    } else {
        DEFAULT_PDF_TILE_CACHE_SIZE_BYTES
    }

fun createPdfTileCache(context: Context): PdfTileCache {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val maxSizeBytes =
        if (activityManager == null) {
            DEFAULT_PDF_TILE_CACHE_SIZE_BYTES
        } else {
            resolvePdfTileCacheSizeBytes(
                isLowRamDevice = activityManager.isLowRamDevice,
                memoryClassMb = activityManager.memoryClass,
            )
        }
    return PdfTileCache(maxSizeBytes = maxSizeBytes)
}
