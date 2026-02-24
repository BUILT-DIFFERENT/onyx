package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokeLineStyle
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StrokeLineStylePatternTest {
    @Test
    fun `solid line style returns single full range`() {
        val samples = samplePoints(12)
        val style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f, lineStyle = StrokeLineStyle.SOLID)

        val ranges = resolveStyledSampleRanges(samples, style)

        assertEquals(1, ranges.size)
        assertEquals(0, ranges.first().first)
        assertEquals(samples.lastIndex, ranges.first().last)
    }

    @Test
    fun `dashed line style produces multiple visible ranges`() {
        val samples = samplePoints(120)
        val style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f, lineStyle = StrokeLineStyle.DASHED)

        val ranges = resolveStyledSampleRanges(samples, style)

        assertTrue(ranges.size > 1)
    }

    @Test
    fun `dotted line style produces multiple visible ranges`() {
        val samples = samplePoints(120)
        val style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f, lineStyle = StrokeLineStyle.DOTTED)

        val ranges = resolveStyledSampleRanges(samples, style)

        assertTrue(ranges.size > 1)
    }

    private fun samplePoints(count: Int): List<StrokeRenderPoint> =
        (0 until count).map { index ->
            StrokeRenderPoint(
                x = index.toFloat(),
                y = 0f,
                pressure = 0.5f,
            )
        }
}
