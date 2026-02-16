package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeTaperingTest {
    @Test
    fun `computeTaperFactor returns 1 for middle points`() {
        val factor = computeTaperFactor(index = 10, totalPoints = 20)
        assertEquals(1f, factor, 0.0001f)
    }

    @Test
    fun `computeTaperFactor returns min factor at index 0`() {
        val factor = computeTaperFactor(index = 0, totalPoints = 20, taperStrength = 1f)
        assertEquals(0.35f, factor, 0.0001f)
    }

    @Test
    fun `computeTaperFactor returns min factor at last index`() {
        val factor = computeTaperFactor(index = 19, totalPoints = 20, taperStrength = 1f)
        assertEquals(0.35f, factor, 0.0001f)
    }

    @Test
    fun `computeTaperFactor increases from start`() {
        val factors = (0 until 6).map { computeTaperFactor(it, 20) }
        // Should monotonically increase from start to the taper boundary
        for (i in 0 until factors.size - 1) {
            assertTrue(
                factors[i] <= factors[i + 1],
                "Factor at $i (${factors[i]}) should be <= factor at ${i + 1} (${factors[i + 1]})",
            )
        }
    }

    @Test
    fun `computeTaperFactor decreases toward end`() {
        val factors = (14 until 20).map { computeTaperFactor(it, 20) }
        // Should monotonically decrease toward the end
        for (i in 0 until factors.size - 1) {
            assertTrue(
                factors[i] >= factors[i + 1],
                "Factor at ${14 + i} (${factors[i]}) should be >= factor at ${14 + i + 1} (${factors[i + 1]})",
            )
        }
    }

    @Test
    fun `computeTaperFactor returns 1 for single point`() {
        val factor = computeTaperFactor(index = 0, totalPoints = 1)
        assertEquals(1f, factor, 0.0001f)
    }

    @Test
    fun `computeTaperFactor handles very short strokes`() {
        val factors = (0 until 3).map { computeTaperFactor(it, 3, taperStrength = 1f) }
        assertTrue(factors.all { it < 1f }, "Short strokes should still taper, but not collapse")
        assertEquals(factors[0], factors[1], 0.0001f)
        assertEquals(factors[1], factors[2], 0.0001f)
    }

    @Test
    fun `computeTaperFactor can disable tapering`() {
        val factors = (0 until 20).map { computeTaperFactor(it, 20, taperStrength = 0f) }
        assertTrue(factors.all { it == 1f })
    }

    @Test
    fun `computePerPointWidths returns empty for empty input`() {
        val style = StrokeStyle(tool = Tool.PEN, baseWidth = 4f)
        val result = computePerPointWidths(emptyList(), style)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computePerPointWidths applies gamma curve`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
            )
        val points =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.5f),
                StrokeRenderPoint(10f, 0f, 0.5f),
                StrokeRenderPoint(20f, 0f, 0.5f),
                StrokeRenderPoint(30f, 0f, 0.5f),
                StrokeRenderPoint(40f, 0f, 0.5f),
                StrokeRenderPoint(50f, 0f, 0.5f),
                StrokeRenderPoint(60f, 0f, 0.5f),
                StrokeRenderPoint(70f, 0f, 0.5f),
                StrokeRenderPoint(80f, 0f, 0.5f),
                StrokeRenderPoint(90f, 0f, 0.5f),
                StrokeRenderPoint(100f, 0f, 0.5f),
            )
        val widths = computePerPointWidths(points, style)
        assertEquals(11, widths.size)

        // All widths should be positive
        widths.forEach { w -> assertTrue(w > 0f, "Width should be positive, got $w") }

        // Middle widths should be wider than edge widths (due to tapering)
        val middleWidth = widths[5]
        val firstWidth = widths[0]
        val lastWidth = widths[10]
        assertTrue(middleWidth > firstWidth, "Middle should be wider than start")
        assertTrue(middleWidth > lastWidth, "Middle should be wider than end")
    }

    @Test
    fun `computePerPointWidths varies with pressure`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                baseWidth = 4f,
                minWidthFactor = 0.5f,
                maxWidthFactor = 1.5f,
            )
        // Enough points so middle points have no tapering
        val lowPressurePoints = (0..20).map { StrokeRenderPoint(it.toFloat(), 0f, 0.1f) }
        val highPressurePoints = (0..20).map { StrokeRenderPoint(it.toFloat(), 0f, 0.9f) }

        val lowWidths = computePerPointWidths(lowPressurePoints, style)
        val highWidths = computePerPointWidths(highPressurePoints, style)

        // Middle point (index 10) should be wider at high pressure
        assertTrue(
            highWidths[10] > lowWidths[10],
            "High pressure (${highWidths[10]}) should produce wider stroke than low (${lowWidths[10]})",
        )
    }

    @Test
    fun `computePerPointWidths keeps practical minimum width on short strokes`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                baseWidth = 2f,
                minWidthFactor = 0.3f,
                maxWidthFactor = 0.5f,
                endTaperStrength = 1f,
            )
        val points =
            listOf(
                StrokeRenderPoint(0f, 0f, 0.1f),
                StrokeRenderPoint(5f, 0f, 0.2f),
                StrokeRenderPoint(10f, 0f, 0.1f),
            )
        val widths = computePerPointWidths(points, style)
        assertTrue(widths.all { it > 0.3f }, "Widths should not collapse to near-zero on short strokes")
    }
}
