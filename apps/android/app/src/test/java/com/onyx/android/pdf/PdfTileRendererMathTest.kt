package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfTileRendererMathTest {
    @Test
    fun `pageRectToTileRange maps visible page rect to tile coordinates`() {
        val range =
            pageRectToTileRange(
                pageRect =
                    PdfVisiblePageRect(
                        left = 10f,
                        top = 40f,
                        right = 520f,
                        bottom = 1040f,
                    ),
                scaleBucket = 1f,
                tileSizePx = 512,
            )

        assertTrue(range.isValid)
        assertEquals(0, range.minTileX)
        assertEquals(1, range.maxTileX)
        assertEquals(0, range.minTileY)
        assertEquals(2, range.maxTileY)
    }

    @Test
    fun `withPrefetch and clampedToPage keep requests inside page tile bounds`() {
        val baseRange = PdfTileRange(minTileX = 1, maxTileX = 1, minTileY = 1, maxTileY = 1)
        val expanded = baseRange.withPrefetch(prefetchTiles = 2)
        val clamped = expanded.clampedToPage(pageMaxTileX = 2, pageMaxTileY = 2)

        assertEquals(0, clamped.minTileX)
        assertEquals(2, clamped.maxTileX)
        assertEquals(0, clamped.minTileY)
        assertEquals(2, clamped.maxTileY)
    }

    @Test
    fun `maxTileIndexForPage computes final tile indexes at scale`() {
        val (maxTileX, maxTileY) =
            maxTileIndexForPage(
                pageWidthPoints = 1024f,
                pageHeightPoints = 900f,
                scaleBucket = 2f,
                tileSizePx = 512,
            )

        assertEquals(3, maxTileX)
        assertEquals(3, maxTileY)
    }

    @Test
    fun `tileKeysForRange returns empty when range is invalid`() {
        val keys =
            tileKeysForRange(
                pageIndex = 3,
                scaleBucket = 2f,
                tileRange = PdfTileRange.INVALID,
            )

        assertTrue(keys.isEmpty())
    }

    @Test
    fun `clampedToPage returns invalid for negative page bounds`() {
        val clamped =
            PdfTileRange(minTileX = 0, maxTileX = 1, minTileY = 0, maxTileY = 1)
                .clampedToPage(pageMaxTileX = -1, pageMaxTileY = 2)

        assertFalse(clamped.isValid)
    }
}
