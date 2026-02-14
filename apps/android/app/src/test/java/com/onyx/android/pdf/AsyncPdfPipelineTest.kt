package com.onyx.android.pdf

import android.graphics.Bitmap
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AsyncPdfPipelineTest {
    @Test
    fun `requestTiles renders uncached key once`() =
        runTest {
            val key = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val renderer = FakeRenderer(mapOf(key to fakeBitmap()))
            val cache = FakeTileStore()
            val pipeline = AsyncPdfPipeline(renderer = renderer, cache = cache, scope = this, maxInFlightRenders = 2)

            pipeline.requestTiles(listOf(key))
            advanceUntilIdle()

            assertEquals(1, renderer.renderCount(key))
            assertTrue(cache.contains(key))
        }

    @Test
    fun `requestTiles does not duplicate in-flight render for same key`() =
        runTest {
            val key = PdfTileKey(pageIndex = 1, tileX = 2, tileY = 3, scaleBucket = 2f)
            val renderer =
                FakeRenderer(
                    bitmaps = mapOf(key to fakeBitmap()),
                    gate = CompletableDeferred(),
                    gatedKeys = setOf(key),
                )
            val cache = FakeTileStore()
            val pipeline = AsyncPdfPipeline(renderer = renderer, cache = cache, scope = this, maxInFlightRenders = 1)

            pipeline.requestTiles(listOf(key))
            pipeline.requestTiles(listOf(key))
            renderer.release(key)
            advanceUntilIdle()

            assertEquals(1, renderer.renderCount(key))
        }

    @Test
    fun `requestTiles cancels jobs for keys that leave viewport`() =
        runTest {
            val keyA = PdfTileKey(pageIndex = 0, tileX = 0, tileY = 0, scaleBucket = 1f)
            val keyB = PdfTileKey(pageIndex = 0, tileX = 1, tileY = 0, scaleBucket = 1f)
            val renderer =
                FakeRenderer(
                    bitmaps = mapOf(keyA to fakeBitmap(), keyB to fakeBitmap()),
                    gate = CompletableDeferred(),
                    gatedKeys = setOf(keyA),
                )
            val cache = FakeTileStore()
            val pipeline = AsyncPdfPipeline(renderer = renderer, cache = cache, scope = this, maxInFlightRenders = 1)

            pipeline.requestTiles(listOf(keyA))
            delay(1)
            pipeline.requestTiles(listOf(keyB))
            renderer.release(keyA)
            advanceUntilIdle()

            assertTrue(renderer.wasCancelled(keyA))
            assertEquals(1, renderer.renderCount(keyB))
            assertTrue(cache.contains(keyB))
        }
}

private class FakeTileStore : PdfTileStore {
    private val tiles = linkedMapOf<PdfTileKey, Bitmap>()

    override suspend fun getTile(key: PdfTileKey): Bitmap? = tiles[key]

    override suspend fun putTile(
        key: PdfTileKey,
        bitmap: Bitmap,
    ): Bitmap {
        val existing = tiles[key]
        if (existing != null) {
            return existing
        }
        tiles[key] = bitmap
        return bitmap
    }

    override suspend fun clear() {
        tiles.clear()
    }

    fun contains(key: PdfTileKey): Boolean = tiles.containsKey(key)
}

private class FakeRenderer(
    private val bitmaps: Map<PdfTileKey, Bitmap>,
    private val gate: CompletableDeferred<Unit>? = null,
    private val gatedKeys: Set<PdfTileKey> = emptySet(),
) : PdfTileRenderEngine {
    private val renderCounts = linkedMapOf<PdfTileKey, Int>()
    private val cancelledKeys = mutableSetOf<PdfTileKey>()

    override suspend fun renderTile(key: PdfTileKey): Bitmap {
        renderCounts[key] = (renderCounts[key] ?: 0) + 1
        if (key in gatedKeys) {
            try {
                gate?.await()
            } catch (cancellation: CancellationException) {
                cancelledKeys += key
                throw cancellation
            }
        }
        return bitmaps.getValue(key)
    }

    fun renderCount(key: PdfTileKey): Int = renderCounts[key] ?: 0

    fun wasCancelled(key: PdfTileKey): Boolean = key in cancelledKeys

    fun release(key: PdfTileKey) {
        if (key in gatedKeys) {
            gate?.complete(Unit)
        }
    }
}

private fun fakeBitmap(): Bitmap {
    val bitmap = mockk<Bitmap>(relaxed = true)
    every { bitmap.isRecycled } returns false
    return bitmap
}
