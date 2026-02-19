package com.onyx.android.ink.ui

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.system.measureTimeMillis

class LassoTransformTest {
    @Test
    fun `moveStrokes translates stroke points by delta`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 50f, t = 1L),
                ),
            )
        val moved = moveStrokes(listOf(stroke), deltaX = 50f, deltaY = 25f)

        assertEquals(1, moved.size)
        assertEquals(50f, moved[0].points[0].x, DELTA)
        assertEquals(25f, moved[0].points[0].y, DELTA)
        assertEquals(150f, moved[0].points[1].x, DELTA)
        assertEquals(75f, moved[0].points[1].y, DELTA)
    }

    @Test
    fun `moveStrokes preserves stroke style and id`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        val moved = moveStrokes(listOf(stroke), deltaX = 10f, deltaY = 10f)

        assertEquals(stroke.id, moved[0].id)
        assertEquals(stroke.style.tool, moved[0].style.tool)
        assertEquals(stroke.style.color, moved[0].style.color)
    }

    @Test
    fun `moveStrokes updates bounds`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 50f, t = 1L),
                ),
                baseWidth = 0f,
            )
        val moved = moveStrokes(listOf(stroke), deltaX = 25f, deltaY = 10f)

        assertEquals(25f, moved[0].bounds.x, DELTA)
        assertEquals(10f, moved[0].bounds.y, DELTA)
        assertEquals(100f, moved[0].bounds.w, DELTA)
        assertEquals(50f, moved[0].bounds.h, DELTA)
    }

    @Test
    fun `moveStrokes handles empty list`() {
        val moved = moveStrokes(emptyList(), deltaX = 10f, deltaY = 10f)
        assertTrue(moved.isEmpty())
    }

    @Test
    fun `resizeStrokes scales around pivot`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 100f, y = 100f, t = 0L),
                    StrokePoint(x = 200f, y = 100f, t = 1L),
                ),
            )
        val pivotX = 100f
        val pivotY = 100f
        val scale = 2f

        val resized = resizeStrokes(listOf(stroke), scale, pivotX, pivotY)

        assertEquals(1, resized.size)
        assertEquals(100f, resized[0].points[0].x, DELTA)
        assertEquals(100f, resized[0].points[0].y, DELTA)
        assertEquals(300f, resized[0].points[1].x, DELTA)
        assertEquals(100f, resized[0].points[1].y, DELTA)
    }

    @Test
    fun `resizeStrokes scales stroke width`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
                baseWidth = 2f,
            )
        val scale = 2f

        val resized = resizeStrokes(listOf(stroke), scale, pivotX = 0f, pivotY = 0f)

        assertEquals(4f, resized[0].style.baseWidth, DELTA)
    }

    @Test
    fun `resizeStrokes handles scale of 1`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 50f, y = 50f, t = 0L),
                    StrokePoint(x = 100f, y = 50f, t = 1L),
                ),
            )

        val resized = resizeStrokes(listOf(stroke), scale = 1f, pivotX = 0f, pivotY = 0f)

        assertEquals(stroke.points[0].x, resized[0].points[0].x, DELTA)
        assertEquals(stroke.points[0].y, resized[0].points[0].y, DELTA)
        assertEquals(stroke.points[1].x, resized[0].points[1].x, DELTA)
        assertEquals(stroke.points[1].y, resized[0].points[1].y, DELTA)
    }

    @Test
    fun `transformStroke combines translate and scale`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 50f, t = 1L),
                ),
            )
        val transformed =
            transformStroke(
                stroke,
                translateX = 50f,
                translateY = 25f,
                scaleX = 2f,
                scaleY = 2f,
                pivotX = 0f,
                pivotY = 0f,
            )

        assertEquals(50f, transformed.points[0].x, DELTA)
        assertEquals(25f, transformed.points[0].y, DELTA)
        assertEquals(250f, transformed.points[1].x, DELTA)
        assertEquals(125f, transformed.points[1].y, DELTA)
    }

    @Test
    fun `calculateSelectionBounds returns null for empty list`() {
        val bounds = calculateSelectionBounds(emptyList())
        assertEquals(null, bounds)
    }

    @Test
    fun `calculateSelectionBounds computes correct bounds for multiple strokes`() {
        val strokes =
            listOf(
                createStroke(
                    listOf(
                        StrokePoint(x = 0f, y = 0f, t = 0L),
                        StrokePoint(x = 100f, y = 50f, t = 1L),
                    ),
                    baseWidth = 0f,
                ),
                createStroke(
                    listOf(
                        StrokePoint(x = 50f, y = 100f, t = 0L),
                        StrokePoint(x = 200f, y = 150f, t = 1L),
                    ),
                    baseWidth = 0f,
                ),
            )
        val bounds = calculateSelectionBounds(strokes)

        assertEquals(0f, bounds!!.x, DELTA)
        assertEquals(0f, bounds.y, DELTA)
        assertEquals(200f, bounds.w, DELTA)
        assertEquals(150f, bounds.h, DELTA)
    }

    @Test
    fun `findStrokesInLasso returns empty set for empty polygon`() {
        val strokes =
            listOf(
                createStroke(
                    listOf(
                        StrokePoint(x = 50f, y = 50f, t = 0L),
                    ),
                ),
            )
        val result = findStrokesInLasso(emptyList(), strokes, createSpatialIndex(strokes))

        assertTrue(result.isEmpty())
    }

    @Test
    fun `findStrokesInLasso returns strokes inside polygon`() {
        val insideStroke =
            createStroke(
                listOf(
                    StrokePoint(x = 50f, y = 50f, t = 0L),
                    StrokePoint(x = 60f, y = 50f, t = 1L),
                ),
            )
        val outsideStroke =
            createStroke(
                listOf(
                    StrokePoint(x = 200f, y = 200f, t = 0L),
                    StrokePoint(x = 210f, y = 200f, t = 1L),
                ),
            )
        val strokes = listOf(insideStroke, outsideStroke)

        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )
        val result = findStrokesInLasso(polygon, strokes, createSpatialIndex(strokes))

        assertEquals(1, result.size)
        assertTrue(insideStroke.id in result)
        assertFalse(outsideStroke.id in result)
    }

    @Test
    fun `pointInPolygon returns true for point inside square`() {
        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )

        assertTrue(pointInPolygon(50f, 50f, polygon))
        assertTrue(pointInPolygon(1f, 1f, polygon))
        assertTrue(pointInPolygon(99f, 99f, polygon))
    }

    @Test
    fun `pointInPolygon returns false for point outside square`() {
        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )

        assertFalse(pointInPolygon(150f, 50f, polygon))
        assertFalse(pointInPolygon(-10f, 50f, polygon))
        assertFalse(pointInPolygon(50f, 150f, polygon))
    }

    @Test
    fun `pointInPolygon handles concave polygon`() {
        val lShapedPolygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 50f),
                Pair(50f, 50f),
                Pair(50f, 100f),
                Pair(0f, 100f),
            )

        assertTrue(pointInPolygon(25f, 25f, lShapedPolygon))
        assertTrue(pointInPolygon(75f, 25f, lShapedPolygon))
        assertTrue(pointInPolygon(25f, 75f, lShapedPolygon))
        assertFalse(pointInPolygon(75f, 75f, lShapedPolygon))
    }

    @Test
    fun `boundsIntersectPolygon returns true for overlapping bounds`() {
        val polygon =
            listOf(
                Pair(50f, 50f),
                Pair(150f, 50f),
                Pair(150f, 150f),
                Pair(50f, 150f),
            )
        val bounds =
            com.onyx.android.ink.model
                .StrokeBounds(x = 0f, y = 0f, w = 100f, h = 100f)

        assertTrue(boundsIntersectPolygon(bounds, polygon))
    }

    @Test
    fun `boundsIntersectPolygon returns false for non-overlapping bounds`() {
        val polygon =
            listOf(
                Pair(200f, 200f),
                Pair(300f, 200f),
                Pair(300f, 300f),
                Pair(200f, 300f),
            )
        val bounds =
            com.onyx.android.ink.model
                .StrokeBounds(x = 0f, y = 0f, w = 100f, h = 100f)

        assertFalse(boundsIntersectPolygon(bounds, polygon))
    }

    @Test
    fun `isStrokeInsidePolygon returns true for stroke fully inside`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 50f, y = 50f, t = 0L),
                    StrokePoint(x = 80f, y = 80f, t = 1L),
                ),
            )
        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )

        assertTrue(isStrokeInsidePolygon(stroke, polygon))
    }

    @Test
    fun `isStrokeInsidePolygon returns true for stroke partially inside`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 80f, y = 50f, t = 0L),
                    StrokePoint(x = 150f, y = 50f, t = 1L),
                ),
            )
        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )

        assertTrue(isStrokeInsidePolygon(stroke, polygon))
    }

    @Test
    fun `isStrokeInsidePolygon returns false for stroke fully outside`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 200f, y = 200f, t = 0L),
                    StrokePoint(x = 250f, y = 250f, t = 1L),
                ),
            )
        val polygon =
            listOf(
                Pair(0f, 0f),
                Pair(100f, 0f),
                Pair(100f, 100f),
                Pair(0f, 100f),
            )

        assertFalse(isStrokeInsidePolygon(stroke, polygon))
    }

    @Test
    fun `resize preserves point order and metadata`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 100L, p = 0.5f, tx = 0.1f, ty = 0.2f, r = 0.3f),
                    StrokePoint(x = 100f, y = 100f, t = 200L, p = 0.8f, tx = 0.4f, ty = 0.5f, r = 0.6f),
                ),
            )
        val resized = resizeStrokes(listOf(stroke), scale = 2f, pivotX = 0f, pivotY = 0f)

        assertEquals(2, resized[0].points.size)
        assertEquals(100L, resized[0].points[0].t)
        assertEquals(200L, resized[0].points[1].t)
        assertEquals(0.5f, resized[0].points[0].p!!, DELTA)
        assertEquals(0.8f, resized[0].points[1].p!!, DELTA)
        assertEquals(0.1f, resized[0].points[0].tx!!, DELTA)
        assertEquals(0.4f, resized[0].points[1].tx!!, DELTA)
    }

    @Test
    fun `resize with scale less than 1 shrinks strokes`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 100f, t = 1L),
                ),
            )
        val resized = resizeStrokes(listOf(stroke), scale = 0.5f, pivotX = 0f, pivotY = 0f)

        assertEquals(50f, resized[0].points[1].x, DELTA)
        assertEquals(50f, resized[0].points[1].y, DELTA)
        assertTrue(resized[0].style.baseWidth < stroke.style.baseWidth)
    }

    @Test
    fun `move and resize combined preserves stroke integrity`() {
        val originalStroke =
            createStroke(
                listOf(
                    StrokePoint(x = 100f, y = 100f, t = 0L),
                    StrokePoint(x = 200f, y = 200f, t = 1L),
                ),
                baseWidth = 2f,
            )

        val moved = moveStrokes(listOf(originalStroke), deltaX = 50f, deltaY = 50f)
        val resized = resizeStrokes(moved, scale = 1.5f, pivotX = 150f, pivotY = 150f)

        assertEquals(1, resized.size)
        assertEquals(2, resized[0].points.size)
        assertEquals(originalStroke.id, resized[0].id)
        assertTrue(abs(resized[0].style.baseWidth - 3f) < DELTA)
    }

    @Test
    fun `move inverse round trip restores original geometry`() {
        val original =
            createStroke(
                listOf(
                    StrokePoint(x = 20f, y = 30f, t = 0L),
                    StrokePoint(x = 90f, y = 140f, t = 1L),
                ),
            )

        val moved = moveStrokes(listOf(original), deltaX = 45f, deltaY = -12f)
        val restored = moveStrokes(moved, deltaX = -45f, deltaY = 12f)

        assertEquals(original.points[0].x, restored[0].points[0].x, DELTA)
        assertEquals(original.points[0].y, restored[0].points[0].y, DELTA)
        assertEquals(original.points[1].x, restored[0].points[1].x, DELTA)
        assertEquals(original.points[1].y, restored[0].points[1].y, DELTA)
    }

    @Test
    fun `resize inverse round trip restores original geometry`() {
        val original =
            createStroke(
                listOf(
                    StrokePoint(x = 50f, y = 60f, t = 0L),
                    StrokePoint(x = 150f, y = 160f, t = 1L),
                ),
            )

        val scaled = resizeStrokes(listOf(original), scale = 1.5f, pivotX = 100f, pivotY = 100f)
        val restored = resizeStrokes(scaled, scale = 1f / 1.5f, pivotX = 100f, pivotY = 100f)

        assertEquals(original.points[0].x, restored[0].points[0].x, DELTA)
        assertEquals(original.points[0].y, restored[0].points[0].y, DELTA)
        assertEquals(original.points[1].x, restored[0].points[1].x, DELTA)
        assertEquals(original.points[1].y, restored[0].points[1].y, DELTA)
    }

    @Test
    fun `findStrokesInLasso remains responsive on large documents`() {
        val strokes =
            (0 until 5000).map { index ->
                val x = (index % 100) * 20f
                val y = (index / 100) * 20f
                createStroke(
                    listOf(
                        StrokePoint(x = x, y = y, t = index.toLong()),
                        StrokePoint(x = x + 8f, y = y + 8f, t = index.toLong() + 1),
                    ),
                    baseWidth = 0f,
                )
            }
        val polygon =
            listOf(
                Pair(100f, 100f),
                Pair(700f, 100f),
                Pair(700f, 700f),
                Pair(100f, 700f),
            )
        val index = createSpatialIndex(strokes)

        var selected: Set<String> = emptySet()
        val elapsedMs =
            measureTimeMillis {
                selected = findStrokesInLasso(polygon, strokes, index)
            }

        assertTrue(selected.isNotEmpty())
        assertTrue(elapsedMs < 500)
    }

    private fun createStroke(
        points: List<StrokePoint>,
        baseWidth: Float = 2f,
    ): Stroke {
        val bounds = calculateBounds(points, strokeWidthPadding = baseWidth)
        return Stroke(
            id =
                java.util.UUID
                    .randomUUID()
                    .toString(),
            points = points,
            style = StrokeStyle(tool = Tool.PEN, baseWidth = baseWidth),
            bounds = bounds,
            createdAt = System.currentTimeMillis(),
        )
    }

    private fun createSpatialIndex(strokes: List<Stroke>): com.onyx.android.ink.model.SpatialIndex {
        val index =
            com.onyx.android.ink.model
                .SpatialIndex()
        strokes.forEach { index.insert(it) }
        return index
    }

    companion object {
        private const val DELTA = 0.001f
    }
}
