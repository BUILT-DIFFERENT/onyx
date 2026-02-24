package com.onyx.android.pdf

import android.graphics.PointF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfSearchTest {
    @Test
    fun `findPdfSearchMatches returns page ordered matches`() {
        val page0 = "hello world".mapIndexed { index, c -> char(index, c.toString(), 0) }
        val page1 = "world hello".mapIndexed { index, c -> char(index, c.toString(), 1) }
        val results =
            findPdfSearchMatches(
                query = "hello",
                charactersByPageIndex = mapOf(1 to page1, 0 to page0),
            )

        assertEquals(2, results.size)
        assertEquals(0, results[0].pageIndex)
        assertEquals(1, results[1].pageIndex)
        assertEquals(0, results[0].startCharIndex)
    }

    @Test
    fun `findPdfSearchMatches is case insensitive and supports overlap`() {
        val chars = "BaNaNa".mapIndexed { index, c -> char(index, c.toString(), 0) }
        val results = findPdfSearchMatches(query = "ana", charactersByPageIndex = mapOf(0 to chars))

        assertEquals(2, results.size)
        assertEquals(1, results[0].startCharIndex)
        assertEquals(3, results[1].startCharIndex)
    }

    @Test
    fun `findPdfSearchMatches computes non zero bounds`() {
        val chars = "abc".mapIndexed { index, c -> char(index, c.toString(), 0) }
        val results = findPdfSearchMatches(query = "abc", charactersByPageIndex = mapOf(0 to chars))

        assertEquals(1, results.size)
        assertTrue(results.first().bounds.width > 0f)
        assertTrue(results.first().bounds.height > 0f)
    }

    private fun char(
        index: Int,
        value: String,
        page: Int,
    ): PdfTextChar {
        val left = index.toFloat() * 10f
        val top = 20f
        val right = left + 8f
        val bottom = top + 12f
        return PdfTextChar(
            char = value,
            pageIndex = page,
            quad =
                PdfTextQuad(
                    p1 = PointF(left, top),
                    p2 = PointF(right, top),
                    p3 = PointF(right, bottom),
                    p4 = PointF(left, bottom),
                ),
        )
    }
}
