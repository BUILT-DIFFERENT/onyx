package com.onyx.android.recognition

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import com.onyx.android.objects.model.ShapeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ShapeBeautifierTest {
    @Test
    fun `detectShapeCandidate recognizes straight line`() {
        val stroke =
            stroke(
                points =
                    listOf(
                        point(0f, 0f, 0),
                        point(20f, 0f, 1),
                        point(40f, 0f, 2),
                        point(60f, 0f, 3),
                        point(80f, 0f, 4),
                        point(100f, 0f, 5),
                    ),
                bounds = StrokeBounds(x = 0f, y = 0f, w = 100f, h = 1f),
            )

        val candidate = detectShapeCandidate(stroke)
        assertNotNull(candidate)
        assertEquals(ShapeType.LINE, candidate!!.shapeType)
    }

    @Test
    fun `detectShapeCandidate recognizes closed box as rectangle`() {
        val stroke =
            stroke(
                points =
                    listOf(
                        point(0f, 0f, 0),
                        point(60f, 0f, 1),
                        point(120f, 0f, 2),
                        point(120f, 60f, 3),
                        point(120f, 120f, 4),
                        point(60f, 120f, 5),
                        point(0f, 120f, 6),
                        point(0f, 60f, 7),
                        point(0f, 0f, 8),
                    ),
                bounds = StrokeBounds(x = 0f, y = 0f, w = 120f, h = 120f),
            )

        val candidate = detectShapeCandidate(stroke)
        assertNotNull(candidate)
        assertEquals(ShapeType.RECTANGLE, candidate!!.shapeType)
    }

    @Test
    fun `detectShapeCandidate returns null for short scribble`() {
        val stroke =
            stroke(
                points =
                    listOf(
                        point(10f, 10f, 0),
                        point(12f, 11f, 1),
                        point(11f, 13f, 2),
                    ),
                bounds = StrokeBounds(x = 10f, y = 10f, w = 3f, h = 3f),
            )

        assertNull(detectShapeCandidate(stroke))
    }

    private fun stroke(
        points: List<StrokePoint>,
        bounds: StrokeBounds,
    ): Stroke =
        Stroke(
            id = "stroke-test",
            points = points,
            style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
            bounds = bounds,
            createdAt = 0L,
        )

    private fun point(
        x: Float,
        y: Float,
        t: Long,
    ): StrokePoint = StrokePoint(x = x, y = y, t = t)
}
