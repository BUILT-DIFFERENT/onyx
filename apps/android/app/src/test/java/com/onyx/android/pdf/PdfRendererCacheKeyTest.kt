package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfRendererCacheKeyTest {
    @Test
    fun `render scale cache key rounds to stable thousandth precision`() {
        val keyA = renderScaleCacheKey(1.50001f)
        val keyB = renderScaleCacheKey(1.50049f)
        val keyC = renderScaleCacheKey(1.5005f)

        assertEquals(1500, keyA)
        assertEquals(1500, keyB)
        assertEquals(1501, keyC)
    }

    @Test
    fun `render scale cache key clamps non-positive values`() {
        assertEquals(1, renderScaleCacheKey(0f))
        assertEquals(1, renderScaleCacheKey(-3f))
    }
}
