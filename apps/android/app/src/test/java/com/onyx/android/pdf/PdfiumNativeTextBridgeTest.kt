package com.onyx.android.pdf

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PdfiumNativeTextBridgeTest {
    @Test
    fun `toPdfTextChars converts PDF bottom-origin coordinates to top-origin quads`() {
        val nativePage =
            PdfiumNativeTextPage(
                codePoints = intArrayOf('A'.code),
                boxes = floatArrayOf(10f, 30f, 180f, 200f),
            )

        val chars = nativePage.toPdfTextChars(pageIndex = 0, pageHeightPoints = 300f)

        assertEquals(1, chars.size)
        val quad = chars.single().quad
        assertEquals(10f, quad.p1.x)
        assertEquals(100f, quad.p1.y)
        assertEquals(30f, quad.p2.x)
        assertEquals(100f, quad.p2.y)
        assertEquals(30f, quad.p3.x)
        assertEquals(120f, quad.p3.y)
        assertEquals(10f, quad.p4.x)
        assertEquals(120f, quad.p4.y)
    }

    @Test
    fun `toPdfTextChars preserves supplementary code points`() {
        val nativePage =
            PdfiumNativeTextPage(
                codePoints = intArrayOf(0x1F642),
                boxes = floatArrayOf(0f, 10f, 0f, 10f),
            )

        val chars = nativePage.toPdfTextChars(pageIndex = 3, pageHeightPoints = 40f)

        assertEquals(1, chars.size)
        assertEquals("🙂", chars.single().char)
        assertEquals(3, chars.single().pageIndex)
    }

    @Test
    fun `toPdfTextChars skips invalid code points`() {
        val nativePage =
            PdfiumNativeTextPage(
                codePoints = intArrayOf(0, 'B'.code),
                boxes =
                    floatArrayOf(
                        0f,
                        5f,
                        0f,
                        5f,
                        5f,
                        10f,
                        0f,
                        5f,
                    ),
            )

        val chars = nativePage.toPdfTextChars(pageIndex = 0, pageHeightPoints = 20f)

        assertEquals(1, chars.size)
        assertEquals("B", chars.single().char)
    }

    @Test
    fun `charBoxToQuad normalizes reversed bounds`() {
        val quad =
            charBoxToQuad(
                left = 30f,
                right = 10f,
                bottom = 200f,
                top = 180f,
                pageHeightPoints = 300f,
            )

        assertEquals(10f, quad.p1.x)
        assertEquals(100f, quad.p1.y)
        assertEquals(30f, quad.p3.x)
        assertEquals(120f, quad.p3.y)
    }
}
