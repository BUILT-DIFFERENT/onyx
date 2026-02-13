package com.onyx.android.ink.cache

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.floor

private const val DEFAULT_STROKE_TILE_CACHE_SIZE_BYTES = 32 * 1024 * 1024
private const val LOW_RAM_STROKE_TILE_CACHE_SIZE_BYTES = 16 * 1024 * 1024
private const val LOW_RAM_MEMORY_CLASS_THRESHOLD_MB = 192
private const val MIN_CACHE_SIZE_BYTES = 1
private const val MIN_TILE_SIZE_PX = 1
private const val DEFAULT_TILE_SIZE_PX = 512
private const val INVALIDATION_BLEED_MARGIN_PX = 2f
private const val TILE_MAX_COORD_EPSILON = 0.0001f
private const val STROKE_BUCKET_SWITCH_UP_MULTIPLIER = 1.1f
private const val STROKE_BUCKET_SWITCH_DOWN_MULTIPLIER = 0.9f
private const val STROKE_SCALE_BUCKET_BASE = 1f
private const val STROKE_SCALE_BUCKET_HIGH = 2f
private const val STROKE_SCALE_BUCKET_MAX = 4f

private val STROKE_SCALE_BUCKETS =
    floatArrayOf(
        STROKE_SCALE_BUCKET_BASE,
        STROKE_SCALE_BUCKET_HIGH,
        STROKE_SCALE_BUCKET_MAX,
    )

data class StrokeTileKey(
    val pageId: String,
    val tileX: Int,
    val tileY: Int,
    val scaleBucket: Float,
)

internal data class TileBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val isEmpty: Boolean
        get() = left >= right || top >= bottom
}

internal data class TileRange(
    val minTileX: Int,
    val maxTileX: Int,
    val minTileY: Int,
    val maxTileY: Int,
) {
    val isValid: Boolean
        get() = minTileX <= maxTileX && minTileY <= maxTileY

    fun contains(
        tileX: Int,
        tileY: Int,
    ): Boolean = tileX in minTileX..maxTileX && tileY in minTileY..maxTileY

    companion object {
        val INVALID = TileRange(minTileX = 0, maxTileX = -1, minTileY = 0, maxTileY = -1)
    }
}

