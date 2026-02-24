@file:Suppress("ReturnCount", "ComplexCondition")

package com.onyx.android.pdf

import androidx.compose.ui.geometry.Rect

internal data class PdfSearchMatch(
    val pageIndex: Int,
    val startCharIndex: Int,
    val endCharIndex: Int,
    val bounds: Rect,
)

internal fun findPdfSearchMatches(
    query: String,
    charactersByPageIndex: Map<Int, List<PdfTextChar>>,
): List<PdfSearchMatch> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) {
        return emptyList()
    }
    return charactersByPageIndex
        .toSortedMap()
        .flatMap { (pageIndex, characters) ->
            findMatchesForPage(pageIndex = pageIndex, characters = characters, query = normalizedQuery)
        }
}

private fun findMatchesForPage(
    pageIndex: Int,
    characters: List<PdfTextChar>,
    query: String,
): List<PdfSearchMatch> {
    if (characters.isEmpty()) {
        return emptyList()
    }
    val haystack = buildString(capacity = characters.size) { characters.forEach { append(it.char.lowercase()) } }
    if (haystack.isBlank()) {
        return emptyList()
    }
    val matches = mutableListOf<PdfSearchMatch>()
    var searchFrom = 0
    while (searchFrom < haystack.length) {
        val hitIndex = haystack.indexOf(query, startIndex = searchFrom)
        if (hitIndex < 0) {
            break
        }
        val endExclusive = (hitIndex + query.length).coerceAtMost(characters.size)
        if (endExclusive > hitIndex) {
            val matchChars = characters.subList(hitIndex, endExclusive)
            matches +=
                PdfSearchMatch(
                    pageIndex = pageIndex,
                    startCharIndex = hitIndex,
                    endCharIndex = endExclusive - 1,
                    bounds = unionBounds(matchChars),
                )
        }
        searchFrom = hitIndex + 1
    }
    return matches
}

private fun unionBounds(chars: List<PdfTextChar>): Rect {
    if (chars.isEmpty()) {
        return Rect.Zero
    }
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    chars.forEach { textChar ->
        val quad = textChar.quad
        val xValues = floatArrayOf(quad.p1.x, quad.p2.x, quad.p3.x, quad.p4.x)
        val yValues = floatArrayOf(quad.p1.y, quad.p2.y, quad.p3.y, quad.p4.y)
        left = minOf(left, xValues.minOrNull() ?: left)
        top = minOf(top, yValues.minOrNull() ?: top)
        right = maxOf(right, xValues.maxOrNull() ?: right)
        bottom = maxOf(bottom, yValues.maxOrNull() ?: bottom)
    }
    if (!left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()) {
        return Rect.Zero
    }
    return Rect(left = left, top = top, right = right, bottom = bottom)
}
