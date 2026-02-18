package com.onyx.android.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

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

class ValidatingTile(
    val bitmap: Bitmap,
) {
    private val valid = AtomicBoolean(true)

    fun isValid(): Boolean = valid.get() && !bitmap.isRecycled

    fun invalidate() {
        valid.set(false)
    }

    fun getBitmapIfValid(): Bitmap? = if (isValid()) bitmap else null
}

interface PdfTileStore {
    suspend fun getTile(key: PdfTileKey): ValidatingTile?

    suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): ValidatingTile

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
    private val tileWrappers = mutableMapOf<Bitmap, ValidatingTile>()
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
                    val wrapper = tileWrappers.remove(oldValue)
                    wrapper?.invalidate()
                    recycleBitmap(oldValue)
                }
            }
        }

    init {
        require(tileSizePx >= MIN_TILE_SIZE_PX) { "tileSizePx must be >= $MIN_TILE_SIZE_PX" }
    }

    override suspend fun getTile(key: PdfTileKey): ValidatingTile? =
        cacheMutex.withLock {
            val bitmap = cache.get(key) ?: return@withLock null
            if (bitmap.isRecycled) {
                cache.remove(key)
                tileWrappers.remove(bitmap)
                return@withLock null
            }
            tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
        }

    override suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): ValidatingTile {
        val existing = getTile(key)
        if (existing != null) {
            if (existing.bitmap !== bitmap) {
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
                return@withLock tileWrappers.getOrPut(lockedExisting) { ValidatingTile(lockedExisting) }
            }
            if (lockedExisting?.isRecycled == true) {
                cache.remove(key)
                tileWrappers.remove(lockedExisting)
            }
            cache.put(key, bitmap)
            tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
        }
    }

    override suspend fun clear() {
        cacheMutex.withLock {
            tileWrappers.values.forEach { it.invalidate() }
            tileWrappers.clear()
            cache.evictAll()
        }
    }

    suspend fun snapshotForPage(pageIndex: Int): Map<PdfTileKey, ValidatingTile> =
        cacheMutex.withLock {
            cache
                .snapshot()
                .filterKeys { key -> key.pageIndex == pageIndex }
                .filterValues { bitmap -> !bitmap.isRecycled }
                .mapValues { (_, bitmap) ->
                    tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
                }
        }

    suspend fun snapshotForPageAndBucket(
        pageIndex: Int,
        scaleBucket: Float,
    ): Map<PdfTileKey, ValidatingTile> =
        cacheMutex.withLock {
            cache
                .snapshot()
                .filterKeys { key -> key.pageIndex == pageIndex && key.scaleBucket == scaleBucket }
                .filterValues { bitmap -> !bitmap.isRecycled }
                .mapValues { (_, bitmap) ->
                    tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
                }
        }

    suspend fun snapshotForPageWithFallback(
        pageIndex: Int,
        currentBucket: Float,
        previousBucket: Float? = null,
    ): Map<PdfTileKey, ValidatingTile> =
        cacheMutex.withLock {
            val snapshot = cache.snapshot()
            val currentBucketTiles =
                snapshot
                    .filterKeys { key ->
                        key.pageIndex == pageIndex && key.scaleBucket == currentBucket
                    }.filterValues { bitmap -> !bitmap.isRecycled }
                    .mapValues { (_, bitmap) ->
                        tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
                    }

            if (previousBucket == null || previousBucket == currentBucket) {
                return@withLock currentBucketTiles
            }

            val currentTilePositions = currentBucketTiles.keys.map { it.tileX to it.tileY }.toSet()
            val previousBucketTiles =
                snapshot
                    .filterKeys { key ->
                        key.pageIndex == pageIndex &&
                            key.scaleBucket == previousBucket &&
                            (key.tileX to key.tileY) !in currentTilePositions
                    }.filterValues { bitmap -> !bitmap.isRecycled }
                    .mapValues { (_, bitmap) ->
                        tileWrappers.getOrPut(bitmap) { ValidatingTile(bitmap) }
                    }

            currentBucketTiles + previousBucketTiles
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
