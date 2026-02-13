package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokePoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CatmullRomSmoothTest {
    @Test
    fun `catmullRomValue returns v1 at t=0`() {
        val result = catmullRomValue(0f, 10f, 20f, 30f, 0f)
        assertEquals(10f, result, 0.0001f)
    }

    @Test
    fun `catmullRomValue returns v2 at t=1`() {
        val result = catmullRomValue(0f, 10f, 20f, 30f, 1f)
        assertEquals(20f, result, 0.0001f)
    }

    @Test
    fun `catmullRomValue interpolates smoothly at midpoint`() {
        val result = catmullRomValue(0f, 10f, 20f, 30f, 0.5f)
        // For uniform spacing the midpoint should be close to the midpoint of v1 and v2
        assertTrue(result > 10f && result < 20f, "Midpoint $result should be between 10 and 20")
    }

    @Test
    fun `catmullRomSmooth passes through original points for 3 input points`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 10f, y = 10f, t = 1L, p = 0.5f),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = 0.5f),
            )
        val result = catmullRomSmooth(points)

        // First point should match exactly
        assertEquals(0f, result.first().x, 0.0001f)
        assertEquals(0f, result.first().y, 0.0001f)

        // Last point should match exactly (last subdivision of last segment)
        assertEquals(20f, result.last().x, 0.0001f)
        assertEquals(0f, result.last().y, 0.0001f)
    }

    @Test
    fun `catmullRomSmooth produces more points than input`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 10f, y = 10f, t = 1L, p = 0.5f),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = 0.5f),
            )
        val result = catmullRomSmooth(points)
        // Should produce 1 (first) + 2 segments × 8 subdivisions = 17 points
        assertTrue(result.size > points.size, "Smoothed should have more points than input")
        assertEquals(17, result.size)
    }

    @Test
    fun `catmullRomSmooth returns input unchanged for 2 or fewer points`() {
        val twoPoints =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 10f, y = 10f, t = 1L, p = 0.5f),
            )
        val result = catmullRomSmooth(twoPoints)
        assertEquals(2, result.size)
        assertEquals(0f, result[0].x, 0.0001f)
        assertEquals(10f, result[1].x, 0.0001f)
    }

    @Test
    fun `catmullRomSmooth returns empty list for empty input`() {
        val result = catmullRomSmooth(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `catmullRomSmooth returns single point for single input`() {
        val points = listOf(StrokePoint(x = 5f, y = 5f, t = 0L, p = 0.5f))
        val result = catmullRomSmooth(points)
        assertEquals(1, result.size)
        assertEquals(5f, result[0].x, 0.0001f)
    }

    @Test
    fun `catmullRomSmooth interpolates pressure along with position`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.2f),
                StrokePoint(x = 10f, y = 0f, t = 1L, p = 0.8f),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = 0.3f),
            )
        val result = catmullRomSmooth(points)
        // All pressures should be clamped to [0, 1]
        result.forEach { point ->
            val pressure = point.pressure ?: 0f
            assertTrue(pressure in 0f..1f, "Pressure $pressure out of range")
        }
    }

    @Test
    fun `catmullRomSmooth handles null pressure with fallback`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = null),
                StrokePoint(x = 10f, y = 0f, t = 1L, p = null),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = null),
            )
        val result = catmullRomSmooth(points)
        // With null pressure, all should use fallback (0.5)
        result.drop(1).forEach { point ->
            val pressure = point.pressure ?: 0f
            assertTrue(pressure in 0f..1f, "Pressure $pressure out of range")
        }
    }

    @Test
    fun `catmullRomSmooth produces smooth curve for curved input`() {
        // Create an S-curve shape
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 5f, y = 10f, t = 1L, p = 0.5f),
                StrokePoint(x = 15f, y = -10f, t = 2L, p = 0.5f),
                StrokePoint(x = 20f, y = 0f, t = 3L, p = 0.5f),
            )
        val result = catmullRomSmooth(points)
        // Expect 1 + 3*8 = 25 interpolated points
        assertEquals(25, result.size)
        // First and last should match input
        assertEquals(0f, result.first().x, 0.0001f)
        assertEquals(20f, result.last().x, 0.0001f)
    }
}
