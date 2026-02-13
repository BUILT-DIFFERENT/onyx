package com.onyx.android.ink.cache

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeTileCacheTest {
    @Test
    fun `expandInvalidationBounds adds half stroke width plus bleed margin`() {
        val expanded =
            expandInvalidationBounds(
                strokeBounds = TileBounds(100f, 200f, 300f, 500f),
                maxStrokeWidthPx = 20f,
            )

        assertEquals(88f, expanded.left, 0.0001f)
        assertEquals(188f, expanded.top, 0.0001f)
        assertEquals(312f, expanded.right, 0.0001f)
        assertEquals(512f, expanded.bottom, 0.0001f)
    }

    @Test
    fun `tileRangeForBounds includes all intersecting tiles`() {
        val range =
            tileRangeForBounds(
                bounds = TileBounds(256f, 256f, 1024f, 1280f),
                tileSizePx = 512,
            )

        assertEquals(0, range.minTileX)
        assertEquals(1, range.maxTileX)
        assertEquals(0, range.minTileY)
        assertEquals(2, range.maxTileY)
        assertTrue(range.contains(1, 2))
        assertFalse(range.contains(2, 2))
    }

    @Test
    fun `tileRangeForBounds returns invalid range for empty bounds`() {
        val range =
            tileRangeForBounds(
                bounds = TileBounds(left = 10f, top = 10f, right = 10f, bottom = 50f),
                tileSizePx = 512,
            )

        assertFalse(range.isValid)
    }

    @Test
    fun `strokeScaleBucketWithHysteresis prevents boundary oscillation`() {
        assertEquals(1f, strokeScaleBucketWithHysteresis(zoom = 2.1f, previousBucket = 1f), 0.0001f)
        assertEquals(2f, strokeScaleBucketWithHysteresis(zoom = 2.2f, previousBucket = 1f), 0.0001f)
        assertEquals(2f, strokeScaleBucketWithHysteresis(zoom = 1.8f, previousBucket = 2f), 0.0001f)
        assertEquals(1f, strokeScaleBucketWithHysteresis(zoom = 1.79f, previousBucket = 2f), 0.0001f)
    }

    @Test
    fun `resolveStrokeTileCacheSizeBytes switches to low ram tier`() {
        assertEquals(
            16 * 1024 * 1024,
            resolveStrokeTileCacheSizeBytes(isLowRamDevice = true, memoryClassMb = 512),
        )
        assertEquals(
            16 * 1024 * 1024,
            resolveStrokeTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 128),
        )
        assertEquals(
            32 * 1024 * 1024,
            resolveStrokeTileCacheSizeBytes(isLowRamDevice = false, memoryClassMb = 256),
        )
    }
}
