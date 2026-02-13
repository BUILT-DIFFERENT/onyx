package com.onyx.android.pdf

import android.graphics.PointF
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TextSelectionModelTest {
    @Test
    fun `findPdfTextCharIndexAtPagePoint returns first matching glyph`() {
        val chars =
            listOf(
                createChar('A', left = 0f, top = 0f, right = 10f, bottom = 10f, pageIndex = 0),
                createChar('B', left = 10f, top = 0f, right = 20f, bottom = 10f, pageIndex = 0),
            )

        val index =
            findPdfTextCharIndexAtPagePoint(
                characters = chars,
                pageX = 15f,
                pageY = 5f,
            )

        assertEquals(1, index)
    }

    @Test
    fun `findPdfTextCharIndexAtPagePoint returns null when no hit`() {
        val chars =
            listOf(
                createChar('A', left = 0f, top = 0f, right = 10f, bottom = 10f, pageIndex = 0),
            )

        val index =
            findPdfTextCharIndexAtPagePoint(
                characters = chars,
                pageX = 30f,
                pageY = 30f,
            )

        assertNull(index)
    }

    @Test
    fun `buildPdfTextSelection orders reversed indices and concatenates text`() {
        val chars =
            listOf(
                createChar('A', left = 0f, top = 0f, right = 10f, bottom = 10f, pageIndex = 0),
                createChar('B', left = 10f, top = 0f, right = 20f, bottom = 10f, pageIndex = 0),
                createChar('C', left = 20f, top = 0f, right = 30f, bottom = 10f, pageIndex = 0),
            )

        val selection =
            buildPdfTextSelection(
                characters = chars,
                startIndex = 2,
                endIndex = 0,
            )

        assertEquals("ABC", selection.text)
        assertEquals(3, selection.chars.size)
        assertEquals('A', selection.chars.first().char)
        assertEquals('C', selection.chars.last().char)
    }

    @Test
    fun `buildPdfTextSelection clamps out-of-range indexes`() {
        val chars =
            listOf(
                createChar('A', left = 0f, top = 0f, right = 10f, bottom = 10f, pageIndex = 0),
                createChar('B', left = 10f, top = 0f, right = 20f, bottom = 10f, pageIndex = 0),
            )

        val selection =
            buildPdfTextSelection(
                characters = chars,
                startIndex = -20,
                endIndex = 99,
            )

        assertEquals("AB", selection.text)
        assertEquals(2, selection.chars.size)
    }
}

@Suppress("LongParameterList")
private fun createChar(
    value: Char,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    pageIndex: Int,
): PdfTextChar =
    PdfTextChar(
        char = value,
        pageIndex = pageIndex,
        quad =
            PdfTextQuad(
                p1 = pointF(left, top),
                p2 = pointF(right, top),
                p3 = pointF(right, bottom),
                p4 = pointF(left, bottom),
            ),
    )

private fun pointF(
    x: Float,
    y: Float,
): PointF =
    PointF().apply {
        this.x = x
        this.y = y
    }