class StrokeTileCache(
    maxSizeBytes: Int = DEFAULT_STROKE_TILE_CACHE_SIZE_BYTES,
    private val tileSizePx: Int = DEFAULT_TILE_SIZE_PX,
    private val recycleBitmap: (Bitmap) -> Unit = { bitmap ->
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    },
) {
    private val cacheMutex = Mutex()
    private val cache =
        object : LruCache<StrokeTileKey, Bitmap>(maxSizeBytes.coerceAtLeast(MIN_CACHE_SIZE_BYTES)) {
            override fun sizeOf(
                key: StrokeTileKey,
                value: Bitmap,
            ): Int = value.allocationByteCount.coerceAtLeast(MIN_CACHE_SIZE_BYTES)

            override fun entryRemoved(
                evicted: Boolean,
                key: StrokeTileKey,
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

    fun getTile(key: StrokeTileKey): Bitmap? {
        val bitmap = cache.get(key) ?: return null
        return if (bitmap.isRecycled) {
            cache.remove(key)
            null
        } else {
            bitmap
        }
    }

    suspend fun putTile(
        key: StrokeTileKey,
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

    suspend fun getOrPutTile(
        key: StrokeTileKey,
        renderTile: () -> Bitmap,
    ): Bitmap {
        val existing = getTile(key)
        if (existing != null) {
            return existing
        }
        val rendered = renderTile()
        return cacheMutex.withLock {
            val lockedExisting = cache.get(key)
            if (lockedExisting != null && !lockedExisting.isRecycled) {
                if (lockedExisting !== rendered) {
                    recycleBitmap(rendered)
                }
                return@withLock lockedExisting
            }
            if (lockedExisting?.isRecycled == true) {
                cache.remove(key)
            }
            cache.put(key, rendered)
            rendered
        }
    }

    suspend fun invalidatePage(pageId: String): Int =
        cacheMutex.withLock {
            val keysToRemove = cache.snapshot().keys.filter { key -> key.pageId == pageId }
            keysToRemove.forEach { key -> cache.remove(key) }
            keysToRemove.size
        }

    suspend fun invalidateStroke(
        pageId: String,
        strokeBounds: RectF,
        maxStrokeWidthPx: Float,
    ): Int =
        cacheMutex.withLock {
            val tileRange = strokeInvalidationTileRange(strokeBounds, maxStrokeWidthPx, tileSizePx)
            if (!tileRange.isValid) {
                return@withLock 0
            }
            val keysToRemove =
                cache.snapshot().keys.filter { key ->
                    key.pageId == pageId && tileRange.contains(key.tileX, key.tileY)
                }
            keysToRemove.forEach { key -> cache.remove(key) }
            keysToRemove.size
        }

    suspend fun clear() {
        cacheMutex.withLock {
            cache.evictAll()
        }
    }

    fun maxSizeBytes(): Int = cache.maxSize()

    fun sizeBytes(): Int = cache.size()
}

internal fun strokeInvalidationTileRange(
    strokeBounds: RectF,
    maxStrokeWidthPx: Float,
    tileSizePx: Int,
): TileRange {
    val bounds = strokeBounds.toTileBounds()
    if (bounds.isEmpty || tileSizePx < MIN_TILE_SIZE_PX) {
        return TileRange.INVALID
    }
    val expandedBounds = expandInvalidationBounds(bounds, maxStrokeWidthPx)
    return tileRangeForBounds(expandedBounds, tileSizePx)
}

internal fun expandInvalidationBounds(
    strokeBounds: TileBounds,
    maxStrokeWidthPx: Float,
): TileBounds {
    if (strokeBounds.isEmpty) {
        return strokeBounds
    }
    val expansion = (maxStrokeWidthPx.coerceAtLeast(0f) / 2f) + INVALIDATION_BLEED_MARGIN_PX
    return TileBounds(
        left = strokeBounds.left - expansion,
        top = strokeBounds.top - expansion,
        right = strokeBounds.right + expansion,
        bottom = strokeBounds.bottom + expansion,
    )
}

internal fun tileRangeForBounds(
    bounds: TileBounds,
    tileSizePx: Int,
): TileRange {
    if (bounds.isEmpty || tileSizePx < MIN_TILE_SIZE_PX) {
        return TileRange.INVALID
    }
    val minTileX = floor(bounds.left / tileSizePx).toInt()
    val minTileY = floor(bounds.top / tileSizePx).toInt()
    val maxTileX = floor((bounds.right - TILE_MAX_COORD_EPSILON) / tileSizePx).toInt()
    val maxTileY = floor((bounds.bottom - TILE_MAX_COORD_EPSILON) / tileSizePx).toInt()
    return TileRange(
        minTileX = minTileX,
        maxTileX = maxTileX,
        minTileY = minTileY,
        maxTileY = maxTileY,
    )
}

internal fun strokeScaleBucketWithHysteresis(
    zoom: Float,
    previousBucket: Float?,
): Float {
    val clampedZoom = zoom.coerceAtLeast(STROKE_SCALE_BUCKETS.first())
    val previousIndex = previousBucket?.let(::bucketIndexForValue)
    if (previousIndex == null) {
        return STROKE_SCALE_BUCKETS.firstOrNull { bucket -> clampedZoom <= bucket }
            ?: STROKE_SCALE_BUCKETS.last()
    }

    var selectedIndex = previousIndex
    while (
        selectedIndex < STROKE_SCALE_BUCKETS.lastIndex &&
        clampedZoom >= STROKE_SCALE_BUCKETS[selectedIndex + 1] * STROKE_BUCKET_SWITCH_UP_MULTIPLIER
    ) {
        selectedIndex++
    }
    while (
        selectedIndex > 0 &&
        clampedZoom < STROKE_SCALE_BUCKETS[selectedIndex] * STROKE_BUCKET_SWITCH_DOWN_MULTIPLIER
    ) {
        selectedIndex--
    }
    return STROKE_SCALE_BUCKETS[selectedIndex]
}

private fun bucketIndexForValue(bucket: Float): Int {
    val exact = STROKE_SCALE_BUCKETS.indexOfFirst { value -> value == bucket }
    if (exact >= 0) {
        return exact
    }
    val firstAtOrAbove = STROKE_SCALE_BUCKETS.indexOfFirst { value -> bucket <= value }
    return if (firstAtOrAbove >= 0) firstAtOrAbove else STROKE_SCALE_BUCKETS.lastIndex
}

private fun RectF.toTileBounds(): TileBounds =
    TileBounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
    )

internal fun resolveStrokeTileCacheSizeBytes(
    isLowRamDevice: Boolean,
    memoryClassMb: Int,
): Int =
    if (isLowRamDevice || memoryClassMb < LOW_RAM_MEMORY_CLASS_THRESHOLD_MB) {
        LOW_RAM_STROKE_TILE_CACHE_SIZE_BYTES
    } else {
        DEFAULT_STROKE_TILE_CACHE_SIZE_BYTES
    }

fun createStrokeTileCache(context: Context): StrokeTileCache {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    val maxSizeBytes =
        if (activityManager == null) {
            DEFAULT_STROKE_TILE_CACHE_SIZE_BYTES
        } else {
            resolveStrokeTileCacheSizeBytes(
                isLowRamDevice = activityManager.isLowRamDevice,
                memoryClassMb = activityManager.memoryClass,
            )
        }
    return StrokeTileCache(maxSizeBytes = maxSizeBytes)
}
