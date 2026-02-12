package com.onyx.android.ink.ui

import com.onyx.android.ink.model.ViewTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ViewTransformTest {
    @Test
    fun `default transform is identity`() {
        val transform = ViewTransform.DEFAULT
        assertEquals(1f, transform.zoom, DELTA)
        assertEquals(0f, transform.panX, DELTA)
        assertEquals(0f, transform.panY, DELTA)
    }

    @Test
    fun `pageToScreen at default transform is identity`() {
        val transform = ViewTransform.DEFAULT
        assertEquals(100f, transform.pageToScreenX(100f), DELTA)
        assertEquals(200f, transform.pageToScreenY(200f), DELTA)
    }

    @Test
    fun `screenToPage at default transform is identity`() {
        val transform = ViewTransform.DEFAULT
        assertEquals(100f, transform.screenToPageX(100f), DELTA)
        assertEquals(200f, transform.screenToPageY(200f), DELTA)
    }

    @Test
    fun `pageToScreen applies zoom and pan`() {
        val transform = ViewTransform(zoom = 2f, panX = 50f, panY = 100f)
        // pageToScreenX = pageX * zoom + panX = 10 * 2 + 50 = 70
        assertEquals(70f, transform.pageToScreenX(10f), DELTA)
        // pageToScreenY = pageY * zoom + panY = 20 * 2 + 100 = 140
        assertEquals(140f, transform.pageToScreenY(20f), DELTA)
    }

    @Test
    fun `screenToPage reverses pageToScreen`() {
        val transform = ViewTransform(zoom = 2.5f, panX = 30f, panY = -20f)
        val pageX = 42f
        val pageY = 87f
        val screenX = transform.pageToScreenX(pageX)
        val screenY = transform.pageToScreenY(pageY)

        assertEquals(pageX, transform.screenToPageX(screenX), DELTA)
        assertEquals(pageY, transform.screenToPageY(screenY), DELTA)
    }

    @Test
    fun `round trip conversion preserves coordinates`() {
        val transform = ViewTransform(zoom = 1.75f, panX = -100f, panY = 200f)

        for (x in listOf(0f, 50f, 100f, -25f, 999f)) {
            for (y in listOf(0f, 50f, 100f, -25f, 999f)) {
                val (screenX, screenY) = transform.pageToScreen(x, y)
                val (backX, backY) = transform.screenToPage(screenX, screenY)
                assertEquals(x, backX, DELTA, "X round-trip failed for ($x, $y)")
                assertEquals(y, backY, DELTA, "Y round-trip failed for ($x, $y)")
            }
        }
    }

    @Test
    fun `pageWidthToScreen scales by zoom`() {
        val transform = ViewTransform(zoom = 3f, panX = 0f, panY = 0f)
        assertEquals(12f, transform.pageWidthToScreen(4f), DELTA)
    }

    @Test
    fun `screenToPage at zero coordinates with pan`() {
        val transform = ViewTransform(zoom = 1f, panX = 100f, panY = 200f)
        // screenToPageX(0) = (0 - 100) / 1 = -100
        assertEquals(-100f, transform.screenToPageX(0f), DELTA)
        assertEquals(-200f, transform.screenToPageY(0f), DELTA)
    }

    @Test
    fun `pair conversion matches individual axis conversion`() {
        val transform = ViewTransform(zoom = 2f, panX = 10f, panY = 20f)
        val (sx, sy) = transform.pageToScreen(5f, 15f)
        assertEquals(transform.pageToScreenX(5f), sx, DELTA)
        assertEquals(transform.pageToScreenY(15f), sy, DELTA)

        val (px, py) = transform.screenToPage(100f, 200f)
        assertEquals(transform.screenToPageX(100f), px, DELTA)
        assertEquals(transform.screenToPageY(200f), py, DELTA)
    }

    @Test
    fun `zoom at MIN_ZOOM boundary`() {
        val transform = ViewTransform(zoom = ViewTransform.MIN_ZOOM)
        val screenX = transform.pageToScreenX(100f)
        assertEquals(100f * ViewTransform.MIN_ZOOM, screenX, DELTA)
    }

    @Test
    fun `zoom at MAX_ZOOM boundary`() {
        val transform = ViewTransform(zoom = ViewTransform.MAX_ZOOM)
        val screenX = transform.pageToScreenX(100f)
        assertEquals(100f * ViewTransform.MAX_ZOOM, screenX, DELTA)
    }
}

private const val DELTA = 0.0001f
