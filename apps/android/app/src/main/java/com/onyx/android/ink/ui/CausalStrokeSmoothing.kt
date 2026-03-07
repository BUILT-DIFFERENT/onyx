package com.onyx.android.ink.ui

import com.onyx.android.ink.model.StrokePoint

internal class CausalStrokeSmoothing {
    private val pointBuffer = ArrayDeque<StrokePoint>(POINT_BUFFER_SIZE)

    fun push(point: StrokePoint): List<StrokePoint> {
        pointBuffer.addLast(point)
        return when (pointBuffer.size) {
            1 -> listOf(point)
            2 -> listOf(blendTowardsRaw(previous = pointBuffer[0], current = pointBuffer[1]))
            3 ->
                listOf(
                    blendTowardsRaw(
                        previous = pointBuffer[1],
                        current = pointBuffer[2],
                    ),
                )
            else -> {
                val samples =
                    sampleSegment(
                        pointBuffer[FIRST_POINT_INDEX],
                        pointBuffer[SECOND_POINT_INDEX],
                        pointBuffer[THIRD_POINT_INDEX],
                        pointBuffer[FOURTH_POINT_INDEX],
                    )
                pointBuffer.removeFirst()
                samples
            }
        }
    }

    fun drainTail(): List<StrokePoint> {
        if (pointBuffer.isEmpty()) {
            return emptyList()
        }
        val drained = mutableListOf<StrokePoint>()
        while (pointBuffer.size >= POINT_BUFFER_SIZE) {
            drained +=
                sampleSegment(
                    pointBuffer[FIRST_POINT_INDEX],
                    pointBuffer[SECOND_POINT_INDEX],
                    pointBuffer[THIRD_POINT_INDEX],
                    pointBuffer[FOURTH_POINT_INDEX],
                )
            pointBuffer.removeFirst()
        }
        when (pointBuffer.size) {
            THREE_POINTS -> {
                drained += blendTowardsRaw(previous = pointBuffer[1], current = pointBuffer[2])
                pointBuffer.clear()
            }

            TWO_POINTS -> {
                drained += pointBuffer.last()
                pointBuffer.clear()
            }

            1 -> {
                drained += pointBuffer.last()
                pointBuffer.clear()
            }

            else -> pointBuffer.clear()
        }
        return dedupePoints(drained)
    }

    private fun blendTowardsRaw(
        previous: StrokePoint,
        current: StrokePoint,
    ): StrokePoint =
        StrokePoint(
            x = previous.x + (current.x - previous.x) * TIP_BLEND_RATIO,
            y = previous.y + (current.y - previous.y) * TIP_BLEND_RATIO,
            t = current.t,
            p = current.p ?: previous.p,
            tx = current.tx ?: previous.tx,
            ty = current.ty ?: previous.ty,
            r = current.r ?: previous.r,
        )

    fun previewPoints(rawPoints: List<StrokePoint>): List<StrokePoint> {
        reset()
        val preview = mutableListOf<StrokePoint>()
        rawPoints.forEach { point ->
            preview += push(point)
        }
        preview += drainTail()
        return dedupePoints(preview)
    }

    fun previewPointCount(rawPointCount: Int): Int =
        previewPoints(
            List(rawPointCount) { index ->
                StrokePoint(x = index.toFloat(), y = index.toFloat(), t = index.toLong())
            },
        ).size

    fun reset() {
        pointBuffer.clear()
    }

    private fun sampleSegment(
        p0: StrokePoint,
        p1: StrokePoint,
        p2: StrokePoint,
        p3: StrokePoint,
    ): List<StrokePoint> {
        val samples = ArrayList<StrokePoint>(CATMULL_SUBDIVISIONS + 1)
        for (step in FIRST_SUBDIVISION_STEP..CATMULL_SUBDIVISIONS) {
            val t = step.toFloat() / CATMULL_SUBDIVISIONS.toFloat()
            samples += catmullRom(p0, p1, p2, p3, t)
        }
        return dedupePoints(samples)
    }

    private fun catmullRom(
        p0: StrokePoint,
        p1: StrokePoint,
        p2: StrokePoint,
        p3: StrokePoint,
        t: Float,
    ): StrokePoint {
        val t2 = t * t
        val t3 = t2 * t
        val x =
            CATMULL_SCALE *
                (
                    DOUBLE * p1.x +
                        (-p0.x + p2.x) * t +
                        (DOUBLE * p0.x - FIVE * p1.x + POINT_BUFFER_SIZE.toFloat() * p2.x - p3.x) * t2 +
                        (-p0.x + THREE * p1.x - THREE * p2.x + p3.x) * t3
                )
        val y =
            CATMULL_SCALE *
                (
                    DOUBLE * p1.y +
                        (-p0.y + p2.y) * t +
                        (DOUBLE * p0.y - FIVE * p1.y + POINT_BUFFER_SIZE.toFloat() * p2.y - p3.y) * t2 +
                        (-p0.y + THREE * p1.y - THREE * p2.y + p3.y) * t3
                )
        val pressure = lerpNullable(p1.p, p2.p, t)
        val tx = lerpNullable(p1.tx, p2.tx, t)
        val ty = lerpNullable(p1.ty, p2.ty, t)
        val rotation = lerpNullable(p1.r, p2.r, t)
        val timestamp = lerpLong(p1.t, p2.t, t)
        return StrokePoint(
            x = x,
            y = y,
            t = timestamp,
            p = pressure,
            tx = tx,
            ty = ty,
            r = rotation,
        )
    }

    private fun dedupePoints(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size <= 1) {
            return points
        }
        val deduped = ArrayList<StrokePoint>(points.size)
        for (point in points) {
            val previous = deduped.lastOrNull()
            if (previous == null || previous.x != point.x || previous.y != point.y) {
                deduped += point
            } else if (point.p != null && point.p != previous.p) {
                deduped[deduped.lastIndex] = previous.copy(p = point.p, t = point.t)
            }
        }
        return deduped
    }

    private fun lerpNullable(
        start: Float?,
        end: Float?,
        t: Float,
    ): Float? {
        val resolvedStart = start ?: end ?: return null
        val resolvedEnd = end ?: resolvedStart
        return resolvedStart + (resolvedEnd - resolvedStart) * t
    }

    private fun lerpLong(
        start: Long,
        end: Long,
        t: Float,
    ): Long = (start + ((end - start) * t)).toLong()

    private companion object {
        const val POINT_BUFFER_SIZE = 4
        const val FIRST_POINT_INDEX = 0
        const val SECOND_POINT_INDEX = 1
        const val THIRD_POINT_INDEX = 2
        const val FOURTH_POINT_INDEX = 3
        const val LAST_POINT_INDEX = 2
        const val TWO_POINTS = 2
        const val THREE_POINTS = 3
        const val FIRST_SUBDIVISION_STEP = 1
        const val CATMULL_SUBDIVISIONS = 4
        const val CATMULL_SCALE = 0.5f
        const val TIP_BLEND_RATIO = 0.85f
        const val DOUBLE = 2f
        const val THREE = 3f
        const val FIVE = 5f
    }
}
