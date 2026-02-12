package com.onyx.android.ui

import com.onyx.android.ink.model.ViewTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteEditorTransformMathTest {
    @Test
    fun `fit transform centers page in viewport`() {
        val transform =
            fitTransformToViewport(
                pageWidth = 1000f,
                pageHeight = 2000f,
                viewportWidth = 2000f,
                viewportHeight = 2000f,
            )

        assertEquals(1f, transform.zoom, DELTA)
        assertEquals(500f, transform.panX, DELTA)
        assertEquals(0f, transform.panY, DELTA)
    }

    @Test
    fun `constrain transform clamps pan when zoomed content larger than viewport`() {
        val constrained =
            constrainTransformToViewport(
                transform = ViewTransform(zoom = 2f, panX = 100f, panY = -2500f),
                pageWidth = 1000f,
                pageHeight = 1000f,
                viewportWidth = 1200f,
                viewportHeight = 1200f,
            )

        assertEquals(0f, constrained.panX, DELTA)
        assertEquals(-800f, constrained.panY, DELTA)
    }

    @Test
    fun `constrain transform re-centers when content smaller than viewport`() {
        val constrained =
            constrainTransformToViewport(
                transform = ViewTransform(zoom = 0.5f, panX = -120f, panY = 10f),
                pageWidth = 1000f,
                pageHeight = 1000f,
                viewportWidth = 1200f,
                viewportHeight = 1400f,
            )

        assertEquals(350f, constrained.panX, DELTA)
        assertEquals(450f, constrained.panY, DELTA)
    }
}

private const val DELTA = 0.0001f
