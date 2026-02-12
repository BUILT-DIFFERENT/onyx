package com.onyx.android.ink.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ColorCacheTest {
    @BeforeEach
    fun resetCache() {
        // ColorCache is an object singleton, so we verify it resolves colors consistently
        // across calls. We can't truly reset it but we can verify idempotency.
    }

    @Test
    fun `resolve returns valid color int for black`() {
        val result = ColorCache.resolve("#000000")
        // parseColor("#000000") returns 0xFF000000.toInt() = -16777216
        assertEquals(0xFF000000.toInt(), result)
    }

    @Test
    fun `resolve returns valid color int for white`() {
        val result = ColorCache.resolve("#FFFFFF")
        assertEquals(0xFFFFFFFF.toInt(), result)
    }

    @Test
    fun `resolve returns valid color int for red`() {
        val result = ColorCache.resolve("#FF0000")
        assertEquals(0xFFFF0000.toInt(), result)
    }

    @Test
    fun `resolve returns consistent results for same input`() {
        val first = ColorCache.resolve("#1E88E5")
        val second = ColorCache.resolve("#1E88E5")
        assertEquals(first, second)
    }

    @Test
    fun `resolve handles lowercase hex`() {
        val upper = ColorCache.resolve("#FF0000")
        val lower = ColorCache.resolve("#ff0000")
        assertEquals(upper, lower)
    }

    @Test
    fun `resolve handles 8-digit ARGB hex`() {
        val result = ColorCache.resolve("#80FF0000")
        // Alpha = 0x80 = 128, R=255, G=0, B=0
        val alpha = (result ushr 24) and 0xFF
        val red = (result ushr 16) and 0xFF
        assertEquals(128, alpha)
        assertEquals(255, red)
    }

    @Test
    fun `resolve produces different colors for different inputs`() {
        val red = ColorCache.resolve("#FF0000")
        val blue = ColorCache.resolve("#0000FF")
        assertNotEquals(red, blue)
    }
}
