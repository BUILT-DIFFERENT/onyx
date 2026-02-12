package com.onyx.android.recognition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MyScriptCoordinateTest {
    @Test
    fun `ptToMm conversion factor is correct`() {
        // 1 pt = 1/72 inch, 1 inch = 25.4 mm
        // So 1 pt = 25.4 / 72 mm ≈ 0.352778 mm
        val expected = 25.4f / 72f
        assertEquals(expected, MyScriptPageManager.MM_PER_POINT, DELTA)
    }

    @Test
    fun `72 points equals 25_4 mm (one inch)`() {
        val points = 72f
        val mm = points * MyScriptPageManager.MM_PER_POINT
        assertEquals(25.4f, mm, DELTA)
    }

    @Test
    fun `zero points converts to zero mm`() {
        val mm = 0f * MyScriptPageManager.MM_PER_POINT
        assertEquals(0f, mm, DELTA)
    }

    @Test
    fun `conversion factor is positive`() {
        assertTrue(MyScriptPageManager.MM_PER_POINT > 0f)
    }

    @Test
    fun `one point converts to approximately 0_353 mm`() {
        val mm = 1f * MyScriptPageManager.MM_PER_POINT
        assertEquals(0.3528f, mm, 0.001f)
    }

    @Test
    fun `typical page width 595pt (A4) converts correctly`() {
        // A4 page in pt: 595 × 842
        val pageWidthPt = 595f
        val pageWidthMm = pageWidthPt * MyScriptPageManager.MM_PER_POINT
        // A4 in mm: 210 × 297
        assertEquals(210f, pageWidthMm, 0.5f) // Within 0.5mm tolerance
    }

    @Test
    fun `typical page height 842pt (A4) converts correctly`() {
        val pageHeightPt = 842f
        val pageHeightMm = pageHeightPt * MyScriptPageManager.MM_PER_POINT
        // A4 height in mm: 297
        assertEquals(297f, pageHeightMm, 0.5f)
    }
}

private const val DELTA = 0.0001f
