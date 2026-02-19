package com.onyx.android.ink.algorithm

import com.onyx.android.ink.model.Stroke
import com.onyx.android.ink.model.StrokeBounds
import com.onyx.android.ink.model.StrokePoint
import com.onyx.android.ink.model.StrokeStyle
import com.onyx.android.ink.model.Tool
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class StrokeSplitterTest {
    @Test
    fun `splitStrokeAtTouchedIndices returns original stroke when no indices touched`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, emptySet())
        assertEquals(1, result.segments.size)
        assertEquals(stroke.id, result.segments[0].id)
    }

    @Test
    fun `splitStrokeAtTouchedIndices returns empty when all points touched`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 10f, y = 0f, t = 1L),
                    StrokePoint(x = 20f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(0, 1, 2))
        assertEquals(0, result.segments.size)
    }

    @Test
    fun `splitStrokeAtTouchedIndices splits stroke at touched middle section`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 10f, y = 0f, t = 1L),
                    StrokePoint(x = 20f, y = 0f, t = 2L),
                    StrokePoint(x = 30f, y = 0f, t = 3L),
                    StrokePoint(x = 40f, y = 0f, t = 4L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(2, 3))
        assertEquals(2, result.segments.size)
        assertEquals(2, result.segments[0].points.size)
        assertEquals(
            0f,
            result.segments[0]
                .points
                .first()
                .x,
        )
        assertEquals(
            10f,
            result.segments[0]
                .points
                .last()
                .x,
        )
        assertEquals(1, result.segments[1].points.size)
        assertEquals(
            40f,
            result.segments[1]
                .points
                .first()
                .x,
        )
    }

    @Test
    fun `splitStrokeAtTouchedIndices produces two segments when middle point touched`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(1))
        assertEquals(2, result.segments.size)
        assertEquals(1, result.segments[0].points.size)
        assertEquals(
            0f,
            result.segments[0]
                .points
                .first()
                .x,
        )
        assertEquals(1, result.segments[1].points.size)
        assertEquals(
            100f,
            result.segments[1]
                .points
                .first()
                .x,
        )
    }

    @Test
    fun `splitStrokeAtTouchedIndices preserves style across fragments`() {
        val style =
            StrokeStyle(
                tool = Tool.PEN,
                color = "#FF0000",
                baseWidth = 3.0f,
                smoothingLevel = 0.5f,
            )
        val stroke =
            Stroke(
                id = UUID.randomUUID().toString(),
                points =
                    listOf(
                        StrokePoint(x = 0f, y = 0f, t = 0L),
                        StrokePoint(x = 50f, y = 0f, t = 1L),
                        StrokePoint(x = 100f, y = 0f, t = 2L),
                    ),
                style = style,
                bounds = StrokeBounds(x = 0f, y = 0f, w = 100f, h = 0f),
                createdAt = System.currentTimeMillis(),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(1))
        assertEquals(2, result.segments.size)
        result.segments.forEach { segment ->
            assertEquals(style.tool, segment.style.tool)
            assertEquals(style.color, segment.style.color)
            assertEquals(style.baseWidth, segment.style.baseWidth)
            assertEquals(style.smoothingLevel, segment.style.smoothingLevel)
        }
    }

    @Test
    fun `splitStrokeAtTouchedIndices generates new IDs for segments`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(1))
        assertEquals(2, result.segments.size)
        assertTrue(result.segments[0].id != stroke.id)
        assertTrue(result.segments[1].id != stroke.id)
        assertTrue(result.segments[0].id != result.segments[1].id)
    }

    @Test
    fun `findTouchedIndices returns empty set when no intersection`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 100f, y = 0f, t = 1L),
                ),
            )
        val eraserPath = listOf(Pair(200f, 200f), Pair(300f, 200f))
        val result = findTouchedIndices(stroke, eraserPath, eraserRadius = 10f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `findTouchedIndices returns touched indices when intersecting`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val eraserPath = listOf(Pair(45f, 0f), Pair(55f, 0f))
        val result = findTouchedIndices(stroke, eraserPath, eraserRadius = 10f)
        assertTrue(result.contains(1))
    }

    @Test
    fun `isPointTouchedByPath returns true when point is within radius`() {
        val pathPoints = listOf(Pair(50f, 50f))
        val result = isPointTouchedByPath(52f, 50f, pathPoints, radius = 5f)
        assertTrue(result)
    }

    @Test
    fun `isPointTouchedByPath returns false when point is outside radius`() {
        val pathPoints = listOf(Pair(50f, 50f))
        val result = isPointTouchedByPath(100f, 100f, pathPoints, radius = 5f)
        assertTrue(!result)
    }

    @Test
    fun `pointToSegmentDistance returns correct distance for perpendicular point`() {
        val distance = pointToSegmentDistance(50f, 50f, 0f, 50f, 100f, 50f)
        assertEquals(0f, distance, 0.001f)
    }

    @Test
    fun `pointToSegmentDistance returns correct distance for off-line point`() {
        val distance = pointToSegmentDistance(50f, 60f, 0f, 50f, 100f, 50f)
        assertEquals(10f, distance, 0.001f)
    }

    @Test
    fun `splitStrokeAtTouchedIndices produces three segments when two gaps exist`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 10f, y = 0f, t = 1L),
                    StrokePoint(x = 20f, y = 0f, t = 2L),
                    StrokePoint(x = 30f, y = 0f, t = 3L),
                    StrokePoint(x = 40f, y = 0f, t = 4L),
                    StrokePoint(x = 50f, y = 0f, t = 5L),
                    StrokePoint(x = 60f, y = 0f, t = 6L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(2, 3, 4))
        assertEquals(2, result.segments.size)
        assertEquals(2, result.segments[0].points.size)
        assertEquals(2, result.segments[1].points.size)
        assertEquals(
            0f,
            result.segments[0]
                .points
                .first()
                .x,
        )
        assertEquals(
            10f,
            result.segments[0]
                .points
                .last()
                .x,
        )
        assertEquals(
            50f,
            result.segments[1]
                .points
                .first()
                .x,
        )
        assertEquals(
            60f,
            result.segments[1]
                .points
                .last()
                .x,
        )
    }

    @Test
    fun `splitStrokeAtTouchedIndices preserves createdAt timestamp`() {
        val createdAt = 1234567890L
        val stroke =
            Stroke(
                id = UUID.randomUUID().toString(),
                points =
                    listOf(
                        StrokePoint(x = 0f, y = 0f, t = 0L),
                        StrokePoint(x = 50f, y = 0f, t = 1L),
                        StrokePoint(x = 100f, y = 0f, t = 2L),
                    ),
                style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
                bounds = StrokeBounds(x = 0f, y = 0f, w = 100f, h = 0f),
                createdAt = createdAt,
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(1))
        assertEquals(2, result.segments.size)
        result.segments.forEach { segment ->
            assertEquals(createdAt, segment.createdAt)
        }
    }

    @Test
    fun `splitStrokeAtTouchedIndices with first point touched leaves trailing segment`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(0))
        assertEquals(1, result.segments.size)
        assertEquals(2, result.segments[0].points.size)
        assertEquals(
            50f,
            result.segments[0]
                .points
                .first()
                .x,
        )
        assertEquals(
            100f,
            result.segments[0]
                .points
                .last()
                .x,
        )
    }

    @Test
    fun `splitStrokeAtTouchedIndices with last point touched leaves leading segment`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(2))
        assertEquals(1, result.segments.size)
        assertEquals(2, result.segments[0].points.size)
        assertEquals(
            0f,
            result.segments[0]
                .points
                .first()
                .x,
        )
        assertEquals(
            50f,
            result.segments[0]
                .points
                .last()
                .x,
        )
    }

    @Test
    fun `findTouchedIndices with curved eraser path detects points`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 50f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val eraserPath =
            listOf(
                Pair(40f, 40f),
                Pair(50f, 50f),
                Pair(60f, 40f),
            )
        val result = findTouchedIndices(stroke, eraserPath, eraserRadius = 15f)
        assertTrue(result.contains(1))
    }

    @Test
    fun `splitStrokeAtTouchedIndices calculates correct bounds for segments`() {
        val stroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 50f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val result = splitStrokeAtTouchedIndices(stroke, setOf(1))
        assertEquals(2, result.segments.size)

        val firstSegment = result.segments[0]
        assertEquals(0f, firstSegment.bounds.x, 1f)
        assertEquals(0f, firstSegment.bounds.y, 1f)

        val secondSegment = result.segments[1]
        assertEquals(100f, secondSegment.bounds.x, 1f)
        assertEquals(0f, secondSegment.bounds.y, 1f)
    }

    @Test
    fun `computeStrokeSplitCandidates is deterministic for same input path`() {
        val targetStroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 50f, y = 0f, t = 1L),
                    StrokePoint(x = 100f, y = 0f, t = 2L),
                ),
            )
        val strokes =
            listOf(
                createStroke(
                    listOf(
                        StrokePoint(x = 0f, y = 20f, t = 0L),
                        StrokePoint(x = 100f, y = 20f, t = 1L),
                    ),
                ),
                targetStroke,
            )
        val path = listOf(45f to -5f, 55f to 5f)

        val first = computeStrokeSplitCandidates(strokes, path, eraserRadius = 10f)
        val second = computeStrokeSplitCandidates(strokes, path, eraserRadius = 10f)

        assertEquals(first.size, second.size)
        first.zip(second).forEach { (a, b) ->
            assertEquals(a.original.id, b.original.id)
            assertEquals(a.segments.map { it.points }, b.segments.map { it.points })
            assertEquals(a.segments.map { it.style }, b.segments.map { it.style })
            assertEquals(a.segments.map { it.createdAt }, b.segments.map { it.createdAt })
            assertEquals(a.segments.map { it.createdLamport }, b.segments.map { it.createdLamport })
        }
    }

    @Test
    fun `split undo redo cycle restores original stroke list losslessly`() {
        val unaffectedStroke =
            createStroke(
                listOf(
                    StrokePoint(x = -20f, y = 0f, t = 0L),
                    StrokePoint(x = -10f, y = 0f, t = 1L),
                ),
            )
        val targetStroke =
            createStroke(
                listOf(
                    StrokePoint(x = 0f, y = 0f, t = 0L),
                    StrokePoint(x = 40f, y = 0f, t = 1L),
                    StrokePoint(x = 80f, y = 0f, t = 2L),
                    StrokePoint(x = 120f, y = 0f, t = 3L),
                ),
            )
        val originalStrokes = listOf(unaffectedStroke, targetStroke)
        val split = splitStrokeAtTouchedIndices(targetStroke, setOf(1, 2))
        val firstApply = applyStrokeSplit(originalStrokes, targetStroke, split.segments)
        assertTrue(firstApply != null)

        val undoOnce = restoreStrokeSplit(firstApply!!.strokes, targetStroke, split.segments, firstApply.insertionIndex)
        val redo = applyStrokeSplit(undoOnce, targetStroke, split.segments)
        assertTrue(redo != null)
        val finalUndo = restoreStrokeSplit(redo!!.strokes, targetStroke, split.segments, redo.insertionIndex)

        assertEquals(originalStrokes, finalUndo)
    }

    private fun createStroke(points: List<StrokePoint>): Stroke {
        val bounds =
            if (points.isEmpty()) {
                StrokeBounds(x = 0f, y = 0f, w = 0f, h = 0f)
            } else {
                var minX = points.minOf { it.x }
                var maxX = points.maxOf { it.x }
                var minY = points.minOf { it.y }
                var maxY = points.maxOf { it.y }
                StrokeBounds(x = minX, y = minY, w = maxX - minX, h = maxY - minY)
            }
        return Stroke(
            id = UUID.randomUUID().toString(),
            points = points,
            style = StrokeStyle(tool = Tool.PEN, baseWidth = 2f),
            bounds = bounds,
            createdAt = System.currentTimeMillis(),
        )
    }
}
