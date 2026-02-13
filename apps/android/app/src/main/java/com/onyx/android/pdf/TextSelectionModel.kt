package com.onyx.android.pdf

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private const val EPSILON = 0.0001f
private const val MIN_INDEX = 0
private const val INDEX_OFFSET = 1

data class PdfTextQuad(
    val p1: PointF,
    val p2: PointF,
    val p3: PointF,
    val p4: PointF,
)

data class PdfTextChar(
    val char: Char,
    val quad: PdfTextQuad,
    val pageIndex: Int,
)

data class PdfTextSelection(
    val chars: List<PdfTextChar>,
    val text: String,
)

interface PdfTextExtractor {
    suspend fun getCharacters(pageIndex: Int): List<PdfTextChar>
}

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
        text = selectedChars.joinToString(separator = "") { it.char.toString() },
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
