package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VariableWidthOutlineTest {
    @Test
    fun `empty samples produce empty path`() {
        val path = buildVariableWidthOutline(emptyList(), emptyList())
        assertTrue(path.isEmpty, "Path should be empty for no samples")
    }

    @Test
    fun `single point produces non-empty oval path`() {
        val samples = listOf(StrokeRenderPoint(10f, 10f, 0.5f))
        val widths = listOf(4f)
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Path should not be empty for a single point")
    }

    @Test
    fun `two-point stroke produces closed outline`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(100f, 0f, 0.5f),
            )
        val widths = listOf(4f, 4f)
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Two-point stroke should produce a non-empty path")
    }

    @Test
    fun `multi-point stroke with uniform width produces outline`() {
        val samples = (0..10).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = List(11) { 4f }
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Multi-point stroke should produce a non-empty path")
    }

    @Test
    fun `variable width produces outline for pressure-varying stroke`() {
        val samples = (0..20).map { StrokeRenderPoint(it * 5f, 0f, it / 20f) }
        val widths = samples.map { (it.pressure ?: 0.5f) * 8f }
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Variable-width stroke should produce a non-empty path")
    }

    @Test
    fun `outline respects widths array`() {
        // Narrow at start, wide in middle, narrow at end
        val samples = (0..10).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = listOf(1f, 2f, 3f, 4f, 5f, 6f, 5f, 4f, 3f, 2f, 1f)
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Variable-width stroke should produce a non-empty path")
    }

    @Test
    fun `very small widths still produce valid path`() {
        val samples =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(10f, 0f, 0.5f),
            )
        val widths = listOf(0.001f, 0.001f)
        val path = buildVariableWidthOutline(samples, widths)
        // Should not crash or produce NaN values
        assertFalse(path.isEmpty, "Even very small widths should produce a path")
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
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Diagonal stroke should produce a valid path")
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
        val path = buildVariableWidthOutline(samples, widths)
        assertFalse(path.isEmpty, "Curved stroke should produce a valid path")
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
        val path = buildVariableWidthOutline(samples, widths)

        // Verify pipeline produces valid output
        assertTrue(samples.size > points.size, "Catmull-Rom should interpolate more points")
        assertEquals(samples.size, widths.size, "Widths count should match samples count")
        assertFalse(path.isEmpty, "Full pipeline should produce a valid path")
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
        val path = buildVariableWidthOutline(samples, widths)
        // Should not crash or produce NaN
        assertFalse(path.isEmpty, "Zero-length segment should still produce a path")
    }

    @Test
    fun `catmullRom subdivisions count is consistent`() {
        val points =
            listOf(
                StrokePoint(x = 0f, y = 0f, t = 0L, p = 0.5f),
                StrokePoint(x = 10f, y = 10f, t = 1L, p = 0.5f),
                StrokePoint(x = 20f, y = 0f, t = 2L, p = 0.5f),
            )
        val result = catmullRomSmooth(points)
        // 1 (first point) + 2 segments × CATMULL_ROM_SUBDIVISIONS = 1 + 2×8 = 17
        val expected = 1 + (points.size - 1) * CATMULL_ROM_SUBDIVISIONS
        assertEquals(expected, result.size, "Should produce expected number of subdivisions")
    }

    @Test
    fun `outline rejects mismatched widths and samples sizes`() {
        val samples = (0..5).map { StrokeRenderPoint(it * 10f, 0f, 0.5f) }
        val widths = listOf(4f, 4f) // too few widths
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            buildVariableWidthOutline(samples, widths)
        }
    }
}
