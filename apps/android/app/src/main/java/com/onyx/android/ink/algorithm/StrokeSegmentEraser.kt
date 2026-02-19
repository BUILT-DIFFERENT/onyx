package com.onyx.android.ink.algorithm

import com.onyx.android.ink.model.Stroke

data class StrokeSplitCandidate(
    val original: Stroke,
    val segments: List<Stroke>,
)

data class StrokeSplitMutation(
    val strokes: List<Stroke>,
    val insertionIndex: Int,
)

fun computeStrokeSplitCandidates(
    strokes: List<Stroke>,
    eraserPathPoints: List<Pair<Float, Float>>,
    eraserRadius: Float,
): List<StrokeSplitCandidate> {
    if (strokes.isEmpty() || eraserPathPoints.isEmpty()) {
        return emptyList()
    }
    return buildList {
        strokes.forEach { stroke ->
            val touchedIndices = findTouchedIndices(stroke, eraserPathPoints, eraserRadius)
            if (touchedIndices.isEmpty()) {
                return@forEach
            }
            val split = splitStrokeAtTouchedIndices(stroke, touchedIndices)
            if (split.segments.size == 1 && split.segments.firstOrNull()?.id == stroke.id) {
                return@forEach
            }
            add(
                StrokeSplitCandidate(
                    original = stroke,
                    segments = split.segments,
                ),
            )
        }
    }
}

fun applyStrokeSplit(
    strokes: List<Stroke>,
    original: Stroke,
    segments: List<Stroke>,
): StrokeSplitMutation? {
    val insertionIndex = strokes.indexOfFirst { it.id == original.id }
    if (insertionIndex < 0) {
        return null
    }
    val updated =
        buildList(strokes.size - 1 + segments.size) {
            addAll(strokes.subList(0, insertionIndex))
            addAll(segments)
            addAll(strokes.subList(insertionIndex + 1, strokes.size))
        }
    return StrokeSplitMutation(strokes = updated, insertionIndex = insertionIndex)
}

fun restoreStrokeSplit(
    strokes: List<Stroke>,
    original: Stroke,
    segments: List<Stroke>,
    insertionIndex: Int,
): List<Stroke> {
    val segmentIds = segments.mapTo(mutableSetOf()) { it.id }
    val withoutSegments = strokes.filterNot { it.id in segmentIds }
    val insertAt = insertionIndex.coerceIn(0, withoutSegments.size)
    return buildList(withoutSegments.size + 1) {
        addAll(withoutSegments.subList(0, insertAt))
        add(original)
        addAll(withoutSegments.subList(insertAt, withoutSegments.size))
    }
}
