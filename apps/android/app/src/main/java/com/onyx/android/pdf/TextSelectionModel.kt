package com.onyx.android.pdf

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val EPSILON = 0.0001f
private const val MIN_INDEX = 0
private const val INDEX_OFFSET = 1
private const val HANDLE_HIT_PADDING_POINTS = 3f

data class PdfTextQuad(
    val p1: PointF,
    val p2: PointF,
    val p3: PointF,
    val p4: PointF,
)

data class PdfTextChar(
    val char: String,
    val quad: PdfTextQuad,
    val pageIndex: Int,
)

data class PdfTextSelection(
    val chars: List<PdfTextChar>,
    val text: String,
)

typealias PdfTextExtractor = PdfTextEngine

internal fun findPdfTextCharIndexAtPagePoint(
    characters: List<PdfTextChar>,
    pageX: Float,
    pageY: Float,
): Int? {
    val hitIndex =
        characters.indexOfFirst { char ->
            char.quad.contains(pageX, pageY)
        }
    return if (hitIndex >= MIN_INDEX) hitIndex else null
}

@Suppress("ReturnCount")
internal fun findNearestPdfTextCharIndex(
    characters: List<PdfTextChar>,
    pageX: Float,
    pageY: Float,
): Int? {
    if (characters.isEmpty()) {
        return null
    }

    val directHit =
        characters.indexOfFirst { char ->
            char.quad.contains(pageX, pageY)
        }
    if (directHit >= MIN_INDEX) {
        return directHit
    }

    val expandedHit =
        characters.indexOfFirst { char ->
            char.quad.containsWithPadding(pageX, pageY, HANDLE_HIT_PADDING_POINTS)
        }
    if (expandedHit >= MIN_INDEX) {
        return expandedHit
    }

    var nearestIndex = MIN_INDEX
    var nearestDistance = Float.MAX_VALUE
    characters.forEachIndexed { index, char ->
        val center = char.quad.center()
        val dx = center.x - pageX
        val dy = center.y - pageY
        val distance = (dx * dx) + (dy * dy)
        if (distance < nearestDistance) {
            nearestDistance = distance
            nearestIndex = index
        }
    }
    return nearestIndex
}

internal fun buildPdfTextSelection(
    characters: List<PdfTextChar>,
    startIndex: Int,
    endIndex: Int,
): PdfTextSelection {
    if (characters.isEmpty()) {
        return PdfTextSelection(chars = emptyList(), text = "")
    }
    val clampedStart = startIndex.coerceIn(MIN_INDEX, characters.lastIndex)
    val clampedEnd = endIndex.coerceIn(MIN_INDEX, characters.lastIndex)
    val firstIndex = min(clampedStart, clampedEnd)
    val lastIndex = max(clampedStart, clampedEnd)
    val selectedChars = characters.subList(firstIndex, lastIndex + INDEX_OFFSET)
    return PdfTextSelection(
        chars = selectedChars,
        text = selectedChars.joinToString(separator = "") { it.char },
    )
}

internal fun PdfTextQuad.contains(
    x: Float,
    y: Float,
): Boolean {
    val points = floatArrayOf(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y)
    var sign = 0f
    var index = 0
    while (index < points.size) {
        val currentX = points[index]
        val currentY = points[index + INDEX_OFFSET]
        val nextIndex = (index + 2) % points.size
        val nextX = points[nextIndex]
        val nextY = points[nextIndex + INDEX_OFFSET]
        val cross = ((x - currentX) * (nextY - currentY)) - ((y - currentY) * (nextX - currentX))
        if (abs(cross) > EPSILON) {
            if (sign == 0f) {
                sign = cross
            } else if ((sign > 0f) != (cross > 0f)) {
                return false
            }
        }
        index += 2
    }
    return true
}

private fun PdfTextQuad.containsWithPadding(
    x: Float,
    y: Float,
    padding: Float,
): Boolean {
    val minX = minOf(p1.x, p2.x, p3.x, p4.x) - padding
    val maxX = maxOf(p1.x, p2.x, p3.x, p4.x) + padding
    val minY = minOf(p1.y, p2.y, p3.y, p4.y) - padding
    val maxY = maxOf(p1.y, p2.y, p3.y, p4.y) + padding
    return x in minX..maxX && y in minY..maxY
}

internal fun PdfTextQuad.center(): PointF =
    pointF(
        x = (p1.x + p2.x + p3.x + p4.x) * 0.25f,
        y = (p1.y + p2.y + p3.y + p4.y) * 0.25f,
    )
