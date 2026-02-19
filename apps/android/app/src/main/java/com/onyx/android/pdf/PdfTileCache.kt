package com.onyx.android.pdf

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import androidx.collection.LruCache
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
    private val recycleBitmap: (Bitmap) -> Unit = { candidate ->
        if (!candidate.isRecycled) {
            candidate.recycle()
        }
    },
) {
    private val lifecycleLock = Any()
    private val valid = AtomicBoolean(true)
    private val recycleRequested = AtomicBoolean(false)
    private val hasRecycled = AtomicBoolean(false)
    private var activeUsers = 0

    class BitmapLease internal constructor(
        val bitmap: Bitmap,
        private val onClose: () -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                onClose()
            }
        }
    }

    fun isValid(): Boolean = valid.get() && !bitmap.isRecycled

    fun invalidate() {
        invalidateAndRecycleWhenUnused()
    }

    fun getBitmapIfValid(): Bitmap? = if (isValid()) bitmap else null

    fun acquireBitmapForUse(): BitmapLease? {
        var lease: BitmapLease? = null
        var canAcquire = false
        synchronized(lifecycleLock) {
            if (isValid()) {
                activeUsers += 1
                canAcquire = true
            }
        }
        if (canAcquire && bitmap.isRecycled) {
            releaseBitmapUse()
        } else if (canAcquire) {
            lease = BitmapLease(bitmap = bitmap, onClose = ::releaseBitmapUse)
        }
        return lease
    }

    inline fun withBitmapIfValid(block: (Bitmap) -> Unit): Boolean {
        val lease = acquireBitmapForUse() ?: return false
        return try {
            if (lease.bitmap.isRecycled) {
                false
            } else {
                block(lease.bitmap)
                true
            }
        } finally {
            lease.close()
        }
    }

    fun invalidateAndRecycleWhenUnused() {
        valid.set(false)
        recycleRequested.set(true)
        val shouldRecycleNow =
            synchronized(lifecycleLock) {
                activeUsers == 0
            }
        if (shouldRecycleNow) {
            recycleIfNeeded()
        }
    }

    private fun releaseBitmapUse() {
        val remaining =
            synchronized(lifecycleLock) {
                activeUsers = (activeUsers - 1).coerceAtLeast(0)
                activeUsers
            }
        if (remaining == 0 && recycleRequested.get()) {
            recycleIfNeeded()
        }
    }

    private fun recycleIfNeeded() {
        if (hasRecycled.compareAndSet(false, true) && !bitmap.isRecycled) {
            recycleBitmap(bitmap)
        }
    }
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
    private val cache =
        object : LruCache<PdfTileKey, ValidatingTile>(maxSizeBytes.coerceAtLeast(MIN_CACHE_SIZE_BYTES)) {
            override fun sizeOf(
                key: PdfTileKey,
                value: ValidatingTile,
            ): Int = value.bitmap.allocationByteCount.coerceAtLeast(MIN_CACHE_SIZE_BYTES)

            override fun entryRemoved(
                evicted: Boolean,
                key: PdfTileKey,
                oldValue: ValidatingTile,
                newValue: ValidatingTile?,
            ) {
                if (oldValue !== newValue) {
                    oldValue.invalidateAndRecycleWhenUnused()
                }
            }
        }

    init {
        require(tileSizePx >= MIN_TILE_SIZE_PX) { "tileSizePx must be >= $MIN_TILE_SIZE_PX" }
    }

    override suspend fun getTile(key: PdfTileKey): ValidatingTile? =
        cacheMutex.withLock {
            val tile = cache.get(key) ?: return@withLock null
            if (!tile.isValid()) {
                cache.remove(key)
                return@withLock null
            }
            tile
        }

    override suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): ValidatingTile =
        cacheMutex.withLock {
            val existing = cache.get(key)
            if (existing != null && existing.isValid()) {
                if (existing.bitmap !== bitmap) {
                    recycleBitmap(bitmap)
                }
                return@withLock existing
            }
            if (existing != null && !existing.isValid()) {
                cache.remove(key)
            }
            val tile = ValidatingTile(bitmap = bitmap, recycleBitmap = recycleBitmap)
            cache.put(key, tile)
            tile
        }

    override suspend fun clear() {
        cacheMutex.withLock {
            cache.evictAll()
        }
    }

    suspend fun snapshotForPage(pageIndex: Int): Map<PdfTileKey, ValidatingTile> =
        cacheMutex.withLock {
            cache
                .snapshot()
                .filterKeys { key -> key.pageIndex == pageIndex }
                .filterValues { tile -> tile.isValid() }
                .mapValues { (_, tile) ->
                    tile
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
                .filterValues { tile -> tile.isValid() }
                .mapValues { (_, tile) ->
                    tile
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
                    }.filterValues { tile -> tile.isValid() }
                    .mapValues { (_, tile) ->
                        tile
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
                    }.filterValues { tile -> tile.isValid() }
                    .mapValues { (_, tile) ->
                        tile
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
