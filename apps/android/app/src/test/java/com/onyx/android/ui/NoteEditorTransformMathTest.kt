package com.onyx.android.ui

import com.onyx.android.ink.model.ViewTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NoteEditorTransformMathTest {
    @Test
    fun `compute zoom limits uses fit-relative min and max`() {
        val limits =
            computeZoomLimits(
                pageWidth = 1000f,
                pageHeight = 2000f,
                viewportWidth = 2000f,
                viewportHeight = 2000f,
            )

        assertEquals(1f, limits.fitZoom, DELTA)
        assertEquals(0.75f, limits.minZoom, DELTA)
        assertEquals(6f, limits.maxZoom, DELTA)
    }

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

    @Test
    fun `apply transform gesture clamps zoom to dynamic limits`() {
        val limits =
            computeZoomLimits(
                pageWidth = 1000f,
                pageHeight = 1000f,
                viewportWidth = 1000f,
                viewportHeight = 1000f,
            )
        val zoomedOut =
            applyTransformGesture(
                current = ViewTransform.DEFAULT,
                gesture =
                    TransformGesture(
                        zoomChange = 0.05f,
                        panChangeX = 0f,
                        panChangeY = 0f,
                        centroidX = 500f,
                        centroidY = 500f,
                    ),
                zoomLimits = limits,
                pageWidth = 1000f,
                pageHeight = 1000f,
                viewportWidth = 1000f,
                viewportHeight = 1000f,
            )
        val zoomedIn =
            applyTransformGesture(
                current = ViewTransform.DEFAULT,
                gesture =
                    TransformGesture(
                        zoomChange = 100f,
                        panChangeX = 0f,
                        panChangeY = 0f,
                        centroidX = 500f,
                        centroidY = 500f,
                    ),
                zoomLimits = limits,
                pageWidth = 1000f,
                pageHeight = 1000f,
                viewportWidth = 1000f,
                viewportHeight = 1000f,
            )

        assertEquals(limits.minZoom, zoomedOut.zoom, DELTA)
        assertEquals(limits.maxZoom, zoomedIn.zoom, DELTA)
    }

    @Test
    fun `zoom bucket steps up with zoom level`() {
        assertEquals(1f, zoomToRenderScaleBucket(0.6f), DELTA)
        assertEquals(1f, zoomToRenderScaleBucket(1f), DELTA)
        assertEquals(1.5f, zoomToRenderScaleBucket(1.1f), DELTA)
        assertEquals(2f, zoomToRenderScaleBucket(1.7f), DELTA)
        assertEquals(3f, zoomToRenderScaleBucket(2.8f), DELTA)
        assertEquals(4f, zoomToRenderScaleBucket(4f), DELTA)
    }

    @Test
    fun `render scale is clamped for very large pages`() {
        val renderScale =
            resolvePdfRenderScale(
                viewZoom = 4f,
                pageWidth = 5000f,
                pageHeight = 5000f,
            )

        assertEquals(0.8f, renderScale, DELTA)
    }
}

private const val DELTA = 0.0001f
