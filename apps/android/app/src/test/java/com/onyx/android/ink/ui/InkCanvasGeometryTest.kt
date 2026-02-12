package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InkCanvasGeometryTest {
    @Test
    fun `calculateBounds returns zero bounds for empty points`() {
        val bounds = calculateBounds(emptyList())
        assertEquals(0f, bounds.x, DELTA)
        assertEquals(0f, bounds.y, DELTA)
        assertEquals(0f, bounds.w, DELTA)
        assertEquals(0f, bounds.h, DELTA)
    }

    @Test
    fun `calculateBounds computes correct bounds for single point`() {
        val points = listOf(StrokePoint(x = 10f, y = 20f, t = 0L))
        val bounds = calculateBounds(points)
        assertEquals(10f, bounds.x, DELTA)
        assertEquals(20f, bounds.y, DELTA)
        assertEquals(0f, bounds.w, DELTA)
        assertEquals(0f, bounds.h, DELTA)
    }

    @Test
    fun `calculateBounds computes correct bounds for multiple points`() {
        val points = listOf(
            StrokePoint(x = 10f, y = 20f, t = 0L),
            StrokePoint(x = 50f, y = 80f, t = 1L),
            StrokePoint(x = 30f, y = 40f, t = 2L),
        )
        val bounds = calculateBounds(points)
        assertEquals(10f, bounds.x, DELTA)
        assertEquals(20f, bounds.y, DELTA)
        assertEquals(40f, bounds.w, DELTA)
        assertEquals(60f, bounds.h, DELTA)
    }

    @Test
    fun `calculateBounds with strokeWidthPadding expands bounds`() {
        val points = listOf(
            StrokePoint(x = 10f, y = 20f, t = 0L),
            StrokePoint(x = 50f, y = 60f, t = 1L),
        )
        val bounds = calculateBounds(points, strokeWidthPadding = 4f)
        // halfPadding = 2f, so min shifts left by 2, max shifts right by 2
        assertEquals(8f, bounds.x, DELTA) // 10 - 2
        assertEquals(18f, bounds.y, DELTA) // 20 - 2
        assertEquals(44f, bounds.w, DELTA) // (50-10) + 4
        assertEquals(44f, bounds.h, DELTA) // (60-20) + 4
    }

    @Test
    fun `calculateBounds with negative coordinates works correctly`() {
        val points = listOf(
            StrokePoint(x = -10f, y = -20f, t = 0L),
            StrokePoint(x = 10f, y = 20f, t = 1L),
        )
        val bounds = calculateBounds(points)
        assertEquals(-10f, bounds.x, DELTA)
        assertEquals(-20f, bounds.y, DELTA)
        assertEquals(20f, bounds.w, DELTA)
        assertEquals(40f, bounds.h, DELTA)
    }

    @Test
    fun `calculateBounds with zero padding matches no-padding behavior`() {
        val points = listOf(
            StrokePoint(x = 10f, y = 10f, t = 0L),
            StrokePoint(x = 20f, y = 20f, t = 1L),
        )
        val noPadding = calculateBounds(points)
        val zeroPadding = calculateBounds(points, strokeWidthPadding = 0f)
        assertEquals(noPadding.x, zeroPadding.x, DELTA)
        assertEquals(noPadding.y, zeroPadding.y, DELTA)
        assertEquals(noPadding.w, zeroPadding.w, DELTA)
        assertEquals(noPadding.h, zeroPadding.h, DELTA)
    }
}

private const val DELTA = 0.0001f
