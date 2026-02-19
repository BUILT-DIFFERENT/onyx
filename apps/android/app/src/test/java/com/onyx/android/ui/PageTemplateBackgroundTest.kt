package com.onyx.android.ui

import com.onyx.android.ink.model.ViewTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PageTemplateBackgroundTest {
    @Test
    fun `grid pattern respects spacing density`() {
        val pattern =
            computeTemplatePattern(
                backgroundKind = "grid",
                pageWidth = 60f,
                pageHeight = 40f,
                spacing = 20f,
            )

        assertEquals(7, pattern.lines.size)
        assertTrue(pattern.lines.any { line -> line.start.x == 20f && line.end.x == 20f })
        assertTrue(pattern.lines.any { line -> line.start.y == 20f && line.end.y == 20f })
    }

    @Test
    fun `lined pattern emits horizontal lines only`() {
        val pattern =
            computeTemplatePattern(
                backgroundKind = "lined",
                pageWidth = 100f,
                pageHeight = 95f,
                spacing = 30f,
            )

        assertEquals(3, pattern.lines.size)
        assertTrue(pattern.lines.all { line -> line.start.y == line.end.y })
    }

    @Test
    fun `dotted pattern density creates expected dot count`() {
        val pattern =
            computeTemplatePattern(
                backgroundKind = "dotted",
                pageWidth = 30f,
                pageHeight = 20f,
                spacing = 10f,
            )

        assertEquals(6, pattern.dots.size)
    }

    @Test
    fun `template coordinates stay stable across zoom and pan`() {
        val pageX = 24f
        val pageY = 48f
        val transforms =
            listOf(
                ViewTransform(zoom = 0.5f, panX = 8f, panY = 12f),
                ViewTransform(zoom = 1f, panX = -10f, panY = 4f),
                ViewTransform(zoom = 2f, panX = 100f, panY = -40f),
                ViewTransform(zoom = 4f, panX = 0f, panY = 0f),
            )

        transforms.forEach { transform ->
            assertEquals(
                pageX * transform.zoom + transform.panX,
                transform.pageToScreenX(pageX),
                0.0001f,
            )
            assertEquals(
                pageY * transform.zoom + transform.panY,
                transform.pageToScreenY(pageY),
                0.0001f,
            )
        }
    }

    @Test
    fun `density slider ranges are kind specific`() {
        assertEquals(20f, spacingRangeForTemplate("grid").start)
        assertEquals(60f, spacingRangeForTemplate("grid").endInclusive)
        assertEquals(30f, spacingRangeForTemplate("lined").start)
        assertEquals(50f, spacingRangeForTemplate("lined").endInclusive)
        assertEquals(10f, spacingRangeForTemplate("dotted").start)
        assertEquals(20f, spacingRangeForTemplate("dotted").endInclusive)
    }
}
