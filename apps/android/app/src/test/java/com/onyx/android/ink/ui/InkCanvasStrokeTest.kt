package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Brush
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InkCanvasStrokeTest {
    @Test
    fun `buildStroke creates stroke with correct number of points`() {
        val points = createTestPoints(5)
        val brush = Brush(tool = Tool.PEN, baseWidth = 4f)
        val stroke = buildStroke(points, brush)
        assertEquals(5, stroke.points.size)
    }

    @Test
    fun `buildStroke generates unique id`() {
        val points = createTestPoints(3)
        val brush = Brush(tool = Tool.PEN, baseWidth = 4f)
        val stroke1 = buildStroke(points, brush)
        val stroke2 = buildStroke(points, brush)
        assertTrue(stroke1.id != stroke2.id, "Each stroke should have a unique ID")
    }

    @Test
    fun `buildStroke sets style from brush`() {
        val brush = Brush(
            tool = Tool.PEN,
            color = "#FF0000",
            baseWidth = 3f,
            minWidthFactor = 0.5f,
            maxWidthFactor = 1.5f,
        )
        val points = createTestPoints(3)
        val stroke = buildStroke(points, brush)
        assertEquals(Tool.PEN, stroke.style.tool)
        assertEquals("#FF0000", stroke.style.color)
        assertEquals(3f, stroke.style.baseWidth, DELTA)
        assertEquals(0.5f, stroke.style.minWidthFactor, DELTA)
        assertEquals(1.5f, stroke.style.maxWidthFactor, DELTA)
    }

    @Test
    fun `buildStroke bounds include width padding`() {
        val points = listOf(
            com.onyx.android.ink.model.StrokePoint(x = 10f, y = 10f, t = 0L),
            com.onyx.android.ink.model.StrokePoint(x = 100f, y = 100f, t = 1L),
        )
        val brush = Brush(
            tool = Tool.PEN,
            baseWidth = 4f,
            maxWidthFactor = 1.5f,
        )
        val stroke = buildStroke(points, brush)

        // Without padding, bounds would be (10, 10, 90, 90)
        // With padding of baseWidth * maxWidthFactor = 4 * 1.5 = 6, halfPadding = 3
        // Expected: (7, 7, 96, 96)
        assertTrue(stroke.bounds.x < 10f, "Bounds X should be expanded: ${stroke.bounds.x}")
        assertTrue(stroke.bounds.y < 10f, "Bounds Y should be expanded: ${stroke.bounds.y}")
        assertTrue(
            stroke.bounds.w > 90f,
            "Bounds width should be expanded: ${stroke.bounds.w}",
        )
        assertTrue(
            stroke.bounds.h > 90f,
            "Bounds height should be expanded: ${stroke.bounds.h}",
        )
    }

    @Test
    fun `buildStroke uses earliest timestamp as createdAt`() {
        val points = listOf(
            com.onyx.android.ink.model.StrokePoint(x = 0f, y = 0f, t = 100L),
            com.onyx.android.ink.model.StrokePoint(x = 10f, y = 10f, t = 200L),
            com.onyx.android.ink.model.StrokePoint(x = 20f, y = 20f, t = 50L),
        )
        val brush = Brush(tool = Tool.PEN, baseWidth = 2f)
        val stroke = buildStroke(points, brush)
        assertEquals(50L, stroke.createdAt)
    }

    @Test
    fun `buildStroke with highlighter tool`() {
        val brush = Brush(tool = Tool.HIGHLIGHTER, baseWidth = 8f)
        val points = createTestPoints(4)
        val stroke = buildStroke(points, brush)
        assertEquals(Tool.HIGHLIGHTER, stroke.style.tool)
        assertNotNull(stroke.id)
    }

    private fun createTestPoints(count: Int) =
        (0 until count).map { i ->
            com.onyx.android.ink.model.StrokePoint(
                x = i * 10f,
                y = i * 5f,
                t = i.toLong(),
                p = 0.5f,
            )
        }
}

private const val DELTA = 0.0001f
