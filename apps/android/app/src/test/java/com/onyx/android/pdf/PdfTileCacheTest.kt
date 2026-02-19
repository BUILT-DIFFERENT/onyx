package com.onyx.android.pdf

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfTileCacheTest {
    @Test
    fun `resolvePdfTileCacheSizeBytes switches to low-ram tier`() {
        assertEquals(
            32 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = true, memoryClassMb = 512),
        )
        assertEquals(
            32 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 128),
        )
        assertEquals(
            64 * 1024 * 1024,
            resolvePdfTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 256),
        )
    }

    @Test
    fun `constructor rejects non-positive tile size`() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfTileCache(
                maxSizeBytes = 1024 * 1024,
                tileSizePx = 0,
            )
        }
    }

    @Test
    fun `getTile returns ValidatingTile with valid bitmap`() =
        runTest {
            val recycledBitmaps = mutableListOf<Bitmap>()
            val cache =
                PdfTileCache(
                    maxSizeBytes = 1024 * 1024,
                    recycleBitmap = { recycledBitmaps.add(it) },
                )
            val key = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val bitmap = fakeBitmap(byteCount = 100)

            val result = cache.putTile(key, bitmap)

            assertNotNull(result)
            assertTrue(result.isValid())
            assertEquals(bitmap, result.bitmap)

            val retrieved = cache.getTile(key)
            assertNotNull(retrieved)
            assertTrue(retrieved!!.isValid())
        }

    @Test
    fun `ValidatingTile invalidate prevents draw`() =
        runTest {
            val cache = PdfTileCache(maxSizeBytes = 1024 * 1024)
            val key = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val bitmap = fakeBitmap(byteCount = 100)

            val tile = cache.putTile(key, bitmap)
            assertTrue(tile.isValid())

            tile.invalidate()
            assertFalse(tile.isValid())
            assertNull(tile.getBitmapIfValid())
        }

    @Test
    fun `eviction invalidates tile and recycles bitmap`() =
        runTest {
            val recycledBitmaps = mutableListOf<Bitmap>()
            val cache =
                PdfTileCache(
                    maxSizeBytes = 200,
                    recycleBitmap = { recycledBitmaps.add(it) },
                )
            val key1 = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val key2 = PdfTileKey(pageIndex = 0, tileX = 1, tileY = 0, scaleBucket = 1f)
            val bitmap1 = fakeBitmap(byteCount = 100)
            val bitmap2 = fakeBitmap(byteCount = 150)

            val tile1 = cache.putTile(key1, bitmap1)
            assertTrue(tile1.isValid())

            cache.putTile(key2, bitmap2)

            assertTrue(tile1.isValid() == false || recycledBitmaps.contains(bitmap1))
        }

    @Test
    fun `clear invalidates all tiles`() =
        runTest {
            val cache = PdfTileCache(maxSizeBytes = 1024 * 1024)
            val key1 = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val key2 = PdfTileKey(pageIndex = 0, tileX = 1, tileY = 0, scaleBucket = 1f)

            val tile1 = cache.putTile(key1, fakeBitmap(byteCount = 100))
            val tile2 = cache.putTile(key2, fakeBitmap(byteCount = 100))

            assertTrue(tile1.isValid())
            assertTrue(tile2.isValid())

            cache.clear()

            assertFalse(tile1.isValid())
            assertFalse(tile2.isValid())
        }

    @Test
    fun `concurrent putTile operations are thread-safe`() =
        runTest {
            val cache = PdfTileCache(maxSizeBytes = 1024 * 1024)
            val keys =
                (0..10).map { i ->
                    PdfTileKey(pageIndex = 0, tileX = i, tileY = 0, scaleBucket = 1f)
                }

            val results =
                keys
                    .map { key ->
                        async {
                            val bitmap = fakeBitmap(byteCount = 50)
                            cache.putTile(key, bitmap)
                        }
                    }.awaitAll()

            results.forEach { tile ->
                assertNotNull(tile)
            }

            val size = cache.sizeBytes()
            assertTrue(size <= 1024 * 1024)
        }

    @Test
    fun `snapshot filters out invalid tiles`() =
        runTest {
            val recycledBitmaps = mutableListOf<Bitmap>()
            val cache =
                PdfTileCache(
                    maxSizeBytes = 200,
                    recycleBitmap = { recycledBitmaps.add(it) },
                )
            val key1 = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val key2 = PdfTileKey(pageIndex = 0, tileX = 1, tileY = 0, scaleBucket = 1f)

            val bitmap1 = fakeBitmap(byteCount = 100)
            val bitmap2 = fakeBitmap(byteCount = 150)
            cache.putTile(key1, bitmap1)
            cache.putTile(key2, bitmap2)

            val snapshot = cache.snapshotForPage(pageIndex = 0)

            assertTrue(snapshot.size <= 2)
            snapshot.values.forEach { tile ->
                assertTrue(tile.isValid())
            }
        }

    @Test
    fun `rapid eviction and access does not crash`() =
        runTest {
            val cache = PdfTileCache(maxSizeBytes = 300)
            val tiles = mutableListOf<ValidatingTile>()

            repeat(20) { i ->
                val key = PdfTileKey(pageIndex = 0, tileX = i, tileY = 0, scaleBucket = 1f)
                val bitmap = fakeBitmap(byteCount = 100)
                val tile = cache.putTile(key, bitmap)
                tiles.add(tile)
            }

            tiles.forEach { tile ->
                tile.getBitmapIfValid()
            }

            val size = cache.sizeBytes()
            assertTrue(size <= 300)
        }

    @Test
    fun `concurrent eviction and snapshot access`() =
        runTest {
            val cache = PdfTileCache(maxSizeBytes = 500)
            val snapshots = mutableListOf<Map<PdfTileKey, ValidatingTile>>()

            val fillJob =
                launch {
                    repeat(50) { i ->
                        val key = PdfTileKey(pageIndex = 0, tileX = i % 10, tileY = i / 10, scaleBucket = 1f)
                        val bitmap = fakeBitmap(byteCount = 100)
                        cache.putTile(key, bitmap)
                    }
                }

            val snapshotJob =
                launch {
                    repeat(20) {
                        val snapshot = cache.snapshotForPage(pageIndex = 0)
                        snapshots.add(snapshot)
                        kotlinx.coroutines.delay(1)
                    }
                }

            fillJob.join()
            snapshotJob.join()

            snapshots.forEach { snapshot ->
                snapshot.values.forEach { tile ->
                    tile.getBitmapIfValid()
                }
            }
        }

    @Test
    fun `eviction waits for active draw lease before recycle`() =
        runTest {
            val recycledBitmaps = mutableListOf<Bitmap>()
            val cache = PdfTileCache(maxSizeBytes = 180, recycleBitmap = { recycledBitmaps.add(it) })
            val key1 = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val key2 = PdfTileKey(pageIndex = 0, tileX = 1, tileY = 0, scaleBucket = 1f)
            val bitmap1 = fakeBitmap(byteCount = 100)
            val bitmap2 = fakeBitmap(byteCount = 120)

            val tile = cache.putTile(key1, bitmap1)
            val lease = tile.acquireBitmapForUse()
            assertNotNull(lease)

            cache.putTile(key2, bitmap2)
            assertFalse(recycledBitmaps.contains(bitmap1))

            lease!!.close()
            assertTrue(recycledBitmaps.contains(bitmap1))
            assertTrue(cache.sizeBytes() <= cache.maxSizeBytes())
        }

    @Test
    fun `concurrent eviction and cancellation overlap stays within budget without crashes`() =
        runTest {
            val recycledBitmaps = mutableListOf<Bitmap>()
            val cache = PdfTileCache(maxSizeBytes = 1024, recycleBitmap = { recycledBitmaps.add(it) })

            repeat(12) { i ->
                val seedKey = PdfTileKey(pageIndex = 0, tileX = i, tileY = 0, scaleBucket = 1f)
                cache.putTile(seedKey, fakeBitmap(byteCount = 90))
            }

            val workers =
                (0 until 16).map { workerIndex ->
                    launch {
                        repeat(80) { step ->
                            val key =
                                PdfTileKey(
                                    pageIndex = 0,
                                    tileX = (workerIndex + step) % 24,
                                    tileY = (workerIndex + step) / 12,
                                    scaleBucket = 1f,
                                )
                            if (step % 3 == 0) {
                                cache.putTile(key, fakeBitmap(byteCount = 96))
                            } else {
                                val tile = cache.getTile(key)
                                val lease = tile?.acquireBitmapForUse()
                                if (lease != null) {
                                    try {
                                        assertFalse(lease.bitmap.isRecycled)
                                    } finally {
                                        lease.close()
                                    }
                                }
                            }
                            if (step % 5 == 0) {
                                yield()
                            }
                        }
                    }
                }

            val cancellationJob =
                launch {
                    workers.forEachIndexed { index, job ->
                        if (index % 2 == 0) {
                            job.cancel()
                        }
                        yield()
                    }
                }

            workers.joinAll()
            cancellationJob.join()

            assertTrue(cache.sizeBytes() <= cache.maxSizeBytes())
        }
}

private fun fakeBitmap(byteCount: Int): Bitmap {
    val bitmap = mockk<Bitmap>(relaxed = true)
    every { bitmap.allocationByteCount } returns byteCount
    every { bitmap.isRecycled } returns false
    every { bitmap.width } returns 512
    every { bitmap.height } returns 512
    return bitmap
}
