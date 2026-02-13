package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.ink.model.ViewTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EraserGeometryTest {
    @Test
    fun `findStrokeToErase returns null for empty strokes`() {
        val result =
            findStrokeToErase(
                screenX = 50f,
                screenY = 50f,
                strokes = emptyList(),
                viewTransform = ViewTransform.DEFAULT,
            )
        assertNull(result)
    }

    @Test
    fun `findStrokeToErase hits a multi-point stroke`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        // Touch at (50, 0) should hit the horizontal stroke
        val result =
            findStrokeToErase(
                screenX = 50f,
                screenY = 0f,
                strokes = listOf(stroke),
                viewTransform = ViewTransform.DEFAULT,
            )
        assertNotNull(result)
        assertEquals(stroke.id, result?.id)
    }

    @Test
    fun `findStrokeToErase misses a distant stroke`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        // Touch at (50, 500) is far from the stroke
        val result =
            findStrokeToErase(
                screenX = 50f,
                screenY = 500f,
                strokes = listOf(stroke),
                viewTransform = ViewTransform.DEFAULT,
            )
        assertNull(result)
    }

    @Test
    fun `findStrokeToErase hits a single-point stroke (dot)`() {
        val stroke =
            createStroke(
                listOf(StrokePoint(x = 50f, y = 50f, t = 0L)),
            )
        // Touch near the dot should hit it
        val result =
            findStrokeToErase(
                screenX = 52f,
                screenY = 50f,
                strokes = listOf(stroke),
                viewTransform = ViewTransform.DEFAULT,
            )
        assertNotNull(result, "Single-point stroke should be erasable")
        assertEquals(stroke.id, result?.id)
    }

    @Test
    fun `findStrokeToErase misses single-point stroke when far away`() {
        val stroke =
            createStroke(
                listOf(StrokePoint(x = 50f, y = 50f, t = 0L)),
            )
        // Touch far from the dot
        val result =
            findStrokeToErase(
                screenX = 200f,
                screenY = 200f,
                strokes = listOf(stroke),
                viewTransform = ViewTransform.DEFAULT,
            )
        assertNull(result, "Should not hit single-point stroke when far away")
    }

    @Test
    fun `findStrokeToErase respects zoom when calculating hit radius`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        // At 2x zoom, the hit radius in page coordinates is halved (10 / 2 = 5px)
        // Touch at (50, 3) in page space should still hit (distance = 3 < 5)
        val transform = ViewTransform(zoom = 2f, panX = 0f, panY = 0f)
        val result =
            findStrokeToErase(
                // screenX = pageX * zoom + panX = 50 * 2 + 0 = 100
                screenX = 100f,
                // screenY = pageY * zoom + panY = 3 * 2 + 0 = 6
                screenY = 6f,
                strokes = listOf(stroke),
                viewTransform = transform,
            )
        assertNotNull(result, "Should hit stroke near the line even at 2x zoom")
    }

    private fun createStroke(points: List<StrokePoint>): Stroke {
        val bounds = calculateBounds(points, strokeWidthPadding = 4f)
        return Stroke(
            id = java.util.UUID.randomUUID().toString(),
            points = points,
            style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
            bounds = bounds,
            createdAt = 0L,
        )
    }
}
