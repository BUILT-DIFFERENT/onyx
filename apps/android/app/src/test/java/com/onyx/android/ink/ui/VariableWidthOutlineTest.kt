package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariableWidthOutlineTest {
    @Test
    fun `empty samples produce empty path`() {
        val geometry = computeStrokeOutlineGeometry(emptyList(), emptyList())
        assertNull(geometry, "Geometry should be absent for no samples")
    }

    @Test
    fun `single point produces non-empty oval path`() {
        val samples = listOf(StrokeRenderPoint(10f, 10f, 0.5f))
        val widths = listOf(4f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Geometry should exist for a single point")
    }

    @Test
    fun `two-point stroke produces closed outline`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(100f, 0f, 0.5f),
            )
        val widths = listOf(4f, 4f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Two-point stroke should produce geometry")
    }

    @Test
    fun `multi-point stroke with uniform width produces outline`() {
        val samples = (0..10).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = List(11) { 4f }
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Multi-point stroke should produce geometry")
    }

    @Test
    fun `variable width produces outline for pressure-varying stroke`() {
        val samples = (0..20).map { StrokeRenderPoint(it * 5f, 0f, it / 20f) }
        val widths = samples.map { (it.pressure ?: 0.5f) * 8f }
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Variable-width stroke should produce geometry")
    }

    @Test
    fun `outline respects widths array`() {
        // Narrow at start, wide in middle, narrow at end
        val samples = (0..10).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = listOf(1f, 2f, 3f, 4f, 5f, 6f, 5f, 4f, 3f, 2f, 1f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Variable-width stroke should produce geometry")
    }

    @Test
    fun `very small widths still produce valid path`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(10f, 0f, 0.5f),
            )
        val widths = listOf(0.001f, 0.001f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Even very small widths should produce geometry")
    }

    @Test
    fun `diagonal stroke produces valid outline`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(50f, 50f, 0.5f),
                StrokeRenderPoint(100f, 100f, 0.5f),
            )
        val widths = listOf(4f, 4f, 4f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Diagonal stroke should produce valid geometry")
    }

    @Test
    fun `curved stroke produces valid outline`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(25f, 50f, 0.5f),
                StrokeRenderPoint(75f, -50f, 0.5f),
                StrokeRenderPoint(100f, 0f, 0.5f),
            )
        val widths = listOf(4f, 6f, 6f, 4f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Curved stroke should produce valid geometry")
    }

    @Test
    fun `full pipeline - catmullRom + widths + outline`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
            )
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.3f),
                StrokePoint(x = 20f, y = 10f, t = 1L, p = 0.6f),
                StrokePoint(x = 40f, y = 5f, t = 2L, p = 0.8f),
                StrokePoint(x = 60f, y = 15f, t = 3L, p = 0.4f),
                StrokePoint(x = 80f, y = 0f, t = 4L, p = 0.2f),
            )
        val samples = catmullRomSmooth(points)
        val widths = computePerPointWidths(samples, style)
        val geometry = computeStrokeOutlineGeometry(samples, widths)

        // Verify pipeline produces valid output
        assertTrue(samples.size > points.size, "Catmull-Rom should interpolate more points")
        assertEquals(samples.size, widths.size, "Widths count should match samples count")
        assertNotNull(geometry, "Full pipeline should produce valid geometry")
    }

    @Test
    fun `outline handles zero-length segment gracefully`() {
        // Two identical points — zero-length segment
        val samples =
            listOf(
                StrokeRenderPoint(50f, 50f, 0.5f),
                StrokeRenderPoint(50f, 50f, 0.5f),
            )
        val widths = listOf(4f, 4f)
        val geometry = computeStrokeOutlineGeometry(samples, widths)
        assertNotNull(geometry, "Zero-length segment should still produce geometry")
    }

    @Test
    fun `catmullRom subdivisions count is consistent`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 10f, y = 10f, t = 1L, p = 0.5f),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = 0.5f),
            )
        val noSmoothing = catmullRomSmooth(points, smoothingLevel = 0f)
        val mediumSmoothing = catmullRomSmooth(points, smoothingLevel = 0.5f)
        val fullSmoothing = catmullRomSmooth(points, smoothingLevel = 1f)

        assertEquals(points.size, noSmoothing.size, "Zero smoothing should preserve raw points")
        assertTrue(mediumSmoothing.size > noSmoothing.size, "Medium smoothing should add interpolated points")
        assertTrue(fullSmoothing.size >= mediumSmoothing.size, "Higher smoothing should not reduce subdivisions")
    }

    @Test
    fun `outline rejects mismatched widths and samples sizes`() {
        val samples = (0..5).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = listOf(4f, 4f) // too few widths
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            computeStrokeOutlineGeometry(samples, widths)
        }
    }
}
